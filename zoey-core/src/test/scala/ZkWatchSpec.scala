package zoey

import org.apache.zookeeper.KeeperException
import org.scalatest.{ BeforeAndAfterAll, FunSpec }
import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, Promise }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.control.NonFatal

class ZkWatchSpec extends FunSpec with BeforeAndAfterAll
  with testing.ZkServer {

  lazy val svr = server()
  lazy val zk = ZkClient(svr.connectStr)
    .retryWith(retry.Pause(3, 3.seconds))

  describe("ZkNode#watch") {
    it ("should work") {
      val node = zk.aclOpenUnsafe("/foo")
      val promise = Promise[String]()
      val future =
        for {
          foo <- node.create("initial".getBytes("utf8"), parent = true)
          watch  <- foo.data.watch()
        } yield watch match {
          case ZNode.Watch(data, update) =>
            update.foreach {
              case NodeEvent.DataChanged(data) =>
                foo.data().foreach {
                  updated =>
                    promise.success(new String(updated.bytes))
                }
            }
            watch
        }

      future.onFailure {
        case NonFatal(e) =>
          println("future failed...")
          e.printStackTrace
      }

      future.onComplete {
        case _ =>
          node.exists().foreach {
            case node =>
              node.set("data".getBytes("utf8"), node.stat.getVersion)
          }
      }
      assert(Await.result(promise.future, 10.seconds) === "data")
    }
  }

  override def afterAll() {
    zk.close()
    svr.close()
  }
}
