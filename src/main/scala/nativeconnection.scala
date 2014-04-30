package zoey

import scala.annotation.tailrec
import scala.concurrent.{ Await, ExecutionContext, Future, Promise }
import scala.concurrent.duration.Duration
import org.apache.zookeeper.{ ZooKeeper, Watcher, WatchedEvent }
import java.util.concurrent.atomic.AtomicReference

case class NativeConnector(
  connectString: String,
  connectTimeout: Option[Duration],
  sessionTimeout: Duration
  )(implicit ec: ExecutionContext)
  extends Connector {

  protected [this] def mkConnection =
    new NativeConnector.Connection(
      connectString, connectTimeout, sessionTimeout, listeners.get())

  // register a session event listener for this listener
  onSessionEvent {
    case e@StateEvent.Expired =>
      println(s"session expired $e")
      Await.result(release(), Duration.Inf)
    case other => ()
  }

  @volatile private[this] var connection:
    Option[NativeConnector.Connection] = None

  // connection timeout
  def apply(): Future[ZooKeeper] =
    connection.getOrElse {
      val c = mkConnection
      connection = Some(c)
      c
    }.apply()

  def release(): Future[Unit] =
    connection match {
      case None =>
        Future(())
      case Some(c) =>
        connection = None
        c.release()
    }
}

object NativeConnector {
  protected class Connection(
    connectString: String,
    connectTimeout: Option[Duration],
    sessionTimeout: Duration,
    sessionListeners: List[Connector.EventHandler]) {

    @volatile protected[this] var zookeeper: Option[ZooKeeper] = None

    /** defer some behavior until afer we receive a state event */
    private class ConnectionWatch(
      andThen: (StateEvent, ZooKeeper) => Unit) extends Watcher {
      private [this] val ref = new AtomicReference[ZooKeeper]
      def process(e: WatchedEvent) {
        @tailrec
        def await(zk: ZooKeeper): ZooKeeper =
          if (zk == null) await(ref.get()) else zk
        val zk = await(ref.get())
        StateEvent(e) match {
          case c @ StateEvent.Connected =>
            andThen(c, zk)
          case e =>
            sys.error(s"rec unexpected event $e")
        }
      }
      def set(zk: ZooKeeper) =
        if (!ref.compareAndSet(null, zk)) sys.error(
          "ref already set!")
    }

    protected[this] var connectPromise = Promise[ZooKeeper]()
    protected[this] val releasePromise = Promise[Unit]()

    lazy val connected: Future[ZooKeeper] = connectPromise.future
    lazy val released: Future[Unit] = releasePromise.future

    def apply(): Future[ZooKeeper] = {
      zookeeper = zookeeper orElse Some(mkZooKeeper)
      connected
    }

    def release()(implicit ec: ExecutionContext): Future[Unit] = Future {
      zookeeper.foreach { zk =>
        println("releasing zk. l8tr.")
        zk.close()
        zookeeper = None
        releasePromise.success(())
      }
    }

    protected[this] def mkZooKeeper: ZooKeeper = {
      val onConnect = new ConnectionWatch({
        case (ev, client) =>
          sessionListeners.foreach {
            _.lift(ev)
          }
          connectPromise.success(client)
      })
      val zk = new ZooKeeper(
        connectString, sessionTimeout.toMillis.toInt, onConnect)
      onConnect.set(zk)
      zk
    }
  }
}
