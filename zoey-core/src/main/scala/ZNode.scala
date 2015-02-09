package zoey

import org.apache.zookeeper.{ CreateMode, KeeperException, WatchedEvent, ZKUtil }
import org.apache.zookeeper.common.PathUtils
import org.apache.zookeeper.data.{ ACL, Stat }
import scala.collection.JavaConverters._
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.util.Try

trait ZNode extends Paths {
  def keeper: ZkClient

  lazy val parent: ZNode = ZNode(keeper, parentPath)

  /** @return a reference to a child znode by path suffix */
  def apply(child: String): ZNode = ZNode(keeper, childPath(child))

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
    data: Array[Byte]     = Array.empty[Byte],
    acls: Seq[ACL]        = keeper.acl,
    mode: CreateMode      = keeper.mode,
    child: Option[String] = None,
    parent: Boolean = false)
   (implicit ec: ExecutionContext): Future[ZNode] = {
    val newPath = child.fold(path)(childPath)
    keeper.retrying { zk =>
      val result = new AsyncCallbackPromise.Str
      zk.create(newPath, data, acls.asJava, mode, result, null)
      result.future.map(keeper(_)).recoverWith {
        case _: KeeperException.NoNodeException if (parent) =>
          ZNode.mkdirp(
            keeper, acls, newPath.take(newPath.lastIndexOf("/"))).flatMap {
              case _ => create(data, acls, mode, child, false)
            }
      }
    }
  }

  /**  deletes the current znode reference at a specific version */
  def delete(version: Int = 0)
   (implicit ec: ExecutionContext): Future[ZNode] =
    keeper.retrying { zk =>
      val result = new AsyncCallbackPromise.Unit
      zk.delete(path, version, result, null)
      result.future map { _ => this }
    }

  /** deletes the current znode referece and all of its children,
   *  and all of their children, and so on */
  def deleteAll
    (implicit ec: ExecutionContext): Future[ZNode] =
      keeper.retrying { zk =>
        Future.sequence(ZKUtil.listSubTreeBFS(zk, path).asScala.reverse.map { del =>
          val result = new AsyncCallbackPromise.Unit
          zk.delete(del, -1, result, null)
          result.future map { _ => this }
        }).map(_.head)
      }

  /** sets the data associated with the current znode reference for a given version */
  def set(data: Array[Byte], version: Int)
   (implicit ec: ExecutionContext): Future[ZNode.Data] =
    keeper.retrying { zk =>
      val result = new AsyncCallbackPromise.Exists(this)
      zk.setData(path, data, version, result, null)
      result.future map { _.apply(data) }
    }

  /** flushes channel between process and the leader */
  def sync()(implicit ec: ExecutionContext): Future[ZNode] =
    keeper.retrying { zk =>
      val result = new AsyncCallbackPromise.Unit
      zk.sync(path, result, null)
      result.future map { _ => this }
    }

  val children: ZOp[ZNode.Children] = new ZOp[ZNode.Children] {

    /** Get this ZNode with its metadata and children */
    def apply()(implicit ec: ExecutionContext): Future[ZNode.Children] =
      keeper.retrying { zk =>
        val result = new AsyncCallbackPromise.Children(ZNode.this)
        zk.getChildren(path, false, result, null)
        result.future
      }

    def watch()(implicit ec: ExecutionContext): Future[ZNode.Watch[ZNode.Children]] =
      keeper.retrying { zk =>
        val result = new AsyncCallbackPromise.Children(ZNode.this)
        val update = new EventPromise
        zk.getChildren(path, update, result, null)
        result.future map { c => ZNode.Watch(Try(c), update.future) } // should handle KeeperException.NoNodeException
      }
    }

  val data: ZOp[ZNode.Data] = new ZOp[ZNode.Data] {

    def apply()(implicit ec: ExecutionContext): Future[ZNode.Data] =
      keeper.retrying { zk =>
        val result = new AsyncCallbackPromise.Data(ZNode.this)
        zk.getData(path, false, result, null)
        result.future
      }

    def watch()(implicit ec: ExecutionContext): Future[ZNode.Watch[ZNode.Data]] =
      keeper.retrying { zk =>
        val result = new AsyncCallbackPromise.Data(ZNode.this)
        val update = new EventPromise
        zk.getData(path, update, result, null)
        result.future map { d => ZNode.Watch(Try(d), update.future) } // should handle KeeperException.NoNodeException
      }
    }

  val exists: ZOp[ZNode.Exists] = new ZOp[ZNode.Exists] {

    def apply()(implicit ec: ExecutionContext): Future[ZNode.Exists] =
      keeper.retrying { zk =>
        val result = new AsyncCallbackPromise.Exists(ZNode.this)
        zk.exists(path, false, result, null)
        result.future
      }

    /** Get this node's metadata and watch for updates */
    def watch()(implicit e: ExecutionContext): Future[ZNode.Watch[ZNode.Exists]] =
      keeper.retrying { zk =>
        val result = new AsyncCallbackPromise.Exists(ZNode.this)
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
  private[this] val SeqName = """(.+)-(\d+)""".r
  implicit val sequentialOrder: Ordering[ZNode] =
    Ordering.by {
      node => (node.parentPath, node.name match {
        case SeqName(name, counter) =>
          (name, counter.toInt)
        case name =>
          (name, 0)
      })
    }

  def mkdirp
   (zk: ZkClient, acls: Seq[ACL], path: String, makes: List[String] = Nil)
   (implicit ec: ExecutionContext): Future[ZNode] =
    zk(path).exists().recoverWith {
      case _: KeeperException.NoNodeException =>
        path.take(path.lastIndexOf("/")) match {
          case empty if empty.isEmpty =>
            zk(path).create(acls = acls, mode = zk.mode)
          case parent =>
            mkdirp(zk, acls, parent, makes :+ path)
        }
    }.flatMap { value =>
      // so what if we created makes above
      if (makes.nonEmpty) Future.sequence(
        makes.map(zk(_).create(acls = acls, mode = zk.mode))).map(_.last)
      else Promise.successful(value).future
    }

  /** @return a new ZNode, if the path is invalid
   *  and illegal argument exception will be thrown */
  def apply(zk: ZkClient, _path: String): ZNode = new ZNode {
    PathUtils.validatePath(_path)
    val keeper = zk
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
      val keeper = znode.keeper
      val stat = _stat
    }
    def apply(znode: Exists): Exists = apply(znode, znode.stat)
    def unapply(znode: Exists) = Some((znode.path, znode.stat))
  }

  trait Children extends Exists {
    val stat: Stat
    val nodes: Seq[ZNode]

    override def equals(other: Any) = other match {
      case Children(p, s, c) => (p == path && s == stat && c == children)
      case o => super.equals(o)
    }
  }

  object Children {
    def apply(znode: Exists, _children: Seq[ZNode]): Children =
      new Children {
        val path   = znode.path
        val keeper = znode.keeper
        val stat   = znode.stat
        val nodes  = _children
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
        val keeper = znode.keeper
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
