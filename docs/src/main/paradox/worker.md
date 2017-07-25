# The Worker Nodes

You can see how a worker node and a number of worker actors is started in the method `Main.startWorker`:

@@snip [Main.scala]($g8src$/scala/worker/Main.scala) { #worker }

When the worker receives work from the master it delegates the actual processing to a child actor, `WorkExecutor`, to keep the worker responsive while executing the work.

The worker actor registers to the `Master` node, so that it know which workers exist.

When the `Master` Actor has work ready, it sends a `WorkIsReady` message to all non-busy workers it knows about, and on the other end, only `Worker` Actors that are `idle` will reply to the `Master` with a `WorkerRequestsWork` message, the `Master` Actor then chooses one (the first) actor that replies and assigns the `Work` to it.

This creates back pressure in that the `Master` actor will not push more work onto the worker nodes than they can process. And while busy processing a workload, the `WorkIsReady` messages does not pile up in the worker inbox.

![Master to Worker Message Flow](images/master-worker-message-flow.png)

