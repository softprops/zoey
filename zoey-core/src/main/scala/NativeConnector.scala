package zoey

import scala.annotation.tailrec
import scala.concurrent.{ Await, ExecutionContext, Future, Promise }
import scala.concurrent.duration.{ Duration, FiniteDuration }
import org.apache.zookeeper.{ ZooKeeper, Watcher, WatchedEvent }
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference

/** A Connector that holds a reference to a zookeeper connection */
case class NativeConnector(
  connectString: String,
  connectTimeout: Option[FiniteDuration],
  sessionTimeout: FiniteDuration,
  authInfo: Option[AuthInfo])
 (implicit ec: ExecutionContext)
  extends Connector {

  /** A `cell` containing a reference to a Connection if one was resolved */
  @volatile private[this] var connection:
    Option[NativeConnector.Connection] = None

  protected [this] def connect() =
    new NativeConnector.Connection(
      connectString,
      connectTimeout,
      sessionTimeout,
      listeners.get(),
      authInfo)

  // register a session event listener for this Connector
  onSessionEvent {
    case StateEvent.Expired =>
      Await.result(close(), Duration.Inf)
  }

  /** lazily resolves a cached zookeeper connection */
  def apply(): Future[ZooKeeper] =
    connection.getOrElse {
      val c = connect()
      connection = Some(c)
      c
    }.apply().recoverWith {
      case e: NativeConnector.ConnectTimeoutException =>
        close() flatMap { _ => Future.failed(e) }
      case e =>
        Future.failed(e)
    }

  def close(): Future[Unit] =
    connection match {
      case None =>
        Connector.Closed
      case Some(ref) =>
        connection = None
        ref.close()
    }
}

object NativeConnector {

  case class ConnectTimeoutException(
    connectString: String, timeout: FiniteDuration)
    extends TimeoutException(s"timeout connecting to $connectString after $timeout")

  case object ClosedException
    extends RuntimeException("This connection was already closed")

  protected class Connection(
    connectString: String,
    connectTimeout: Option[FiniteDuration],
    sessionTimeout: FiniteDuration,
    sessionListeners: List[Connector.EventHandler],
    authInfo: Option[AuthInfo])
   (implicit val ec: ExecutionContext) {

    @volatile protected[this] var zookeeper: Option[ZooKeeper] = None

    override def toString =
      s"${getClass.getName}(${zookeeper.getOrElse("(disconnected)")})"

    /** defer some behavior until afer we receive a session  state event
     *  http://zookeeper.apache.org/doc/trunk/zookeeperProgrammers.html#ch_zkSessions */
    private class ConnectionWatch(
      andThen: (StateEvent, ZooKeeper) => Unit) extends Watcher {
      private [this] val ref = new AtomicReference[ZooKeeper]
      def process(e: WatchedEvent) {
        @tailrec
        def await(zk: ZooKeeper): ZooKeeper =
          if (zk == null) await(ref.get()) else zk
        val zk = await(ref.get())
        StateEvent(e) match {
          case e @ StateEvent.Connected =>
            andThen(e, zk)
          case _ =>
            // we capture session expired events in session listener
            // the underlying client handles disconnects
        }
      }
      def set(zk: ZooKeeper) =
        if (!ref.compareAndSet(null, zk)) sys.error(
          "ref already set!")
    }

    protected[this] var connectPromise = Promise[ZooKeeper]()
    protected[this] val closePromise = Promise[Unit]()

    /** if connectTimeout is defined, a secondary future will be scheduled
     *  to fail at this time. If this failure happens before the connection
     *  is promise is satisfied, the future returned with be that of the
     *  failure
     */
    lazy val connected: Future[ZooKeeper] = connectTimeout.map {
      undelay.Complete(connectPromise.future).within(
        _, ConnectTimeoutException(connectString, _))
    }.getOrElse(connectPromise.future)

    lazy val closed: Future[Unit] = closePromise.future

    def apply(): Future[ZooKeeper] =
      if (closed.isCompleted) Future.failed(ClosedException) else {
        zookeeper = zookeeper orElse Some(mkZooKeeper)
        connected
      }

    def close(): Future[Unit] = Future {
      zookeeper.foreach { zk =>
        zk.close()
        zookeeper = None
        closePromise.success(())
      }
    }

    protected[this] def mkZooKeeper: ZooKeeper = {
      val onConnect = new ConnectionWatch({
        case (ev, client) =>
          sessionListeners.foreach {
            _.lift(ev)
          }
          authInfo.foreach { info =>
            client.addAuthInfo(info.scheme, info.data)
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
