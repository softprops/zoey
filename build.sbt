organization in ThisBuild := "me.lessis"

version in ThisBuild := "0.1.0-SNAPSHOT"

crossScalaVersions in ThisBuild := Seq("2.10.4", "2.11.1")

scalaVersion in ThisBuild := crossScalaVersions.value.last

scalacOptions in ThisBuild ++= Seq(Opts.compile.deprecation)

resolvers in ThisBuild += "softprops-maven" at "http://dl.bintray.com/content/softprops/maven"

libraryDependencies in ThisBuild += 
  ("org.apache.zookeeper" % "zookeeper" % "3.4.6").exclude("javax.jms", "jms").exclude("com.sun.jmx", "jmxri").exclude("com.sun.jdmk", "jmxtools")

lazy val `zoey-core` = project.dependsOn(`zoey-testing` % "test->test;compile->compile")

lazy val `zoey-testing` = project
