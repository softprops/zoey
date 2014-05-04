libraryDependencies ++= Seq(
  (if (scalaVersion.value.startsWith("2.9.3")) "org.scalatest" %% "scalatest" % "1.9.2" else "org.scalatest" %% "scalatest" % "2.1.3") % "test",
  ("org.apache.zookeeper" % "zookeeper" % "3.4.6").exclude("javax.jms", "jms").exclude("com.sun.jmx", "jmxri").exclude("com.sun.jdmk", "jmxtools"),
  "me.lessis" %% "retry-core" % "0.1.0")

description := "an asyncronous interface for zookeeper"

licenses := Seq(("MIT",  url("https://github.com/softprops/%s/blob/%s/LICENSE"
                                           .format(name.value, version.value))))

initialCommands := "import scala.concurrent.ExecutionContext.Implicits.global, zoey._; val c = ZkClient()"
