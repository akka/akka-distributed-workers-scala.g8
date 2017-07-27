# The Back-End Nodes

The back-end nodes hosts a single actor which is the heart of the solution: the `Master` actor which manages outstanding work, keeps track of available workers, and notifies registered workers when new work is available.

## The Master singleton

The [Cluster Singleton](http://doc.akka.io/docs/akka/current/scala/guide/modules.html#cluster-singleton) tool in Akka makes sure an actor only runs concurrently on one node within the subset of nodes marked with the role `back-end` at any given time. It will run on the oldest such node, if that node is removed from the cluster the singleton will be started on the new oldest node. 

![Managed Singleton](images/singleton-manager.png)

The tool also allows for interacting with the singleton from any node in the cluster without explicitly knowing where it runs, this is done through the `ClusterSingletonProxy`. You can see this in action in the `FrontEnd` and `Worker` actors.

You can see how the master singleton is started in the method `startSingleton`
in `MasterSingleton`:

@@snip [MasterSingleton.scala]($g8src$/scala/worker/MasterSingleton.scala) { #singleton }

The singleton accepts the `Prop`s of the actual singleton actor, as well as configuration which allows us to decide that the singleton actors should only run on the nodes with the role `back-end`.

The proxy is similarly configured, with the role where the singleton will be running, and also a path where the singleton manager actor can be found:

@@snip [MasterSingleton.scala]($g8src$/scala/worker/MasterSingleton.scala) { #proxy }

In case of the master node crashing and being removed from the cluster another master actor is automatically started on a standby node. The master on the standby node takes over the responsibility for outstanding work. Work in progress can continue and will be reported to the new master. 

The state of the master is recovered on the standby node in the case of the node being lost through event sourcing. 

An alternative to event sourcing and the singleton master would be to keep track of all jobs in a central database, but that is more complicated and not as scalable. In the end of the tutorial we will describe how multiple masters can be supported with a small adjustment.

Let's now explore the implementation of the `Master` actor in depth.
