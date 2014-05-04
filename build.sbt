organization := "me.lessis"

version := "0.1.0-SNAPSHOT"

lazy val root = project.in(file("."))
  .settings(
    publish := {}, test := {}, publishLocal := {}
  )
  .aggregate(core, testing)

lazy val core = Common.module("core")
  .dependsOn(testing % "test->test;compile->compile")

lazy val testing = Common.module("testing")
  .settings(publish := {}, test := {})
