organization := "me.lessis"

name := "zoey"

version := "0.1.0-SNAPSHOT"

libraryDependencies += ("org.apache.zookeeper" % "zookeeper" % "3.4.5").exclude("javax.jms", "jms").exclude("com.sun.jmx", "jmxri").exclude("com.sun.jdmk", "jmxtools")
