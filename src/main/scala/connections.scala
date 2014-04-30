package zoey

import scala.concurrent.Future
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
}
