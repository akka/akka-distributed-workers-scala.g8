## Run the Application

When running the applicaiton without parameters it runs a six node cluster within the same JVM. It can be more
interesting to run them in separate processes. Open three terminal windows.

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
sbt "runMain worker.Main 5001 3"
```

5001 means the node will be a worker node, and the second parameter `3` means that it will host three worker actors.

Look at the log output in the different terminal
windows. In the second window (frontend) you should see that the produced jobs are processed
and logged as `"Consumed result"`.

Take a look at the logging that is done in `WorkProducer`, `Master` and `Worker`.
Identify the corresponding log entries in the 3 terminal windows.

Shutdown the worker node (third terminal window) with `ctrl-c`.
Observe how the `"Consumed result"` logs in the frontend node (second terminal window)
stops. Start the worker node again.

```bash
sbt "runMain worker.Main 5001 3"
```

You can also start more such worker nodes in new terminal windows.

You can start more cluster backend nodes using port numbers between 2000-2999:

```bash
sbt "runMain worker.Main 2552"
```

You can start more cluster frontend nodes using port numbers between 3000-3999:

```bash
sbt "runMain worker.Main 3002"		
```

Any port outside these ranges creates a worker node, for which you can also play around with the number of worker actors on using the second parameter. 

```bash
sbt "runMain worker.Main 5009 4"		
```

## The journal 

Note that this sample runs the [shared LevelDB journal](http://doc.akka.io/docs/akka/current/scala/persistence.html#shared-leveldb-journal)
on the node with port 2551. This is a single point of failure, if you kill this node the sample app will not continue working. This is not something to you should be using in production, a real system would use a distributed journal.

The files of the shared journal are saved in the target directory and when you restart
the application the state is recovered. You can clean the state with:

```bash
sbt clean
```