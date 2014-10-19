package zoey

import java.util.concurrent.atomic.AtomicReference
import org.apache.zookeeper.{ CreateMode, ZooKeeper }
import org.apache.zookeeper.data.ACL
import org.apache.zookeeper.ZooDefs.Ids.{
  CREATOR_ALL_ACL, OPEN_ACL_UNSAFE, READ_ACL_UNSAFE }
import scala.collection.JavaConverters._
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._

class ZkClient(
  protected[this] val connection: Connector,
  val mode: CreateMode                  = CreateMode.PERSISTENT,
  val acl: Seq[ACL]                     = CREATOR_ALL_ACL.asScala,
  protected[this] val retryPolicy: Option[retry.Policy] = None)
 (implicit ec: ExecutionContext) {

  /** @return a reference to a ZNode by path */
  def apply(path: String): ZNode = znode(path)

  /** @return a reference to a zNode by path */
  def znode(path: String): ZNode = ZNode(this, path)

  /** @return a reference to the current connection's zookeeper instance */
  def apply(): Future[ZooKeeper] = connection()

  /** @return an operation retried given the current zookeeper instance */
  def retrying[T]
   (op: ZooKeeper => Future[T]): Future[T] =
    retryPolicy match {
      case Some(policy) =>
        // the resulting value is always considered a success (exceptions are not)
        implicit val success = retry.Success.always
        policy(() => apply().flatMap(op))
      case _ =>
        apply().flatMap(op)
    }

  /** registers a new event handler for session events */
  def onSessionEvent(f: Connector.EventHandler) =
    connection.onSessionEvent(f)

  /** closes client and releases resources */
  def close(): Future[Unit] = connection.close()

  // modes

  def ephemeral = copy(_mode = CreateMode.EPHEMERAL)

  def ephemeralSequential = copy(_mode = CreateMode.EPHEMERAL_SEQUENTIAL)

  def persistent = copy(_mode = CreateMode.PERSISTENT)

  def persistentSequential = copy(_mode = CreateMode.PERSISTENT_SEQUENTIAL)

  // acls

  def aclCreatorAll = copy(_acl = CREATOR_ALL_ACL.asScala)

  def aclOpenUnsafe = copy(_acl = OPEN_ACL_UNSAFE.asScala)

  def aclReadUnsafe = copy(_acl = READ_ACL_UNSAFE.asScala)

  // retries

  def retryWith(policy: retry.Policy) = copy(_retryPolicy = Some(policy))

  def retryTimes(max: Int) = retryWith(retry.Directly(max))

  def retryPausing(max: Int = 4, delay: FiniteDuration = 1.second) =
    retryWith(retry.Pause(max, delay))
 
  def retryBackoff(
    max: Int = 8,
    delay: FiniteDuration = 1.second,
    base: Int = 2) =
    retryWith(retry.Backoff(max, delay, base))

  protected[this] def copy(
    _acl: Seq[ACL]                     = acl,
    _mode: CreateMode                  = mode,
    _retryPolicy: Option[retry.Policy] = retryPolicy) =
    new ZkClient(connection, _mode, _acl, _retryPolicy)

  override def toString =
    s"ZkClient($connection)"
}

object ZkClient {
  val DefaultHost = "0.0.0.0:2181"

  def apply(
    host: String                           = DefaultHost,
    connectTimeout: Option[FiniteDuration] = None,
    sessionTimeout: FiniteDuration         = 4.seconds,
    authInfo: Option[AuthInfo]             = None)
   (implicit ec: ExecutionContext): ZkClient =
    new ZkClient(new NativeConnector(
      host, connectTimeout, sessionTimeout, authInfo))

   def roundRobin(
     hosts: Seq[String]                     = DefaultHost :: Nil,
     connectTimeout: Option[FiniteDuration] = None,
     sessionTimeout: FiniteDuration         = 4.seconds,
     authInfo: Option[AuthInfo]             = None)
    (implicit ec: ExecutionContext): ZkClient =
     new ZkClient(Connector.RoundRobin(hosts.map(
          new NativeConnector(
            _, connectTimeout, sessionTimeout, authInfo)):_*))
}
