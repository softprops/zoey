package zoey

import scala.concurrent.{ Await, ExecutionContext, Future, Promise }
import scala.concurrent.duration.Duration
import org.apache.zookeeper.{ ZooKeeper, Watcher, WatchedEvent }

case class NativeConnector(
  connectString: String,
  connectTimeout: Option[Duration],
  sessionTimeout: Duration
  //timer: ???
  )(implicit ec: ExecutionContext)
  extends Connector {

  protected [this] def mkConnection =
    new NativeConnector.Connection(
      connectString, connectTimeout, sessionTimeout) //timer

  onSessionEvent {
    case StateEvent.Expired => Await.result(release(), Duration.Inf)
  }

  @volatile private var connection: Option[NativeConnector.Connection] = None

  // connection timeout
  def apply(): Future[ZooKeeper] =
    connection.getOrElse {
      val c = mkConnection
      //c.sessionEvents.foreach { event =>
        // send event to session broker
      //}
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
    sessionTimeout: Duration) {

    @volatile protected[this] var zookeeper: Option[ZooKeeper] = None

    protected[this] var connectPromise = Promise[ZooKeeper]()

    lazy val connected: Future[ZooKeeper] = connectPromise.future
/*      connectTimeout.map { to => 
        connectPromise.future within(to) rescue {
          case _: TimeoutException =>
            Future.exception(..)
        }
      }.getOrElse(connectPromise)*/

    protected[this] val releasePromise = Promise[Unit]()
    val released: Future[Unit] = releasePromise.future
    
    // val sessionEvents

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

    protected[this] def mkZooKeeper: ZooKeeper =
      new ZooKeeper(
        connectString, sessionTimeout.toMillis.toInt, new Watcher {
          def process(event: WatchedEvent) = println(event)
        })
  }
}
