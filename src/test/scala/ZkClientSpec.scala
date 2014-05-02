package zoey

import org.scalatest.{ BeforeAndAfterAll, FunSpec }
import scala.concurrent.duration.Duration
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.NonFatal

class ZkClientSpec extends FunSpec with BeforeAndAfterAll with ZkServer {
  lazy val svr = server()

  describe("ZkClient") {
    it ("should work") {
      val zk = ZkClient(svr.clientAddr)
      val path = "/test"
      val future = zk.aclOpenUnsafe.ephemeral(path).create()
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
