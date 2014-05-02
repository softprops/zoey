package zoey

import org.scalatest.{ BeforeAndAfterAll, FunSpec }
import scala.concurrent.duration._
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import retry.Defaults.timer
import scala.util.control.NonFatal
import java.net.InetSocketAddress

class ConnectionIssuesSpec extends FunSpec with ZkServer {
  describe("ConnectionIssues") {
    it ("should reconnect") {
      val addr = new InetSocketAddress(2181)
      val cli = ZkClient(s"${addr.getHostName}:${addr.getPort}", Some(2.seconds)).retried()
      println("applying future")
      val future = cli()
      future.onFailure {
        case NonFatal(e) => println(s"failing $e")
      }
      Await.ready(future, Duration.Inf)
    }
  }
}
