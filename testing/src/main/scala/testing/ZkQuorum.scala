package zoey.testing

import org.apache.zookeeper.server.{ ServerCnxnFactory, ZooKeeperServer }
import org.apache.zookeeper.server.quorum.{
  QuorumPeer, QuorumPeerConfig, QuorumPeerMain
}
import java.net.InetSocketAddress
import java.nio.channels.ServerSocketChannel
import java.io.{ BufferedWriter, File, FileWriter }
import java.util.{ Properties, UUID }
import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.NonFatal

/** provides access to in memory zk server quorum on the fly */
trait ZkQuorum {
  
  case class Builder(
    _id: Long = 1,
    _clientAddress: Option[InetSocketAddress] = None,
    _dataDir: Option[File] = None,
    _dataLogDir: Option[File] = None,
    _localSessionsEnabled: Option[Boolean] = None,
    _localSessionsUpgradingEnabled: Option[Boolean] = None,
    _tickTime: Option[Int] = None,
    _maxClientCnxns: Option[Int] = None,
    _minSessionTimeout: Option[Int] = None,
    _maxSessionTimeout: Option[Int] = None,
    _initLimit: Option[Int] = None,
    _syncLimit: Option[Int] = None,
    _electionAlg: Option[Int] = None,
    _quorumListenOnAllIPs: Option[Boolean] = None,
    _syncEnabled: Option[Boolean] = None,
    _dynamicConfigFile: Option[File] = None,
    _standaloneEnabled: Option[Boolean] = None,
    // todo: support observer/particpant labels
    _servers: Map[Long, (InetSocketAddress, Int)] =
      Map.empty[Long, (InetSocketAddress, Int)]) {
  
    def id(idx: Long) = copy(_id = idx)
    def servers(svrs: (Long, (InetSocketAddress, Int))*) =
      copy(_servers = svrs.toMap)
    def clientAddr(addr: InetSocketAddress) = copy(_clientAddress = Some(addr))
    def dataDir(dir: File) = copy(_dataDir = Some(dir))
    def dataLogDir(dir: File) = copy(_dataLogDir = Some(dir))
    def localSessionsEnabled(enabled: Boolean) =
      copy(_localSessionsEnabled = Some(enabled))
    def localSessionsUpgradingEnabled(enabled: Boolean) =
      copy(_localSessionsUpgradingEnabled = Some(enabled))
    def tickTime(time: Int) = copy(_tickTime = Some(time))
    def maxConnections(max: Int) = copy(_maxClientCnxns = Some(max))
    def initLimit(limit: Int) = copy(_initLimit = Some(limit))
    def syncLimit(limit: Int) = copy(_syncLimit = Some(limit))
    def toMap: Map[String, String] =
      (Map.empty[String, String] ++
       _dataDir.map(("dataDir" -> _.getAbsolutePath)) ++
       _dataLogDir.map(("dataLogDir" -> _.getAbsolutePath)) ++
       _clientAddress.map(("clientPort" -> _.getPort.toString)) ++
       _localSessionsEnabled.map(("localSessionsEnabled" -> _.toString)) ++
       _localSessionsUpgradingEnabled.map(("localSessionsUpgradingEnabled" -> _.toString)) ++
       _clientAddress.map(("clientPortAddress" -> _.getAddress.getHostAddress)) ++
       _tickTime.map(("tickTime" -> _.toString)) ++
       _maxClientCnxns.map(("maxClientCnxns" -> _.toString)) ++
       _minSessionTimeout.map(("minSessionTimeout" -> _.toString)) ++
       _maxSessionTimeout.map(("maxSessionTimeout" -> _.toString)) ++
       _initLimit.map(("initLimit" -> _.toString)) ++
       _syncLimit.map(("syncLimit" -> _.toString)) ++
       _electionAlg.map(("electionAlg" -> _.toString)) ++
       _quorumListenOnAllIPs.map(("quorumListenOnAllIPs" -> _.toString)) ++
       _syncEnabled.map(("syncEnabled" -> _.toString)) ++
       _dynamicConfigFile.map(("dynamicConfigFile" -> _.getAbsolutePath)) ++
       _standaloneEnabled.map(("standaloneEnabled" -> _.toString)) ++
       _servers.map({
         case (id, (addr, electPort)) =>
           (s"server.$id",  addr.getAddress.getHostAddress :: addr.getPort :: electPort :: Nil mkString(":"))
       }).toMap)

    def build: QuorumPeerConfig = new QuorumPeerConfig() {
      _dataDir.foreach { base =>
        val myid = new File(base, "myid")
        if (myid.createNewFile()) {
          val writer = new BufferedWriter(new FileWriter(myid))
          writer.write(_id.toString)
          writer.flush()
          writer.close()
        }
      }

      parseProperties(new Properties {
        toMap.foreach {
          case (k, v) => setProperty(k, v)
        }
      })
    }
  }

  def defaultBuilder =  {
    val path = new File(
      System.getProperty("java.io.tmpdir"), "zk-" + UUID.randomUUID())
    path.mkdir()
    path.deleteOnExit()
    Builder()
      .clientAddr(new InetSocketAddress(Port.random))
      .tickTime(ZooKeeperServer.DEFAULT_TICK_TIME)
      .maxConnections(100)
      .dataDir(path)
      .dataLogDir(path)
      .initLimit(10)
      .syncLimit(5)
  }

  trait Quorum {
    def shutdown(): Unit
    def clientAddr: String
  }

  class QuorumMain extends QuorumPeerMain {
    def shutdown() =
      Option(quorumPeer).foreach { peer =>
        val cnxnFactField = classOf[QuorumPeer].getDeclaredField("cnxnFactory")
        cnxnFactField.setAccessible(true)
        val cnxnFactory = cnxnFactField.get(quorumPeer).asInstanceOf[ServerCnxnFactory]
        cnxnFactory.closeAll()
        val ssField = cnxnFactory.getClass().getDeclaredField("ss")
        ssField.setAccessible(true)
        ssField.get(cnxnFactory).asInstanceOf[ServerSocketChannel].close()
        peer.shutdown()
      }

    def awaitStartup: Unit =
      Option(quorumPeer) match {
        case None =>
          Thread.sleep(100)
          awaitStartup
        case Some(_) =>
          println(s"server $quorumPeer started")
      }
  }

  def quorum(configure: Builder => Builder = identity): Quorum = {
    val builder = configure(defaultBuilder)
    val configs = builder._servers.map {
      case (id, _) =>
        (id, configure(defaultBuilder).id(id).build)
    }
    
    new Quorum {
      val mains = configs.map {
        case (id, _) => (id, new QuorumMain)
      }.toMap
      val runs = Future.sequence(configs.map {
        case (id, cfg) =>
          Future {
            mains(id).runFromConfig(cfg)
          }
      })
      mains.foreach { case (_, main) => main.awaitStartup }
      runs.onFailure {
        case NonFatal(e) =>
          System.err.println("failed to start server $e")
      }
      def shutdown() = mains.foreach { case (_, main) => main.shutdown() }
      def clientAddr: String = configs.map {
        case (_, cfg) =>
          s"${cfg.getClientPortAddress.getAddress.getHostAddress}:${cfg.getClientPortAddress.getPort}"
      }.mkString(",")
    }
  }
}
