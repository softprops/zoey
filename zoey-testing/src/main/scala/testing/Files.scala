package zoey.testing

import java.util.Random
import java.lang.{ Integer => JInt }
import java.io.File

private [testing] object Files {
  private lazy val random = new Random
  /** creates a new random temporary dir */
  def randomTemp = {
    val f = new File(
      System.getProperty("java.io.tmpdir"),
      "zk-" + JInt.toHexString(random.nextInt))
    f.mkdir()
    Runtime.getRuntime.addShutdownHook(new Thread {
      override def run = if (f.exists) {
        def delete(file: File): Unit =
          file match {
            case dir if (dir.isDirectory) =>
              dir.listFiles.foreach(delete)
              dir.delete()
            case plain =>
              plain.delete()
          }
        delete(f)
      }
    })
    f
  }
}
