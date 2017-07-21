# Piecing it All Together

Now when we know more about the Worker and Frontend that interacts with the Master it
is time to take a closer look at
<a href="#code/src/main/scala/worker/Master.scala" class="shortcut">Master.scala</a>.

Workers register itself to the master with `RegisterWorker`. Each worker
has an unique identifier and the master keeps track of the workers, including current
`ActorRef` (sender of `RegisterWorker` message) that can be used
for sending notifications to the worker. This `ActorRef` is not a direct
link to the worker actor, but messages sent to it will be delivered to the worker.
When using the cluster client messages are are tunneled via the receptionist on some
node in the cluster to avoid inbound connections from other cluster nodes to the client.

When the master receives `Work` from frontend it adds the work item to
the queue of pending work and notifies idle workers with `WorkIsReady` message.

To be able to restore same state in case of fail over to a standby master actor the
changes (domain events) are stored in an append only transaction log and can be replayed
when standby actor is started.
<a href="http://doc.akka.io/docs/akka/2.4.0/scala/persistence.html" target="_blank">Akka Persistence</a>
is used for that. <a href="#code/src/main/scala/worker/Master.scala" class="shortcut">Master</a> extends
`PersistentActor` and events are stored in with the calls to the `persist`
method. When the domain event has been saved successfully the master replies
with an acknowledgement message (`Ack`) to the frontend.
The master also keeps track of accepted work identifiers to be able to discard duplicates
sent from the frontend.

![Frontend to Master Message Flow](images/frontend-master-message-flow.png)

When a worker receives `WorkIsReady` it sends back `WorkerRequestsWork`
to the master, which hands out the work, if any, to the worker. The master keeps track of that
the worker is busy and expect a result within a deadline. For long running jobs the worker
could send progress messages, but that is not implemented in the example.

![Master to Worker Message Flow](images/master-worker-message-flow.png)

When the worker sends `WorkIsDone` the master updates its state of the worker
and sends acknowledgement back to the worker. This message must also be idempotent as the worker will
re-send if it doesn't receive the acknowledgement.

![When Work is Done](images/master-worker-message-flow-2.png)

## Summary

The <a href="#code/src/main/scala/worker/Master.scala" class="shortcut">Master</a> actor
is a <a href="http://doc.akka.io/docs/akka/2.4.0/scala/cluster-singleton.html"
target="_blank">Cluster Singleton</a> and register itself in the 
<a href="http://doc.akka.io/docs/akka/2.4.0/scala/cluster-client.html" target="_blank">Cluster Receptionist</a>.
The `Master` is using 
<a href="http://doc.akka.io/docs/akka/2.4.0/scala/persistence.html" target="_blank">Akka Persistence</a>
to store incoming jobs and state.

The <a href="#code/src/main/scala/worker/Frontend.scala" class="shortcut">Frontend</a> actor send work
to the master via the `ClusterSingletonProxy`.

The <a href="#code/src/main/scala/worker/Worker.scala" class="shortcut">Worker</a> communicate with the
cluster and its master with the <a href="http://doc.akka.io/docs/akka/2.4.0/scala/cluster-client.html"
target="_blank">Cluster Client</a>.

![Cluster Nodes](images/cluster-nodes.png)
