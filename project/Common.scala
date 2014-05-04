object Common {
  import sbt._, sbt.Keys._
  def module(mod: String) =
    Project(mod, file(mod), 
            settings = Defaults.defaultSettings ++ Seq(
              organization := "me.lessis",
              name := s"zoey-$mod",
              version := "0.1.0-SNAPSHOT",
              crossScalaVersions := Seq("2.10.4", "2.11.0"),
              scalaVersion := crossScalaVersions.value.head,
              scalacOptions ++= Seq(Opts.compile.deprecation),
              licenses := Seq(("MIT",  url("https://github.com/softprops/zoey/blob/%s/LICENSE"
                                           .format(version.value)))),
              publishArtifact in Test := false,
              pomExtra := (
                <scm>
                <url>git@github.com:softprops/zoey.git</url>
                <connection>scm:git:git@github.com:softprops/zoey.git</connection>
                </scm>
                <developers>
                <developer>
                <id>softprops</id>
                <name>Doug Tangren</name>
                <url>https://github.com/softprops</url>
                </developer>
                </developers>)) ++
            bintray.Plugin.bintraySettings)
}
