package zoey.testing

import java.io.File
import org.apache.zookeeper.server.{
  NIOServerCnxnFactory, ServerCnxnFactory, ZooKeeperServer
}
import java.util.UUID
import java.net.InetSocketAddress
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/** provides access to in memory zk servers on the fly */
trait ZkServer {

  trait Server {
    /** @return true, if server is currently running */
    def isRunning: Boolean
    /** shuts the a server down */
    def shutdown(): Unit
    /** address a zk client can resolve server from */
    def clientAddr: String
    /** persists state */
    def save(): Unit
  }

  def server(
    host: InetSocketAddress = new InetSocketAddress(Port.random),
    maxConnections: Int = 100,
    tickTime: Int = ZooKeeperServer.DEFAULT_TICK_TIME): Server = {
    val path = new File(System.getProperty("java.io.tmpdir"), "zk-" + UUID.randomUUID())
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
