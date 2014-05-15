description := "testing interfaces for zookeeper"

libraryDependencies +=
  ("org.apache.zookeeper" % "zookeeper" % "3.4.6").exclude("javax.jms", "jms").exclude("com.sun.jmx", "jmxri").exclude("com.sun.jdmk", "jmxtools")

publish := {}

publishLocal := {}
