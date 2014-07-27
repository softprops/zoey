libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "2.2.0" % "test",
  "me.lessis" %% "retry" % "0.2.0")

description := "an asyncronous interface for zookeeper"

initialCommands := "import scala.concurrent.ExecutionContext.Implicits.global, zoey._; val c = ZkClient()"

testOptions in Test += Tests.Setup(
  () => System.setProperty("zookeeper.jmx.log4j.disable", "true"))
