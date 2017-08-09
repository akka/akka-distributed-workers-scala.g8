# Next Steps

The following are some ideas where to take this sample next. Implementations for each idea is left to the reader as good exercises.

## Using a different serializer

To simplify things in this sample the default Java Serializer was used but in a real application it should not be used. 

For passing messages across the network the Java Serialization has serious security implications (worst case ability to remotely execute code) and does not have good performance characteristics.

For storing the domain events in a persistent journal, there is also the problem with how to deal with versioning, which is hard if not impossible using Java Serialization.

A few options to look into are listed in the [Akka Docs section on Serialization](http://doc.akka.io/docs/akka/current/scala/serialization.html#external-akka-serializers)

## A HTTP Based API

The `FrontEnd` in this sample is a dummy that automatically generates work. A real application could for example use [Akka HTTP](http://doc.akka.io/docs/akka-http/current/scala/http/introduction.html) to provide a HTTP REST (or other) API for external clients.

## Scaling better with many masters

If the singleton master becomes a bottleneck we could start several master actors and shard the jobs among them. For each shard of master/standby nodes we would use a separate cluster role name, e.g. "back-end-shard1", "back-end-shard2". We would also need to ensure that the shard masters got unique `entityId`s.

## More tools for building distributed systems

In this example we have used
[Cluster Singleton](http://doc.akka.io/docs/akka/current/scala/cluster-singleton.html#cluster-singleton)
and
[Distributed Publish Subscribe](http://doc.akka.io/docs/akka/current/scala/distributed-pub-sub.html)
 but those are not the only tools in Akka Cluster. 
 
 You can also find a good overview of the various modules that make up Akka in 
 [this section of the official documentation](http://doc.akka.io/docs/akka/current/scala/guide/modules.html#cluster-singleton)