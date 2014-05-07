package zoey


import org.apache.zookeeper.{ CreateMode, KeeperException, WatchedEvent, ZKUtil }
import org.apache.zookeeper.common.PathUtils
import org.apache.zookeeper.data.{ ACL, Stat }
import scala.collection.JavaConverters._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Try }

trait ZNode extends Paths {
  protected [zoey] val zkClient: ZkClient

  lazy val parent: ZNode = ZNode(zkClient, parentPath)

  def client = zkClient

  /** @return a reference to a child znode by path suffix */
  def apply(child: String): ZNode = ZNode(client, childPath(child))

  /** @return an exists reference */
  def apply(stat: Stat): ZNode.Exists = ZNode.Exists(this, stat)

  /** @return an children reference */
  def apply(stat: Stat, children: Seq[String]): ZNode.Children =
    ZNode.Children(this, stat, children)

  /** @return a data reference */
  def apply(stat: Stat, bytes: Array[Byte]): ZNode.Data =
    ZNode.Data(this, stat, bytes)

  /** @return a reference to this znode
   *  with an alternative underlying client */
  def withZkClient(zk: ZkClient): ZNode =
    ZNode(zk, path)

  /** creates the current znode reference if it does not exist */
  def create(
    data: Array[Byte] = Array.empty[Byte],
    acls: Seq[ACL]    = zkClient.acl,
    mode: CreateMode  = zkClient.mode,
    child: Option[String] = None,
    parent: Boolean = false)
   (implicit ec: ExecutionContext): Future[ZNode] = {
    val newPath = child.map("%s/%s".format(path, _)).getOrElse(path)
    zkClient.retrying { zk =>
      val result = new StringCallbackPromise
      zk.create(newPath, data, acls.asJava, mode, result, null)
      result.future.map(zkClient(_)).recoverWith {
        case _: KeeperException.NoNodeException if (parent) =>
          def mkdirp(target: String, makes: List[String] = Nil): Future[ZNode] =
            zkClient(target).exists().recoverWith {
              case _: KeeperException.NoNodeException =>
                target.take(target.lastIndexOf("/")) match {
                  case empty if empty.isEmpty =>
                    Future.sequence((target :: makes).map(zkClient(_).create(acls = acls, mode = mode))).map(_.last)
                  case parent =>
                    mkdirp(parent, makes :+ target)
                }
            }
          mkdirp(newPath.take(newPath.lastIndexOf("/"))).flatMap {
            case _ => create(data, acls, mode, child, false)
          }
      }
    }
  }

  /**  deletes the current znode reference at a specific version */
  def delete(version: Int = 0)
   (implicit ec: ExecutionContext): Future[ZNode] =
    zkClient.retrying { zk =>
      val result = new UnitCallbackPromise
      zk.delete(path, version, result, null)
      result.future map { _ => this }
    }

  /** deletes the current znode referece and all of its children,
   *  and all of their children, and so on */
  def deleteAll
    (implicit ec: ExecutionContext): Future[ZNode] =
      zkClient.retrying { zk =>
        val result = new UnitCallbackPromise
        ZKUtil.deleteRecursive(zk, path, result, null)
        result.future map { _ => this }
      }

  /** sets the data associated with the current znode reference for a given version */
  def setData(data: Array[Byte], version: Int)
   (implicit ec: ExecutionContext): Future[ZNode.Data] =
    zkClient.retrying { zk =>
      val result = new ExistsCallbackPromise(this)
      zk.setData(path, data, version, result, null)
      result.future map { _.apply(data) }
    }

  /** flushes channel between process and the leader */
  def sync()(implicit ec: ExecutionContext): Future[ZNode] =
    zkClient.retrying { zk =>
      val result = new UnitCallbackPromise
      zk.sync(path, result, null)
      result.future map { _ => this }
    }

  val getChildren: ZOp[ZNode.Children] = new ZOp[ZNode.Children] {

    /** Get this ZNode with its metadata and children */
    def apply()(implicit ec: ExecutionContext): Future[ZNode.Children] =
      zkClient.retrying { zk =>
        val result = new ChildrenCallbackPromise(ZNode.this)
        zk.getChildren(path, false, result, null)
        result.future
      }

    def watch()(implicit ec: ExecutionContext) =
      zkClient.retrying { zk =>
        val result = new ChildrenCallbackPromise(ZNode.this)
        val update = new EventPromise
        zk.getChildren(path, update, result, null)
        result.future map { c => ZNode.Watch(Try(c), update.future) } // should handle KeeperException.NoNodeException
      }
    }

  val getData: ZOp[ZNode.Data] = new ZOp[ZNode.Data] {

    def apply()(implicit ec: ExecutionContext): Future[ZNode.Data] =
      zkClient.retrying { zk =>
        val result = new DataCallbackPromise(ZNode.this)
        zk.getData(path, false, result, null)
        result.future
      }

    def watch()(implicit ec: ExecutionContext) =
      zkClient.retrying { zk =>
        val result = new DataCallbackPromise(ZNode.this)
        val update = new EventPromise
        zk.getData(path, update, result, null)
        result.future map { d => ZNode.Watch(Try(d), update.future) } // should handle KeeperException.NoNodeException
      }
    }

  val exists: ZOp[ZNode.Exists] = new ZOp[ZNode.Exists] {

    def apply()(implicit ec: ExecutionContext) =
      zkClient.retrying { zk =>
        val result = new ExistsCallbackPromise(ZNode.this)
        zk.exists(path, false, result, null)
        result.future
      }

    /** Get this node's metadata and watch for updates */
    def watch()(implicit e: ExecutionContext) =
      zkClient.retrying { zk =>
        val result = new ExistsCallbackPromise(ZNode.this)
        val update = new EventPromise
        zk.exists(path, update, result, null)
        result.future.map { e => ZNode.Watch(Try(e), update.future) } // should handle KeeperException.NoNodeException
      }
    }

  override def hashCode = path.hashCode

  override def equals(other: Any) = other match {
    case z @ ZNode(_) => z.hashCode == hashCode
    case _ => false
  }

  override def toString = s"ZNode($path)"
}

/**
 * ZNode utilities and return types.
 */
object ZNode {

  /** @return a new ZNode, if the path is invalid
   *  and illegal argument exception will be thrown */
  def apply(zk: ZkClient, _path: String) = new ZNode {
    PathUtils.validatePath(_path)
    protected[zoey] val zkClient = zk
    val path = _path
  }

  def unapply(znode: ZNode) = Some(znode.path)

  object Error {
    def unapply(ke: KeeperException) = Option(ke.getPath)
  }

  trait Exists extends ZNode {
    val stat: Stat

    override def equals(other: Any) = other match {
      case Exists(p, s) => (p == path && s == stat)
      case o => super.equals(o)
    }

    def apply(children: Seq[String]): ZNode.Children = apply(stat, children)
    def apply(bytes: Array[Byte]): ZNode.Data = apply(stat, bytes)
  }

  object Exists {
    def apply(znode: ZNode, _stat: Stat) = new Exists {
      val path = znode.path
      protected[zoey] val zkClient = znode.zkClient
      val stat = _stat
    }
    def apply(znode: Exists): Exists = apply(znode, znode.stat)
    def unapply(znode: Exists) = Some((znode.path, znode.stat))
  }

  trait Children extends Exists {
    val stat: Stat
    val children: Seq[ZNode]

    override def equals(other: Any) = other match {
      case Children(p, s, c) => (p == path && s == stat && c == children)
      case o => super.equals(o)
    }
  }

  object Children {
    def apply(znode: Exists, _children: Seq[ZNode]): Children =
      new Children {
        val path = znode.path
        protected[zoey] val zkClient = znode.zkClient
        val stat = znode.stat
        val children = _children
      }
    def apply(znode: ZNode, stat: Stat, children: Seq[String]): Children =
      apply(Exists(znode, stat), children.map(znode.apply))
    def unapply(z: Children) = Some((z.path, z.stat, z.children))
  }

  trait Data extends Exists {
    val stat: Stat
    val bytes: Array[Byte]

    override def equals(other: Any) = other match {
      case Data(p, s, b) => (p == path && s == stat && b == bytes)
      case o => super.equals(o)
    }
  }

  object Data {
    def apply(znode: ZNode, _stat: Stat, _bytes: Array[Byte]) =
      new Data {
        val path = znode.path
        protected[zoey] val zkClient = znode.zkClient
        val stat = _stat
        val bytes = _bytes
      }
    def apply(znode: Exists, bytes: Array[Byte]): Data =
      apply(znode, znode.stat, bytes)
    def unapply(znode: Data) =
      Some((znode.path, znode.stat, znode.bytes))
  }

  case class Watch[T <: Exists](
    result: Try[T], update: Future[WatchedEvent]) {
    def map[V <: Exists](toV: T => V): Watch[V] =
      new Watch(result.map(toV), update)
  }
}
