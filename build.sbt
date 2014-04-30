organization := "me.lessis"

name := "zoey"

version := "0.1.0-SNAPSHOT"

libraryDependencies += ("org.apache.zookeeper" % "zookeeper" % "3.4.5").exclude("javax.jms", "jms").exclude("com.sun.jmx", "jmxri").exclude("com.sun.jdmk", "jmxtools")

description := "an asyncronous interface for zookeeper"

crossScalaVersions := Seq("2.10.4", "2.11.0")

scalaVersion := crossScalaVersions.value.head

initialCommands := "import scala.concurrent.ExecutionContext.Implicits.global, zoey._; val c = ZkClient()"
