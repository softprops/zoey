import sbt._, sbt.Keys._

object Common {
  def module(mod: String) =
    Project(mod, file(mod), 
            settings = Defaults.defaultSettings ++ Seq(
              name := s"zoey-$mod"))
}
