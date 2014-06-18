package zoey

import org.scalatest.{ BeforeAndAfterAll, FunSpec }
import scala.concurrent.duration.Duration
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Failure
class ZkAuthClientSpec extends FunSpec with BeforeAndAfterAll
  with testing.ZkServer {

  lazy val svr = server(auth = Some(("foo", "bar")))
  lazy val authed = ZkClient(
    svr.connectStr, authInfo = Some(AuthInfo.digest("foo", "bar")))
  lazy val anon = ZkClient(svr.connectStr)

  describe("Authenticted ZkClient") {
    it ("should require auth") {
      val future = for {
        foo        <- authed("/foo").create("data".getBytes)
        authedData <- foo.getData()
        anonData   <- anon("/foo").getData()
      } yield (authedData, anonData)
      Await.ready(future, Duration.Inf)
      future.onComplete {
        case Failure(KeeperAuthException(fail)) =>
          assert(fail.getPath === "/foo")
        case _ => fail("expected StatEvent.AuthFailed")
      }
    }
  }

  override def afterAll() {
    authed.close()
    anon.close()
    svr.close()
  }
}
