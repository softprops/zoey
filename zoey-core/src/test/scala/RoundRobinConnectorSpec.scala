package zoey

import org.scalatest.{ BeforeAndAfterAll, FunSpec }
import scala.concurrent.duration._
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.NonFatal
import java.net.InetSocketAddress

class RoundRobinConnectorSpec extends FunSpec
  with BeforeAndAfterAll with testing.ZkServer {
  val addrs = (2181 to 2183).map(new InetSocketAddress(_))
  val strAddrs = addrs.map(addr => s"${addr.getHostName}:${addr.getPort}").toList
  lazy val svr = server(addrs.last)
  lazy val cli = ZkClient.roundRobin(
    strAddrs, Some(2.seconds)).retryTimes(3)

  describe("RoundRobinConnector") {
    it ("should retry failed operations on each server provided") {
      svr // trigger svr startup
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
    cli.close()
    svr.close()
  }
}
