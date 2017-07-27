# Front-end Nodes

A typical front-end provides a RESTful API that is used by the clients to submit (POST) jobs.
When the service has accepted the job it returns `Created/201` response code to the client.
If it can't accept the job it returns a failure response code and the client has to retry or
discard the job.

This could be implemented nicely with Akka HTTP but to keep the scope of this guide limited we 
emulate the front-end with an ordinary actor, `FrontEnd`,  that in random intervals generates a 
payload and sends to the `Master`.

## The Front-end Actor

@@snip [FrontEnd.scala]($g8src$/scala/worker/Frontend.scala) { #front-end }

As you can see the `FrontEnd` actor sends the work to the active master via the
`ClusterSingletonProxy`. It doesn't care about the exact location of the
master. Somewhere in the cluster there should be one master actor running.
The message is sent with `ask/?` to be able to reply to the client (`WorkProducer`)
when the job has been accepted or denied by the master.

If the work is not accepted or there is no response the `FrontEnd` actor backs off a bit and then sends the work again.

You can see how a Frontend actor is started in the method `Main.startFrontEnd`:

@@snip [Main.scala]($g8src$/scala/worker/Main.scala) { #front-end }

## The Work Result Consumer Actor

When a workload has been processed successfully this will be published to all interested cluster nodes through
[Distributed Pub-Sub](http://doc.akka.io/docs/akka/current/scala/distributed-pub-sub.html#distributed-publish-subscribe-in-cluster)

In addition to the `FrontEnd` actor the front-end nodes start an actor that subscribes to the completion events and 
logs when a workload has completed:

@@snip [Main.scala]($g8src$/scala/worker/WorkResultConsumer.scala) { #work-result-consumer }

TODO this is a bit surprisingly squashed in here to showcase pub-sub? something more realistic instead?