package zoey

import org.apache.zookeeper.KeeperException
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import java.util.concurrent.atomic.AtomicBoolean
import retry._

object KeeperConnectionException {
  def unapply(e: KeeperException): Option[KeeperException] =
    e match {
      case e: KeeperException.ConnectionLossException => Some(e)
      case e: KeeperException.SessionExpiredException => Some(e)
      case e: KeeperException.SessionMovedException => Some(e)
      case e: KeeperException.OperationTimeoutException => Some(e)
      case e => None
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
      val fail = new AtomicBoolean(false)
      op.onFailure {
        case KeeperConnectionException(_) =>
          fail.getAndSet(true)
        case e@NativeConnector.ConnectTimeoutException(_, _) =>
          fail.getAndSet(true)
      }
      implicit val success = new Success[T](
        _ => fail.getAndSet(false))
      
      retry.Backoff(max, delay, base)(() => op)
    }
  }
}
