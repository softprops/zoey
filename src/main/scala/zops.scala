package zoey

import scala.concurrent.{ ExecutionContext, Future }

trait ZOp[T <: ZNode.Exists] {

  def apply()(implicit ec: ExecutionContext): Future[T]

  def watch()(implicit ec: ExecutionContext): Future[ZNode.Watch[T]]

  /*def monitor(): Offer[Try[T]] = {
    val broker = new Broker[Try[T]]
    // Set the watch, send the result to the broker, and repeat this when an event occurs
    def setWatch() {
      watch() onSuccess { case ZNode.Watch(result, update) =>
        broker ! result onSuccess { _ =>
          update onSuccess { _ => setWatch() }
        }
      }
    }
    setWatch()
    broker.recv
  }*/
}
