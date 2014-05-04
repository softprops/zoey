package zoey

trait Paths {
  def path: String

  lazy val name: String = path.lastIndexOf('/') match {
    case i if (i == -1 || i == path.length - 1) => ""
    case i => path.substring(i + 1)
  }

  lazy val parentPath: String = path.lastIndexOf('/') match {
    case i if i <= 0 => "/"
    case i => path.substring(0, i)
  }

  def childPath(child: String): String = path match {
    case path if !path.endsWith("/") => path + "/" + child
    case path => path + child
  }
}
