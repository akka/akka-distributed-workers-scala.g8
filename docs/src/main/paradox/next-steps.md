# Next Steps

## Using a Good Serializer

TODO don't use Java serializer etc etc

## Using an Actual Database 

TODO link to cassandra?

## A HTTP Based API

TODO link to Akka HTTP (unless we should even include an actual API)

## Many Masters

If the singleton master becomes a bottleneck we could start several master actors and
shard the jobs among them. For each shard of master/standby nodes we would use a separate
cluster role name, e.g. "backend-shard1", "backend-shard2".

## More Tools for Building Distributed Systems

In this example we have used
[Cluster Singleton](http://doc.akka.io/docs/akka/current/scala/cluster-singleton.html#cluster-singleton)
and
[Distributed Publish Subscribe](http://doc.akka.io/docs/akka/current/scala/distributed-pub-sub.html)
 but those are not the only tools in Akka Cluster. 
 
 You can also find a good overview of the various modules that make up Akka in 
 [this section of the official documentation](http://doc.akka.io/docs/akka/current/scala/guide/modules.html#cluster-singleton)