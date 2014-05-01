package zoey

import org.scalatest.{ BeforeAndAfterAll, FunSpec }
import java.net.InetSocketAddress
import scala.concurrent.duration.Duration
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.NonFatal

class ZkClientSpec extends FunSpec with BeforeAndAfterAll with ZkServer {
  lazy val host = new InetSocketAddress(0)
  lazy val svr = server(host)

  describe("ZkClient") {
    it ("should work") {
      val zk = ZkClient(s"${host.getHostName}:${svr.getClientPort}")
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
