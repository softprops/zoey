package zoey

import scala.concurrent.{ ExecutionContext, Future }
import java.util.concurrent.atomic.AtomicReference
import org.apache.zookeeper.ZooKeeper
import scala.annotation.tailrec

trait Connector {
  protected[this] val listeners =
    new AtomicReference[List[Connector.EventHandler]](Nil)

  // todo event broker for session events

  def apply(): Future[ZooKeeper]

  def release(): Future[Unit]

  @tailrec
  final def onSessionEvent(f: Connector.EventHandler) {
    val list = listeners.get()
    if (!listeners.compareAndSet(list, f :: list)) onSessionEvent(f)
  }
}

object Connector {
  type EventHandler = PartialFunction[StateEvent, Unit]

  case class RoundRobin(connectors: Connector*)(
    implicit ec: ExecutionContext) extends Connector {
    private[this] var index = 0
    private[this] def incrIndex() =
      synchronized {
        if (index == Int.MaxValue) {
          index = 0
        }
        index = index + 1
        index
      }
    protected[this] def next() =      
      connectors(incrIndex() % connectors.length)

    def apply(): Future[ZooKeeper] = next().apply()

    /** Disconnect from all ZooKeeper servers. */
    def release(): Future[Unit] =
      Future.sequence {
        connectors map { _.release() }
      }.map(_ => Future.successful(()))
  }

}
