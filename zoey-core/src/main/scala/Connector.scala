package zoey

import scala.concurrent.{ ExecutionContext, Future }
import java.util.concurrent.atomic.AtomicReference
import org.apache.zookeeper.ZooKeeper
import scala.annotation.tailrec

/** A connector defines a means of connecting to and referencing a zookeeper to
 *  perform requests on and to release sources */
trait Connector {
  protected[this] val listeners =
    new AtomicReference[List[Connector.EventHandler]](Nil)

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

  /** A roundrobin connector distributes requests for client access across
   *  a number of defined connectors */
  case class RoundRobin(connectors: Connector*)
   (implicit ec: ExecutionContext) extends Connector {
    @volatile private[this] var iter = connectors.iterator
    private[this] val robin = new Iterator[Connector] {
      def hasNext = connectors.nonEmpty
      def next = {
        if (!iter.hasNext) {
          iter = connectors.iterator
        }
        iter.next
      }
    }

    def apply(): Future[ZooKeeper] = robin.next().apply()

    /** Disconnect from all ZooKeeper servers. */
    def release(): Future[Unit] =
      Future.sequence {
        connectors map { _.release() }
      }.map(_ => Future.successful(()))
  }

}
