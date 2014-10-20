licenses in ThisBuild := Seq(("MIT", url(s"https://github.com/softprops/zoey/blob/${version.value}/LICENSE")))

publishArtifact in Test in ThisBuild := false

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

bintraySettings

bintray.Keys.packageLabels in bintray.Keys.bintray := Seq("zookeeper", "distributed-systems")

lsSettings

LsKeys.tags in LsKeys.lsync := (bintray.Keys.packageLabels in bintray.Keys.bintray).value

externalResolvers in LsKeys.lsync := (resolvers in bintray.Keys.bintray).value

