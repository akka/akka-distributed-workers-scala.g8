# The Backend

The heart of the solution is the `Master` actor that manages outstanding work
and notifies registered workers when new work is available.

![Managed Singleton](images/singleton-manager.png)

The [Cluster Singleton](http://doc.akka.io/docs/akka/current/scala/guide/modules.html#cluster-singleton) tool in 
Akka makes sure it only runs on one node within the subset of nodes
marked with the role `backend` at any given time. It will run on the oldest such node, 
if that node is removed from the cluster the singleton will be started on the new oldest node. 

The tool also allows for interacting with the singleton from any node in the cluster
without explicitly knowing where it runs, this is done through the `ClusterSingletonProxy`.

You can see how the master singleton is started in the method `startBackend`
in <a href="#code/src/main/scala/worker/Main.scala" class="shortcut">Main.scala</a>

Scala
: @@@

@@@


In case of failure of the master node another master actor is automatically started on
a standby node. The master on the standby node takes over the responsibility for
outstanding work. Work in progress can continue and will be reported to the new master.
The state of the master can be re-created on the standby node using event sourcing.
An alternative to event sourcing and the singleton master would be to keep track of all
jobs in a central database, but that is more complicated and not as scalable. In the end
of the tutorial we will describe how multiple masters can be supported with a small adjustment.

The master actor is made available for workers by registering itself
in the <a href="http://doc.akka.io/docs/akka/2.4.0/scala/cluster-client.html"
target="_blank">ClusterReceptionist</a>.

The frontend actor talks to the master actor via the
in the <a href="http://doc.akka.io/docs/akka/2.4.0/scala/distributed-pub-sub.html"
target="_blank">ClusterSingletonProxy</a>.

Later we will explore the implementation of the <a href="#code/src/main/scala/worker/Master.scala" class="shortcut">Master</a>
actor in depth, but first we will take a look at the frontend and worker that interacts with the master.
