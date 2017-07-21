## Run the Application

Open the <a href="#run" class="shortcut">Run</a> tab and select `worker.Main` followed
by Restart. On the left-hand side we can see the console output, which is logging output
from nodes joining the cluster, the simulated work and results.

The <a href="#code/src/main/scala/worker/Main.scala" class="shortcut">worker.Main</a>
starts three actor systems in the same JVM process. It can be more
interesting to run them in separate processes. <b>Stop</b> the application in the
<a href="#run" class="shortcut">Run</a> tab and then open three terminal windows.

In the first terminal window, start the first seed node with the following command:

```bash
sbt "runMain worker.Main 2551"
```

2551 corresponds to the port of the first seed-nodes element in the configuration. In the log
output you see that the cluster node has been started and changed status to 'Up'.

In the second terminal window, start the frontend node with the following command:

```bash
sbt "runMain worker.Main 3001"		
```

3001 is to the port of the node. In the log output you see that the cluster node has been started
and joins the 2551 node and becomes a member of the cluster. Its status changed to 'Up'.

Switch over to the first terminal window and see in the log output that the member joined.
So far, no `Worker` has not been started, i.e. jobs are produced and accepted but
not processed.

In the third terminal window, start a worker node with the following command:

```bash
sbt "runMain worker.Main 0"
```

Now you don't need to specify the port number, 0 means that it will use a random available port.
This worker node is not part of the cluster, but it connects to one of the configured cluster
nodes via the `ClusterClient`. Look at the log output in the different terminal
windows. In the second window (frontend) you should see that the produced jobs are processed
and logged as `"Consumed result"`.

Take a look at the logging that is done in 
<a href="#code/src/main/scala/worker/WorkProducer.scala" class="shortcut">WorkProducer.scala</a>,
<a href="#code/src/main/scala/worker/Master.scala" class="shortcut">Master</a>
and <a href="#code/src/main/scala/worker/Worker.scala" class="shortcut">Worker.scala</a>.
Identify the corresponding log entries in the 3 terminal windows.

Shutdown the worker node (third terminal window) with `ctrl-c`.
Observe how the `"Consumed result"` logs in the frontend node (second terminal window)
stops. Start the worker node again.

```bash
sbt "runMain worker.Main 0"
```

You can also start more such worker nodes in new terminal windows.

You can start more cluster backend nodes using port numbers between 2000-2999:

```bash
sbt "runMain worker.Main 2552"
```

You can start more cluster frontend nodes using port numbers between 3000-3999:

```
sbt "runMain worker.Main 3002"		
```

Note that this sample runs the 
<a href="http://doc.akka.io/docs/akka/2.4.0/scala/persistence.html#Shared_LevelDB_journal" target="_blank">shared LevelDB journal</a>
on the node with port 2551. This is a single point of failure, and should not be used in production. 
A real system would use a 
<a href="http://akka.io/community/" target="_blank">distributed journal</a>.

The files of the shared journal are saved in the target directory and when you restart
the application the state is recovered. You can clean the state with:

```bash
sbt clean
```