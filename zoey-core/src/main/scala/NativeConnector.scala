package zoey

import scala.annotation.tailrec
import scala.concurrent.{ Await, ExecutionContext, Future, Promise }
import scala.concurrent.duration.{ Duration, FiniteDuration }
import org.apache.zookeeper.{ ZooKeeper, Watcher, WatchedEvent }
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference

case class NativeConnector(
  connectString: String,
  connectTimeout: Option[FiniteDuration],
  sessionTimeout: FiniteDuration,
  authInfo: Option[AuthInfo])
 (implicit ec: ExecutionContext)
  extends Connector {

  protected [this] def mkConnection =
    new NativeConnector.Connection(
      connectString, connectTimeout, sessionTimeout, listeners.get(), authInfo)

  // register a session event listener for this listener
  onSessionEvent {
    case StateEvent.Expired =>
      Await.result(release(), Duration.Inf)
    case other => ()
  }

  @volatile private[this] var connection:
    Option[NativeConnector.Connection] = None

  def apply(): Future[ZooKeeper] =
    connection.getOrElse {
      val c = mkConnection
      connection = Some(c)
      c
    }.apply().recoverWith {
      case e: NativeConnector.ConnectTimeoutException =>
        release() flatMap { _ => Future.failed(e) }
      case e =>
        Future.failed(e)
    }

  def release(): Future[Unit] =
    connection match {
      case None =>
        Future.successful(())
      case Some(c) =>
        connection = None
        c.release()
    }
}

object NativeConnector {

  case class ConnectTimeoutException(connectString: String, timeout: FiniteDuration)
    extends TimeoutException(s"timeout connecting to $connectString after $timeout")

  protected class Connection(
    connectString: String,
    connectTimeout: Option[FiniteDuration],
    sessionTimeout: FiniteDuration,
    sessionListeners: List[Connector.EventHandler],
    authInfo: Option[AuthInfo])
   (implicit val ec: ExecutionContext) {

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

    /** if connectTimeout is defined, a secondary future will be scheduled
     *  to fail at this time. If this failure happens before the connection
     *  is promise is satisfied, the future returned with be that of the
     *  failure
     */
    lazy val connected: Future[ZooKeeper] = connectTimeout.map { to =>
      val prom = Promise[ZooKeeper]()
      val fail = odelay.Delay(to) {
        prom.failure(
          ConnectTimeoutException(connectString, to))
      }
      val success = connectPromise.future
      success.onComplete { case _ => fail.cancel() }
      Future.firstCompletedOf(success :: prom.future :: Nil)
    }.getOrElse(connectPromise.future)

    lazy val released: Future[Unit] = releasePromise.future

    def apply(): Future[ZooKeeper] = {
      zookeeper = zookeeper orElse Some(mkZooKeeper)
      connected
    }

    def release()(implicit ec: ExecutionContext): Future[Unit] = Future {
      zookeeper.foreach { zk =>
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
