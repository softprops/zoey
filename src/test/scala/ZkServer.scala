package zoey

import java.io.File
import org.apache.zookeeper.server.{
  NIOServerCnxnFactory, ZooKeeperServer
}
import java.util.UUID
import java.net.InetSocketAddress

/** provides access to an in memory zk server on the fly */
trait ZkServer {
  def server(
    host: InetSocketAddress = new InetSocketAddress(0),
    maxConnections: Int = 100,
    tickTime: Int = ZooKeeperServer.DEFAULT_TICK_TIME) = {
    //val path = File.createTempFile("zk-server-", null)
    val path = new File(System getProperty "java.io.tmpdir", "zk-" + UUID.randomUUID())
    path.deleteOnExit()
    val server = new ZooKeeperServer(path, path, tickTime)
    new NIOServerCnxnFactory {
      configure(host, maxConnections)
      startup(server)
    }
    server
  }  
}
