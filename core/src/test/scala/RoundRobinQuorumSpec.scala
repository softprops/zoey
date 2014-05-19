package zoey

import org.scalatest.{ BeforeAndAfterAll, FunSpec }
import scala.concurrent.duration._
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import retry.Defaults.timer
import scala.util.control.NonFatal
import java.net.InetSocketAddress

class RoundRobinQuorumSpec extends FunSpec
  with BeforeAndAfterAll with testing.ZkQuorum {

  val addrs = (2181 to 2183).map(new InetSocketAddress(_))
  lazy val cluster = quorum(_.servers(addrs.zipWithIndex.map {
    case (addr, id) => (id.toLong, (addr, testing.Port.random))
  }:_*))

  describe("RoundRobinQuorum") {
    it ("should retry failed operations on each server provided") {
      cluster // trigger cluster startup
      println(s"cluster clientAddr ${cluster.clientAddr}")
      val cli = ZkClient.roundRobin(
        cluster.clientAddr.split(","), Some(2.seconds)).retried(max = 3)
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
    cluster.shutdown()
  }
}
