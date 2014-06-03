package zoey

import org.apache.zookeeper.KeeperException
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import scala.util.control.NonFatal
import retry._

object KeeperConnectionException {
  def unapply(e: KeeperException): Option[KeeperException] =
    e match {
      case e: KeeperException.ConnectionLossException => Some(e)
      case e: KeeperException.SessionExpiredException => Some(e)
      case e: KeeperException.SessionMovedException => Some(e)
      case e: KeeperException.OperationTimeoutException => Some(e)
      case e =>
        None
    }
}

trait RetryPolicy {
  def apply[T](op: => Future[T]): Future[T]
}

object RetryPolicy {
  object None extends RetryPolicy {
    def apply[T](op: => Future[T]): Future[T] = op
  }

  case class Exponential(
    max: Int = 8,
    delay: Duration = 500.millis,
    base: Int = 2)(
    implicit ec: ExecutionContext,
    timer: Timer) extends RetryPolicy {
    def apply[T](op: => Future[T]): Future[T] = {
      implicit val success = new Success[T](Function.const(true))
      LenientBackoff(max, delay, base)(() => op)
    }
  }

  protected object LenientBackoff extends LenientCountingRetry {
    def apply[T](
      max: Int = 8,
      delay: Duration = 500.millis,
      base: Int = 2)
      (promise: () => Future[T])
      (implicit success: Success[T],
       timer: Timer,
       executor: ExecutionContext): Future[T] = {
        retry(max,
              promise,
              success,
              count => SleepFuture(delay) {
                LenientBackoff(count,
                        Duration(delay.length * base, delay.unit),
                        base)(promise)
              }.flatMap(identity))
      }
  }


  protected trait LenientCountingRetry {
    /** Applies the given function and will retry up to `max` times,
     until a successful result is produced. */
    protected def retry[T](
      max: Int,
      promise: () => Future[T],
      success: Success[T],
      orElse: Int => Future[T])(implicit executor: ExecutionContext): Future[T] = {
      val fut = promise()
      fut.flatMap { res =>
        if (max < 1 || success.predicate(res)) fut
        else orElse(max - 1)
      } recoverWith {
        case NonFatal(e) =>
          if (max < 1) fut else orElse(max - 1)
      }
    }
  }
}
