package zoey

import java.io.File
import org.apache.zookeeper.server.{
  NIOServerCnxnFactory, ServerCnxnFactory, ZooKeeperServer
}
import java.util.UUID
import java.net.InetSocketAddress
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/** provides access to an in memory zk server on the fly */
trait ZkServer {

  trait Server {
    def isRunning: Boolean
    def shutdown(): Unit
    def clientAddr: String
    def save(): Unit
  }

  def server(
    host: InetSocketAddress = new InetSocketAddress(2181),
    maxConnections: Int = 100,
    tickTime: Int = ZooKeeperServer.DEFAULT_TICK_TIME): Server = {
    //val path = File.createTempFile("zk-server-", null)
    val path = new File(System getProperty "java.io.tmpdir", "zk-" + UUID.randomUUID())
    path.deleteOnExit()
    val server = new ZooKeeperServer(path, path, tickTime)
    val fact = ServerCnxnFactory.createFactory()
    fact.configure(host, maxConnections)
    fact.startup(server)
    new Server {
      def isRunning = server.isRunning
      def clientAddr = s"${host.getHostName}:${server.getClientPort}"
      def save() = server.takeSnapshot()
      def shutdown() = {
        if (!isRunning) {
          server.shutdown()
        }
        fact.shutdown()
      }
    }
  }  
}
