# The Frontend

A typical frontend provides a RESTful API that is used by the clients to submit (POST) jobs.
When the service has accepted the job it returns `Created/201` response code to the client.
If it can't accept the job it returns a failure response code and the client has to retry or
discard the job.

In this example the frontend is emulated, for simplicity, by an ordinary actor, see
<a href="#code/src/main/scala/worker/Frontend.scala" class="shortcut">Frontend.scala</a>
and client requests are simulated by the
<a href="#code/src/main/scala/worker/WorkProducer.scala" class="shortcut">WorkProducer.scala</a>.
As you can see the `Frontend` actor sends the work to the active master via the
`ClusterSingletonProxy`. It doesn't care about the exact location of the
master. Somewhere in the cluster there should be one master actor running.
The message is sent with `ask/?` to be able to reply to the client (`WorkProducer`)
when the job has been accepted or denied by the master.

![Frontend to Master Message Flow](images/frontend-master-message-flow.png)

You can see how a Frontend and WorkProducer actor is started in the method `startFrontend`
in <a href="#code/src/main/scala/worker/Main.scala" class="shortcut">Main.scala</a>