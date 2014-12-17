package zoey

import scala.concurrent.{ Future, Promise }
import java.util.{ List => JList }
import org.apache.zookeeper.{ AsyncCallback, KeeperException }
import org.apache.zookeeper.data.Stat
import scala.collection.JavaConverters._

private[zoey] trait AsyncCallbackPromise[T] {
  private[this] val promise = Promise[T]()
  protected def process(rc: Int, path: String)(result: => T) {
    KeeperException.Code.get(rc) match {
      case KeeperException.Code.OK =>
        promise.success(result)
      case code =>
        promise.failure(KeeperException.create(code, path))
    }
  }
  def future: Future[T] = promise.future
}

private[zoey] object AsyncCallbackPromise {
  class Str extends AsyncCallbackPromise[String]
    with AsyncCallback.StringCallback {
    def processResult(rc: Int, path: String, ctx: AnyRef, name: String) =
      process(rc, path) { name }
  }

  class Unit extends AsyncCallbackPromise[scala.Unit]
    with AsyncCallback.VoidCallback {
    def processResult(rc: Int, path: String, ctx: AnyRef) =
      process(rc, path) { }
  }

  class Exists(znode: ZNode)
    extends AsyncCallbackPromise[ZNode.Exists]
    with AsyncCallback.StatCallback {
    def processResult(rc: Int, path: String, ctx: AnyRef, stat: Stat) =
      process(rc, path) {
        znode(stat)
      }
  }

  class Children(znode: ZNode)
    extends AsyncCallbackPromise[ZNode.Children]
    with AsyncCallback.Children2Callback {
    def processResult(rc: Int, path: String, ctx: AnyRef, children: JList[String], stat: Stat) =
      process(rc, path) {
        znode(stat, children.asScala.toSeq)
      }
  }

  class Data(znode: ZNode)
    extends AsyncCallbackPromise[ZNode.Data]
    with AsyncCallback.DataCallback {
    def processResult(rc: Int, path: String, ctx: AnyRef, bytes: Array[Byte], stat: Stat) =
      process(rc, path) {
        znode(stat, bytes)
      }
  }
}

