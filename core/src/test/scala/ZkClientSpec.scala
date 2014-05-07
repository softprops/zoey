package zoey

import org.apache.zookeeper.KeeperException
import org.scalatest.{ BeforeAndAfterAll, FunSpec }
import scala.concurrent.duration.Duration
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.NonFatal

class ZkClientSpec extends FunSpec with BeforeAndAfterAll
  with testing.ZkServer {

  lazy val svr = server()

  describe("ZkClient") {
    it ("should work") {
      val zk = ZkClient(svr.clientAddr)
      val path = "/test/parent/grandparent"
      val future =
        for {
          parent <- zk.aclOpenUnsafe(path).create(parent = true)
          foo    <- parent.create(child = Some("foo"))
          bar    <- parent.create(child = Some("bar"))
          kids   <- parent.getChildren()
          if kids.children.toSet == Set(foo, bar)
          after  <- parent.deleteAll
          _      <- after.exists().map(_ => true).recover {
            case _: KeeperException.NoNodeException =>
              true
          }
        } yield after

      future.onFailure {
        case NonFatal(e) => e.printStackTrace
      }
      assert(Await.result(future, Duration.Inf).path === path)
      zk.close()
    }
  }

  override def afterAll() {
    svr.shutdown()
  }
}
