package zoey

trait Paths {

  def path: String

  lazy val name: String =
    path.lastIndexOf('/') match {
      case i if i == -1 || i == path.length - 1 => ""
      case i => path.substring(i + 1)
    }

  lazy val parentPath: String =
    path.lastIndexOf('/') match {
      case i if i <= 0 => "/"
      case i => path.substring(0, i)
    }

  def childPath(child: String): String =
    if (isRoot) {
      if (child.startsWith("/")) child
      else path + child
    } else {
      if (child.startsWith("/")) path + child
      else path + "/" + child
    }

  def isRoot = "/" == path
}
