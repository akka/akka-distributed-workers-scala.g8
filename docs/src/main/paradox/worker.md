# The Worker Nodes

When the worker receives work from the master it delegates the actual processing to a child actor, `WorkExecutor`, to keep the worker responsive while executing the work.

The worker actor registers to the `Master` node, so that it know which workers exist.

When the `Master` actor has work ready, it sends a `WorkIsReady` message to all workers it thinks is not busy, only the `Worker` actors that are `idle` will reply to the `Master` with a `WorkerRequestsWork` message, the `Master` actor then chooses one (the first) actor that replies and assigns the `Work` to it.

This creates achieves back pressure from busy nodes. The `Master` actor will not push more work onto the worker nodes than they can process. It also means that while a worker is busy processing a workload, the `WorkIsReady` messages does not pile up in the worker inbox as it is the `WorkExecutor` that is actually doing the processing.

![Master to Worker Message Flow](images/master-worker-message-flow.png)

You can see how a worker node and a number of worker actors is started in the method `Main.startWorker`:

@@snip [Main.scala]($g8src$/scala/worker/Main.scala) { #worker }

Now that we have covered all the details, we can experiment with different sets of nodes for the cluster.