import sbt._
import sbt.Keys._
import bintray.Plugin.bintraySettings
import ls.Plugin.lsSettings

object Publishing {
  def settings = Seq(
    licenses in ThisBuild := Seq(("MIT", url(s"https://github.com/softprops/zoey/blob/${version.value}/LICENSE"))),
    publishArtifact in Test := false,
    pomExtra in ThisBuild := (
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
      </developers>)
  ) ++ bintraySettings ++ lsSettings ++ Seq(
    bintray.Keys.packageLabels in bintray.Keys.bintray := Seq("zookeeper", "distributed-systems"),
    ls.Plugin.LsKeys.tags in ls.Plugin.LsKeys.lsync := (bintray.Keys.packageLabels in bintray.Keys.bintray).value,
    externalResolvers in ls.Plugin.LsKeys.lsync := (resolvers in bintray.Keys.bintray).value
  )
}

