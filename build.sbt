organization in ThisBuild := "me.lessis"

version in ThisBuild := "0.1.0-SNAPSHOT"

crossScalaVersions in ThisBuild := Seq("2.10.4", "2.11.0")

scalaVersion in ThisBuild := crossScalaVersions.value.head

scalacOptions in ThisBuild ++= Seq(Opts.compile.deprecation)

lazy val core = Common.module("core")
  .dependsOn(testing % "test->test;compile->compile")

lazy val testing = Common.module("testing")
