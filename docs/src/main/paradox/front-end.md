# Front-End Nodes

A typical front-end provides a RESTful API that is used by the clients to submit jobs. Implementing such a HTTP API is a perfect fit for [Akka HTTP](http://doc.akka.io/docs/akka-http/current/scala/http/introduction.html) or [Play Framework](https://www.playframework.com) but to keep the scope of this guide limited we have chosen to  emulate the front-end with an ordinary actor, `FrontEnd`,  that in random intervals generates a payload and sends to the `Master`.

## The Front-end Actor

@@snip [FrontEnd.scala]($g8src$/scala/worker/Frontend.scala) { #front-end }

As you can see the `FrontEnd` actor schedules `Tick` messages to itself when starting up. the `Tick` message then triggers creation of a new `Work`, sending the work to the `Master` actor on a `back-end` node and switching to a new `busy` behavior.

Sending work to the active master is done via the `ClusterSingletonProxy`. This means that the `FrontEnd` actor itself does not know about the exact location of the master. Somewhere in the cluster there should be one master actor running.

The message is sent with `?` ([ask](http://doc.akka.io/docs/akka/current/scala/actors.html#ask-send-and-receive-future)) to add a timeout for getting a reply from the when the job has been accepted or denied by the master. If the timeout hits this will fail the returned `Future` with an `akka.pattern.AskTimeoutException`, we transform any such failure into a `NotOk` to handle timeout and negative response in the same way.

The future is then `pipe`d to the actor itself, meaning that when it completes the value it is completed with is sent to the actor as a message.

When a workload has been acknowledged by the master, the actor schedules a new tick to itself and toggles back to the `idle` behavior.  

If the work is not accepted or there is no response, for example if the message or response got lost, the `FrontEnd` actor backs off a bit and then sends the work again.

You can see the how the actors on a front-end node is started in the method `Main.startFrontEnd`:

@@snip [Main.scala]($g8src$/scala/worker/Main.scala) { #front-end }

## The Work Result Consumer Actor

The `FrontEnd` actor only concerns itself with posting workloads, and does not in any way care when the work has been completed. That is instead done by the `WorkResultConsumerActor`.

When a workload has been processed successfully this will be published to all interested cluster nodes through
[Distributed Pub-Sub](http://doc.akka.io/docs/akka/current/scala/distributed-pub-sub.html#distributed-publish-subscribe-in-cluster)

In addition to the `FrontEnd` actor the front-end nodes start an actor that subscribes to the completion events and logs when a workload has completed:

@@snip [Main.scala]($g8src$/scala/worker/WorkResultConsumer.scala) { #work-result-consumer }

In an actual application you would probably want a way for clients to poll or stream the status changes of the submitted work.