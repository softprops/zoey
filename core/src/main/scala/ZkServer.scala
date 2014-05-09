package zoey

import org.apache.zookeeper.client.FourLetterWordMain.send4LetterWord
import scala.concurrent.{ ExecutionContext, Future }

/** An non-blocking interface for all of the 4-letter-words zk servers respond to
 *  http://zookeeper.apache.org/doc/r3.4.6/zookeeperAdmin.html#sc_zkCommands */
case class ZkServer(
  host: String, port: Int)(
  implicit ec: ExecutionContext) {
  def conf = send("conf")
  def cons = send("cons")
  def crst = send("crst")
  def dump = send("dump")
  def envi = send("envi")
  def ruok = send("ruok")
  def srst = send("srst")
  def srvr = send("srvr")
  def stat = send("stat")
  def wchs = send("wchs")
  def wchc = send("wchc")
  def wchp = send("wchp")
  def mntr = send("mntr")
  def send(cmd: String): Future[String] =
    Future(send4LetterWord(host, port, cmd))
}
