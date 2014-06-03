package zoey.testing

import org.apache.zookeeper.server.{
  NIOServerCnxnFactory, ServerCnxnFactory, ZooKeeperServer
}
import java.io.Closeable
import java.util.UUID
import java.net.InetSocketAddress
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/** provides access to in memory standalone zk servers on the fly */
trait ZkServer {

  trait Server extends Closeable {
    /** @return true, if server is currently running */
    def isRunning: Boolean
    /** address a zk client can resolve server from */
    def connectStr: String
    /** persists state */
    def save(): Unit
  }

  def server(
    host: InetSocketAddress = new InetSocketAddress(Port.random),
    maxConnections: Int = 100,
    tickTime: Int = ZooKeeperServer.DEFAULT_TICK_TIME): Server = {
    val path = Files.randomTemp
    val server = new ZooKeeperServer(path, path, tickTime)
    val fact = ServerCnxnFactory.createFactory()
    fact.configure(host, maxConnections)
    fact.startup(server)
    new Server {
      def isRunning = server.isRunning
      def connectStr = s"${host.getHostName}:${server.getClientPort}"
      def save() = server.takeSnapshot()
      def close() = {
        if (!isRunning) {
          server.shutdown()
        }
        fact.shutdown()
      }
    }
  }  
}
