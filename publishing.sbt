licenses := Seq(("MIT", url(s"https://github.com/softprops/zoey/blob/${version.value}/LICENSE")))

publishArtifact in Test := false

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
