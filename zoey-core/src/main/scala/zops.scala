package zoey

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

trait ZOp[T <: ZNode.Exists] {

  def apply()(implicit ec: ExecutionContext): Future[T]

  def watch()(implicit ec: ExecutionContext): Future[ZNode.Watch[T]]

  def monitor[A](f: Try[T] => A)(implicit ec: ExecutionContext): Unit = {
    def loop(): Unit = {
      watch().foreach { case ZNode.Watch(result, update) =>
        result.foreach { _ =>
          update.foreach { _ => loop() }
        }
        f(result)
      }
    }
    loop()
  }
}
