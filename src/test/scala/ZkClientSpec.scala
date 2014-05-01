package zoey

import org.scalatest.{ BeforeAndAfterAll, FunSpec }
import java.net.InetSocketAddress
import scala.concurrent.duration.Duration
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global

class ZkClientSpec extends FunSpec with BeforeAndAfterAll with ZkServer {
  val host = new InetSocketAddress(0)
  val svr = server()

  describe("ZkClient") {
    it ("should work") {
      val zk = ZkClient(host.toString)
      val future = zk("/").getChildren.apply().map(_.children)
      future.foreach(println)
      Await.ready(future, Duration.Inf)
    }
  }

  override def afterAll() {
    svr.shutdown()
  }
}
