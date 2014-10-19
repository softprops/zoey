package zoey

import org.scalatest.{ BeforeAndAfterAll, FunSpec }
import scala.concurrent.duration._
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.NonFatal

class RoundRobinQuorumSpec extends FunSpec
  with BeforeAndAfterAll with testing.ZkQuorum {

  lazy val cluster = quorum(3)
  lazy val cli = ZkClient.roundRobin(
    cluster.connectStr.split(","), Some(2.seconds))
    .retryWith(retry.Directly(3))

  describe("RoundRobinQuorum") {
    it ("should retry failed operations on each server provided") {
      println(s"cluster connectStr ${cluster.connectStr}")
      val f = cli()      
      f.onFailure {
        case e: Throwable => println(s"failing $e")
      }
      f.onSuccess {
        case e => println(s"round robin success $e")
      }
      println(s"awaiting $f")
      Await.ready(f, Duration.Inf)
      println("done")
    }
  }

  override def afterAll() {
    println("shutting down cluster")
    cli.close()
    cluster.close()
  }
}
