package zoey

import org.scalatest.{ BeforeAndAfterAll, FunSpec }
import scala.concurrent.duration._
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.NonFatal

class RoundRobinQuorumElectSpec extends FunSpec
  with BeforeAndAfterAll with testing.ZkQuorum {

  lazy val cluster = quorum(3)
  lazy val cli = ZkClient.roundRobin(
    cluster.connectStr.split(","), Some(2.seconds)).retryTimes(max = 3)

  def awaitConnect = {
    val f = cli()      
    f.onFailure {
      case e: Throwable => println(s"failing $e")
    }
    f.onSuccess {
      case e => println(s"round robin success $e")
    }
    println(s"awaiting $f")
    Await.ready(f, Duration.Inf)
    println("connected")
  }

  describe("RoundRobinQuorum") {
    it ("should handle leader re-relects with grace") {
      println(s"zkclient is $cli")
      println(s"cluster $cluster")
      awaitConnect
      println(s"cluster $cluster")
      println("\n\n\nkilling leader\n\n\n")
      cluster.closeLeader()
      awaitConnect
      println(s"cluster $cluster")
    }
  }

  override def afterAll() {
    println("\n\n\n\nshutting down cluster\n\n\n")
    cli.close()
    cluster.close()
  }
}
