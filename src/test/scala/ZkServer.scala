package zoey

import java.io.File
import org.apache.zookeeper.server.{
  NIOServerCnxnFactory, ZooKeeperServer
}
import java.util.UUID
import java.net.InetSocketAddress

/** provides access to an in memory zk server on the fly */
trait ZkServer {
  def tickTime = ZooKeeperServer.DEFAULT_TICK_TIME
  def maxConnections = 100
  def server(host: InetSocketAddress = new InetSocketAddress(0)) = {
    val path = File.createTempFile("zk-server-", null)
    val server = new ZooKeeperServer(path, path, tickTime)
    new NIOServerCnxnFactory {
     configure(host, maxConnections)
     startup(server)
    }
    server
  }  
}
