package zoey

import java.util.concurrent.atomic.AtomicReference
import scala.collection.JavaConverters._
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.concurrent.duration._
import scala.annotation.tailrec
import org.apache.zookeeper.{ CreateMode, ZooKeeper }
import org.apache.zookeeper.data.ACL
import org.apache.zookeeper.ZooDefs.Ids.CREATOR_ALL_ACL

trait ZkClient {
  protected [this] val connection: Connector

  val acl: Seq[ACL] = CREATOR_ALL_ACL.asScala

  def apply(path: String): ZNode = ZNode(this, path)

  def apply(): Future[ZooKeeper] = connection()

  def retrying[T](op: ZooKeeper => Future[T])(implicit ec: ExecutionContext): Future[T] =
    apply().flatMap(op)

  def onSessionEvent(f: Connector.EventHandler) =
    connection.onSessionEvent(f)

  def release(): Future[Unit] = connection.release()

  def mode: CreateMode = CreateMode.PERSISTENT
}

object ZkClient {
  def apply(
    host: String = "0.0.0.0:2181",
    connectTimeout: Option[FiniteDuration] = None,
    sessionTimeout: FiniteDuration = 4.seconds)(
    implicit ec: ExecutionContext): ZkClient =
      new ZkClient {
        val connection = new NativeConnector(
          host, connectTimeout, sessionTimeout)
      }
}
