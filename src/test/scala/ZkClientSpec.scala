package zoey

import org.scalatest.{ BeforeAndAfterAll, FunSpec }
import java.net.InetSocketAddress
import scala.concurrent.duration.Duration
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.NonFatal

class ZkClientSpec extends FunSpec with BeforeAndAfterAll with ZkServer {
  lazy val host = new InetSocketAddress(2181)
  lazy val svr = server(host)

  describe("ZkClient") {
    it ("should work") {
      val zk = ZkClient(s"${host.getHostName}:${svr.getClientPort}")
      val future = zk.aclOpenUnsafe.ephemeral("/test").create()
      future.foreach(println)
      future.onFailure {
        case NonFatal(e) => e.printStackTrace
      }
      Await.ready(future, Duration.Inf)
      zk.close()
    }
  }

  override def afterAll() {
    svr.shutdown()
  }
}
