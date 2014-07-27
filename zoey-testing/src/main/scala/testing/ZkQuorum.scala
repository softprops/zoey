package zoey.testing

import org.apache.zookeeper.server.quorum.QuorumStats.Provider
import org.apache.zookeeper.server.{ ServerCnxnFactory, ZooKeeperServer }
import org.apache.zookeeper.server.quorum.{
  QuorumPeer, QuorumPeerConfig, QuorumPeerMain
}
import java.io.{ Closeable, BufferedWriter, File, FileWriter }
import java.net.InetSocketAddress
import java.nio.channels.ServerSocketChannel
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
    _clientPort: Option[Int] = None,
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
    def clientPort(port: Int) = copy(_clientPort = Some(port))
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
    def electionAlg(alg: Int) = copy(_electionAlg = Some(alg))
    def toMap: Map[String, String] =
      (Map.empty[String, String] ++
       _dataDir.map(("dataDir" -> _.getCanonicalPath)) ++
       _dataLogDir.map(("dataLogDir" -> _.getCanonicalPath)) ++
       _clientAddress.map(_.getPort).orElse(_clientPort).map(("clientPort" -> _.toString)) ++
       _localSessionsEnabled.map(("localSessionsEnabled" -> _.toString)) ++
       _localSessionsUpgradingEnabled.map(("localSessionsUpgradingEnabled" -> _.toString)) ++
       _clientAddress.map(("clientPortAddress" -> _.getHostName)) ++
       _tickTime.map(("tickTime" -> _.toString)) ++
       _maxClientCnxns.map(("maxClientCnxns" -> _.toString)) ++
       _minSessionTimeout.map(("minSessionTimeout" -> _.toString)) ++
       _maxSessionTimeout.map(("maxSessionTimeout" -> _.toString)) ++
       _initLimit.map(("initLimit" -> _.toString)) ++
       _syncLimit.map(("syncLimit" -> _.toString)) ++
       _electionAlg.map(("electionAlg" -> _.toString)) ++
       _quorumListenOnAllIPs.map(("quorumListenOnAllIPs" -> _.toString)) ++
       _syncEnabled.map(("syncEnabled" -> _.toString)) ++
       _dynamicConfigFile.map(("dynamicConfigFile" -> _.getCanonicalPath)) ++
       _standaloneEnabled.map(("standaloneEnabled" -> _.toString)) ++
       _servers.map({
         case (id, (addr, electPort)) =>
           (s"server.$id",  addr.getHostName :: addr.getPort :: electPort :: Nil mkString(":"))
       }).toMap)

    def build: QuorumPeerConfig = new QuorumPeerConfig() {
      _dataDir.foreach { base =>
        val myid = new File(base, "myid")
        try {
          if (myid.createNewFile()) {
            val writer = new BufferedWriter(new FileWriter(myid))
            writer.write(_id.toString)
            writer.flush()
            writer.close()
          } else sys.error(s"could not create file $myid")
        } catch {
          case e: Throwable =>
            e.printStackTrace
            throw e
        }
      }
      parseProperties(new Properties {
        toMap.foreach { case (k,v) => setProperty(k, v) }
      })
    }
  }

  def defaultBuilder =  {
    val path = Files.randomTemp
    Builder()
      .clientPort(Port.random)
      .tickTime(ZooKeeperServer.DEFAULT_TICK_TIME)
      .maxConnections(100)
      .dataDir(path)
      .initLimit(5)
      .syncLimit(2)
  }

  trait Quorum extends Closeable {
    def connectStr: String
    def kill(instance: Int): Unit
    def killLeader(): Unit
    def closeLeader(): Unit
    def foreach(f: Peer => Unit): Unit
  }

  case class Peer(val id: Long)
    extends QuorumPeerMain with Closeable {

    override def toString =
      s"Peer(server: $id, leader: $leader)"

    def leader: Boolean =
      Option(quorumPeer).filter(
        Provider.LEADING_STATE == _.getServerState).isDefined

    def kill() {
      Option(quorumPeer).foreach { peer =>
        val cnxnFactField = classOf[QuorumPeer].getDeclaredField("cnxnFactory")
        cnxnFactField.setAccessible(true)
        val cnxnFactory = cnxnFactField.get(quorumPeer).asInstanceOf[ServerCnxnFactory]
        cnxnFactory.closeAll()
        val ssField = cnxnFactory.getClass().getDeclaredField("ss")
        ssField.setAccessible(true)
        ssField.get(cnxnFactory).asInstanceOf[ServerSocketChannel].close()
        close()
      }
    }

    def close() {
      Option(quorumPeer).foreach(_.shutdown())
    }

    def awaitStartup: Unit =
      Option(quorumPeer) match {
        case None =>
          Thread.sleep(100)
          awaitStartup
        case _ =>
      }
  }

  def quorum(
    of: Int,
    configure: Builder => Builder = identity): Quorum = {
    val servers = (1 to of).map { id =>
      (id.toLong,
       (new InetSocketAddress("localhost", Port.random), // quorum address
        Port.random))                                    // election port
    }
    val configs = servers.map {
      case (id, _) =>
        (id, configure(defaultBuilder).id(id).servers(servers:_*).build)
    }
    
    new Quorum {
      val mains = configs.map { case (id, _) => (id, Peer(id)) }.toMap
      configs.foreach {
        case (id, cfg) =>
          // spawn new thread to ensure each instance is
          // not started in the same thread
          new Thread(new Runnable {
            def run {
              try mains(id).runFromConfig(cfg) catch {
                case NonFatal(e) =>
                  e.printStackTrace
              }
            }
          }).start()
          mains(id).awaitStartup
      }

      def close() = foreach(_.close())

      def foreach(f: Peer => Unit) =
        mains.values.foreach(f)

      def closeLeader() =
        mains.values.filter(_.leader).foreach(_.close())

      def killLeader() =
        mains.values.filter(_.leader).foreach(_.kill())

      def kill(instance: Int) =
        mains.get(instance).foreach(_.kill())

      def connectStr: String = configs.map {
        case (_, cfg) => s"${cfg.getClientPortAddress.getHostName}:${cfg.getClientPortAddress.getPort}"
      }.mkString(",")

      override def toString = mains.values.mkString(", ")
    }
  }
}
