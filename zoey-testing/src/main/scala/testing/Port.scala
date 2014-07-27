package zoey.testing

object Port {
  def random() = {
    val s = new java.net.ServerSocket(0)
    val available = s.getLocalPort
    s.close()
    available
  }
}
