package zoey

import java.util.concurrent.atomic.AtomicReference
import scala.collection.JavaConverters._
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.concurrent.duration._
import scala.annotation.tailrec
import org.apache.zookeeper.{ CreateMode, ZooKeeper }
import org.apache.zookeeper.data.ACL
import org.apache.zookeeper.ZooDefs.Ids.{
  ANYONE_ID_UNSAFE, AUTH_IDS, CREATOR_ALL_ACL, OPEN_ACL_UNSAFE, READ_ACL_UNSAFE }

trait ZkClient {
  protected [this] val connection: Connector

  val acl: Seq[ACL] = CREATOR_ALL_ACL.asScala

  def apply(path: String): ZNode = ZNode(this, path)

  def apply(): Future[ZooKeeper] = connection()

  def retrying[T](op: ZooKeeper => Future[T])(implicit ec: ExecutionContext): Future[T] =
    apply().flatMap(op)

  def onSessionEvent(f: Connector.EventHandler) =
    connection.onSessionEvent(f)

  def close(): Future[Unit] = connection.release()

  def mode: CreateMode = CreateMode.PERSISTENT

  // http://zookeeper.apache.org/doc/r3.4.5/api/index.html?org/apache/zookeeper/ZooKeeper.html

  def aclCreatorAll = copy(_acl = CREATOR_ALL_ACL.asScala)

  def aclOpenUnsafe = copy(_acl = OPEN_ACL_UNSAFE.asScala)

  def aclReadUnsafe = copy(_acl = READ_ACL_UNSAFE.asScala)

  def ephemeral = copy(_mode = CreateMode.EPHEMERAL)

  def ephemeralSequential = copy(_mode = CreateMode.EPHEMERAL_SEQUENTIAL)

  def persistent = copy(_mode = CreateMode.PERSISTENT)

  def persistentSequential = copy(_mode = CreateMode.PERSISTENT_SEQUENTIAL)

  protected[this] def copy(
    _connector: Connector = connection,
    _acl: Seq[ACL] = acl,
    _mode: CreateMode = mode) = new ZkClient {
    val connection = _connector
    override val acl = _acl
    override val mode = _mode
  }
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
