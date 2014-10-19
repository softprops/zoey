# zoey

[![Build Status](https://travis-ci.org/softprops/zoey.svg)](https://travis-ci.org/softprops/zoey)

a non-blocking interface for [apache zookeeper](http://zookeeper.apache.org/). Inspired by Twitter's wonderful zk-client, implemented in terms of scala's standard library features.

## goals

* be simple

The underlying concepts of zookeeper are simple. Interacting with the standard java driver is not.

* be robust

Zookeeper is a mature HA answer for many distributed systems questions. Despite that, networks are still unreliable. These zookeeper interfaces should be designed with that in mind.

* be asyncronous by default

The standard java zookeeper driver supports blocking operations with an optional support for callback style async operations. Scala's standard library Futures model latter very well providing a familiar interface that works well with existing scala libraries. 

## usage

### zoey core

Zoey core defines interfaces for creating a configurable and robust client to communicate with zookeeper servers.

#### Creating a client

Creating a new client with default options is simple.

```scala
val cli = zoey.ZkClient()
```

The default client will connect a zookeeper server listening on `0.0.0.0:2181`. If you've spun up a zookeeper locally
this may be fine, but if you've spun one up on another host just provide that connection string as an argument to your clients constructor.

```scala
val cli = zoey.ZkClient(connectString)
```

Many clients connecting to the same servers to perform operations can have an undesierable herding effect. Zookeeper servers are often distributed across a number of hosts for high availability. A _round robin_ interface is provided for creating a zookeeper client that will perform requests on a different host for each operation in the order the hosts are defined.

```scala
val cli = zoey.ZkClient.roundRobin(hostA :: hostB :: hostC :: Nil)
```

If a given server is unavailable, you don't want to wait on a connection to establish all day. In these cases you may with to set a connection timeout, specified as a [FiniteDuration](http://www.scala-lang.org/api/current/index.html#scala.concurrent.duration.FiniteDuration).

```scala
import scala.concurrent.duration._
val cli = zoey.ZkClient(connectString, connectTimeout = Some(3 seconds))
```

Once connected the server, the server will attempt to make sure its connection with you is healthy by establishing a session timeout for gaps in network connectivity. By default, this session timeout is set to 4 seconds. If you wish to change this, set the sessionTimeout when creating your client.

```scala
import scala.concurrent.duration._
val cli = zoey.ZkClient(connectStr, sessionTimeout = 10.seconds)
```

Once you've created a client, you may which to configure it's [mode](http://zookeeper.apache.org/doc/r3.4.6/api/org/apache/zookeeper/CreateMode.html) of operation. Typically this will be one of ephemeral or persistent and optionally sequential.
The client mode defines the semantics for how long of information written to zookeeper is stored. The default is persistent.

Zoey's client interfaces produce immutable instances but who share a client connection. Keep this in mind when storing references to client instances.

```scala
val ephemeralSeqCli = cli.emphermalSequential
```

Operations requested of a distributed network system are not guaranteed to be successful due to a number of potential factors. To make zoey
more robust in the face of these potential issues, zoey provides an interface for retrying failed operations, leveraging interfaces defined in the [retry](https://github.com/softprops/retry) library.

The following will attempt to retry operations up to 4 times with an [exponential backoff](https://github.com/softprops/retry#backoff) time starting at 1 second.

```scala
val retryingCli = cli.retryWith(
  retry.Backoff(max = 4, delay = 1.second))
```

### Getting data out

Zookeeper stores data with structures called ZNodes which are addressable by directory-like paths. To reference a ZNode, provide it's path
to the client.

```scala
val node = cli.node("/foo/bar")
```

or more simply...

```scala
val node = cli("/foo/bar")
```

The above `node` is a reference to a zookeepers ZNode's address. No information as been collected from or sent to the server yet. It is just a description for a future point of reference when invoking operations.

Zookeeper defines a basic set of operations for creating, updating and reading data from ZNodes.

Read operations are unique in that you can request information, and optionally, request to get notified when this information changes.

Zookeeper calls these notifications "watches"

For example, given the znode reference we created above, `node`, we can ask zookeeper if this ZNode exists _and_ to get notified when it that fact changes.

These watch notifications only fire once, and thus, are perfectly representated as scala Futures which also may only be satisfied once.

```scala
// the `exists` operation
val exists = node.exists

// calling apply() on a read operation produces a future which will be satisfied once information is retried once
val future = exists()
future.foreach {
  case exists => println(s"rec $exists")
}

// calling watch() on a read operation produces a structure that exposes of the result of the operation as a Try and a future 
// which will be satisfied when an update to this information occurs. Since futures may only be satisfied once, when
// and update occurs you will need to re-request a new watch for the read operation on the ZNode
val watch = exists.watch()
watch.foreach {
  case zoey.Watch(exists, updateFuture) =>
    println(s"exists $exists")
    updateFuture.foreach {
      case event => onPathChanged(event)
    }
}
```

Besides knowing that a given ZNode exists, you can ask for what data is contain with the `data` operation.

```scala
node.data().foreach {
  case data =>
    // data is a znode ref populated with the bytes stored at its path
    println(s"node stores ${data.bytes.size} bytes")
}
```

ZNodes differ from tranditional filesystem file descriptors in that they can act as both containers for data _and_ directories which have a list of child paths which are addresses to other ZNodes.

```scala
node.children().foreach {
  case chilren =>
    // children is a znode ref populated with the nodes stored at its path
    println(s"node has ${children.nodes.size} children")
}
```

### Getting data in

Now that you know how to get information out of zookeeper. Let's look at how you get it in.

ZNode path addresses work just like unix paths. You can't create path "/foo/bar" is path "/foo" doesn't exist. In the unix world you can request the `-p` (parent) flag to the `mkdir` command to ensure parent directories get created. With zoey, if you are storing data addressed at path "/foo/bar" and
don't know for sure that ZNode "/foo" exists you can set the `parent` parameter to true on create requests

```scala
node.create("test".getBytes, parent = true)
 .foreach {
   case created => println(s"created $created")
 }
```

What you can create, you can also update, but the current data a ZNode holds, as seen by you, may not be consistent with the data as seen
by the zookeeper server if there are other zookeeper clients interacting with the server. To accommodate this consistency conundrum zookeeper
attaches version information to ZNodes. You can read this version information by requesting the znode [Stat](http://zookeeper.apache.org/doc/r3.4.6/api/org/apache/zookeeper/data/Stat.html) with `znode.exists`.

```scala
node.set("updated".getBytes, version).foreach {
  case result => println(s"updated $result")
}
```

### Closing time

Since a zoey ZkClient maintains a persistent connection with a zookeeper server, you should instrument your application in a way
to gracefully terminate this connection. To do this. Simply call the `close` method defined on ZkClient which will return a Future that will be satisfied when the close operation is complete.

```scala
val closeFuture = zkClient.close()
closeFuture.foreach {
  case _ => println("the zoo is closed")
}
```

Close should only be called once.

Doug Tangren (softprops) 2013-2014
