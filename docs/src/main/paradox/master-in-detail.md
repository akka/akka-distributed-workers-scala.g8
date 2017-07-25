# The Master Actor in Detail

Let's revisit the `Master` Actor since it without question is the most involved component in the guide.

In a real application you might want to try to separate concerns to a larger extent, but as this comes with a cost with regards to getting an overview we have decided to keep a few somewhat separate aspects in the same class.

## Modelling the set of current work items 

The `WorkState` keeps track of the current set of work that is pending, has been accepted by a worker, has completed etc. Every change to the `WorkState` is modelled as a domain event: 

@@snip [WorkState.scala]($g8src$/scala/worker/WorkState.scala) { #events }

This allows us to capture and store each such event that happens, we can later replaying all of them on an empty model and arrive at the exact same state. This is how event sourcing and [Akka Persistence](http://doc.akka.io/docs/akka/current/scala/persistence.html) allows us to start the actor (possibly on a different node) and reach the same state as a previous instance.

Inside of the actor this means that any time the `WorkState` is modified, we must first `persist` the event, and not until we know that was successful we can apply the event to the state. If it would be the other way around we could apply a change, have the write fail, and be in a state a replaying node could not reach.

Let's look at how a command to process a work item from the frontend comes in:

@@snip [Master.scala]($g8src$/scala/worker/Master.scala) { #persisting }

The first thing you might notice is the comment saying `idempotent`, this means that the same work message may arrive multiple times, but regardless how many times it comes in, it should only be executed once. This is needed since the `Frontend` actor re-sends work in case of the `Work` or `Ack` messages getting lost (Akka does not provide any guarantee of delivery, [see details in the docs](http://doc.akka.io/docs/akka/current/scala/general/message-delivery-reliability.html#discussion-why-no-guaranteed-delivery-)).

To make the logic idempotent we simple check if the work id is already known, and if it is we simply `Ack` it without further logic.

If the work is previously unknown, we start by transforming it into a `WorkAccepted` event, which we persist, and only in the `handler`-function passed to `persist` do we actually update the `workState`, send an `Ack` back to the `Frontend` and trigger a search for available workers.


## Akka Persistence differences from regular Actors

While in a "normal" Actor the only thing we have to do is to implement `receive`, which is then invoked for each incoming message. In a `PersistentActor` there are three things that needs to be implemented:

 1. `persistenceId` is a global identifier for the actor, we must make sure that there is never more than one Actor instance with the same `persistenceId` running globally, or else we would possibly mess up its journal.
 1. `receiveCommand` corresponds to the `receive` method of regular actors. Messages sent to the actor end up here. 
 1. `receiveRecover` is invoked with the recorded events of the actor when it starts up 

## Keeping track of workers

For the set of worker, saving them to a persistent store would be less valuable, as there is a high chance that the set of workers and worker nodes are not the same after a stop and start of the master. Imagine that we stopped an entire cluster and started a new one, then the entire set of workers would be invalid and the cluster would have to start by figuring this out and invalidating the workers. 

Instead we have chosen a different strategy for the workers:  

From the moment a `Worker` is created until it stops it will periodically register to the master using the `RegisterWorker` message, this means we do not have to persist the list of workers, if a `backend` node fails and the master is started on a new node, the workers will simply start sending their periodic registrations to the new node.

It also means that if a worker goes away, it will stop periodically pinging the master and we can detect that it is no longer available. We also remove workers that hasn't come back with a work result before the `work-timeout`.

@@snip [Master.scala]($g8src$/scala/worker/Master.scala) { #pruning }

When stopping a `Worker` Actor still tries to gracefully remove it self using the `DeRegisterWorker` message, but in case of crash it will have no chance to communicate that with the master node.

@@snip [Master.scala]($g8src$/scala/worker/Master.scala) { #graceful-remove }


