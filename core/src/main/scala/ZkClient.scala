package zoey

import java.util.concurrent.atomic.AtomicReference
import scala.collection.JavaConverters._
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.concurrent.duration._
import scala.annotation.tailrec
import org.apache.zookeeper.{ CreateMode, ZooKeeper }
import org.apache.zookeeper.data.ACL
import org.apache.zookeeper.ZooDefs.Ids.{
  CREATOR_ALL_ACL, OPEN_ACL_UNSAFE, READ_ACL_UNSAFE }

trait ZkClient {
  protected [this] val connection: Connector

  val acl: Seq[ACL] = CREATOR_ALL_ACL.asScala

  val retryPolicy: RetryPolicy = RetryPolicy.None

  /** @return reference a ZNode by path */
  def apply(path: String): ZNode = znode(path)

  def znode(path: String): ZNode = ZNode(this, path)

  /** @return a reference to the current connections zookeeper instance */
  def apply(): Future[ZooKeeper] =
    retryPolicy(connection())

  def retried(
    max: Int = 8,
    delay: FiniteDuration = 500.millis,
    base: Int = 2)(
    implicit ec: ExecutionContext,
    timer: retry.Timer) =
    copy(_retryPolicy = RetryPolicy.Exponential(max, delay, base))

  final def retrying[T](op: ZooKeeper => Future[T])(
    implicit ec: ExecutionContext): Future[T] =
    retryPolicy(apply().flatMap(op))

  def onSessionEvent(f: Connector.EventHandler) =
    connection.onSessionEvent(f)

  def close(): Future[Unit] = connection.release()

  def mode: CreateMode = CreateMode.PERSISTENT

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
    _mode: CreateMode = mode,
    _retryPolicy: RetryPolicy = retryPolicy) = new ZkClient {
    val connection = _connector
    override val acl = _acl
    override val mode = _mode
    override val retryPolicy = _retryPolicy
  }

  override def toString =
    "ZkClient(%s)" format connection
}

object ZkClient {
  val DefaultHost = "0.0.0.0:2181"

  def apply(
    host: String = DefaultHost,
    connectTimeout: Option[FiniteDuration] = None,
    sessionTimeout: FiniteDuration = 4.seconds,
    authInfo: Option[AuthInfo] = None)
   (implicit ec: ExecutionContext): ZkClient =
      new ZkClient {
        val connection = new NativeConnector(
          host, connectTimeout, sessionTimeout, authInfo)
      }

   def roundRobin(
     hosts: Seq[String] = DefaultHost :: Nil,
     connectTimeout: Option[FiniteDuration] = None,
     sessionTimeout: FiniteDuration = 4.seconds,
     authInfo: Option[AuthInfo] = None)
    (implicit ec: ExecutionContext): ZkClient =
      new ZkClient {
        val connection = Connector.RoundRobin(hosts.map(
          new NativeConnector(
            _, connectTimeout, sessionTimeout, authInfo)):_*)
      }
}
