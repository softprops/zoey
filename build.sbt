organization in ThisBuild := "me.lessis"

version in ThisBuild := "0.1.0-SNAPSHOT"

crossScalaVersions in ThisBuild := Seq("2.10.4", "2.11.0")

scalaVersion in ThisBuild := crossScalaVersions.value.head

scalacOptions in ThisBuild ++= Seq(Opts.compile.deprecation)

lazy val core = project.settings(name := s"zoey-${name.value}")
  .dependsOn(testing % "test->test;compile->compile")

lazy val testing = project.settings(name := s"zoey-${name.value}")
