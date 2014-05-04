package zoey

import org.scalatest.{ BeforeAndAfterAll, FunSpec }
import scala.concurrent.duration._
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import retry.Defaults.timer
import scala.util.control.NonFatal
import java.net.InetSocketAddress

class ConnectionIssuesSpec extends FunSpec with BeforeAndAfterAll with testing.ZkServer {
  val addrs = (2181 to 2183).map(new InetSocketAddress(_))
  lazy val svr = server(addrs.last)

  describe("ConnectionIssues") {
    it ("should reconnect") {
      val strAddrs = addrs.map(addr => s"${addr.getHostName}:${addr.getPort}").toList
      svr // trigger svr startup
      val cli = ZkClient.roundRobin(
        strAddrs, Some(2.seconds)).retried(max = 3)
      val future = cli()
      future.onFailure {
        case NonFatal(e) => println(s"failing $e")
      }
      future.onSuccess {
        case e => println(s"round robin success $e")
      }
      Await.ready(future, Duration.Inf)
    }
  }

  override def afterAll() {
    svr.shutdown()
  }
}
