package zoey

import org.apache.zookeeper.server.auth.DigestAuthenticationProvider

case class AuthInfo(scheme: String, data: Array[Byte])

/** see also http://zookeeper.apache.org/doc/r3.4.6/zookeeperProgrammers.html#sc_ZooKeeperPluggableAuthentication */
object AuthInfo {
  def digest(user: String, password: String): AuthInfo = {
    val data = DigestAuthenticationProvider.generateDigest(s"$user:$password").getBytes("utf8")
    // zkcontrib - why is getscheme note a static method
    AuthInfo(new DigestAuthenticationProvider().getScheme, data)
  }

  /** http://zookeeper.apache.org/doc/r3.4.6/zookeeperAdmin.html#sc_authOptions */
  def superDigest(password: String) = digest("super", password)
}
