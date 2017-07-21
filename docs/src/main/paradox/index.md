# Akka Distributed Workers with Scala Guide
 
Akka is a toolkit and runtime for building highly concurrent, distributed, and fault-tolerant event-driven applications on the JVM. Akka can be used with both Java and Scala.
This guide introduces Akka by describing the Scala version of the Hello World example. If you prefer to use Akka with Java, switch to the [Akka Distributed Workers with Java guide](http://developer.lightbend.com/guides/akka-quickstart-java/).

This guide demonstrates more advanced usage of Akka and requires familiarity with Actors. If you have no previous experience with Actors you should start with [Akka Quickstart with Scala](http://developer.lightbend.com/guides/akka-quickstart-scala/) which goes through the basics.

Within 30 minutes, you should be able to download and run the example and use this guide to understand how the example is constructed. This will get you started building distributed systems with Akka.

## Downloading the example 

The Akka Distributed Workers example for Scala is a zipped project that includes a distribution of sbt (build tool). You can run it on Linux, MacOS, or Windows. The only prerequisite is Java 8.

Download and unzip the example:

1. Download the zip file from [Lightbend Tech Hub](http://dev.lightbend.com/start/?group=akka&project=akka-distributed-workers-scala) by clicking `CREATE A PROJECT FOR ME`. 
1. Extract the zip file to a convenient location: 
  - On Linux and OSX systems, open a terminal and use the command `unzip akka-distributed-workers-scala.zip`. Note: On OSX, if you unzip using Archiver, you also have to make the sbt files executable:
```
 $ chmod u+x ./sbt
 $ chmod u+x ./sbt-dist/bin/sbt
```
  - On Windows, use a tool such as File Explorer to extract the project. 

## Running the example

To run the sample application, which starts a small cluster inside of the same JVM instance:

1. In a console, change directories to the top level of the unzipped project.
 
    For example, if you used the default project name, akka-distributed-workers-scala, and extracted the project to your root directory,
    from the root directory, enter: `cd akka-distributed-workers-scala`

1. Enter `./sbt` on OSX/Linux or `sbt.bat` on Windows to start sbt.
 
    sbt downloads project dependencies. The `>` prompt indicates sbt has started in interactive mode.

1. At the sbt prompt, enter `run`.
 
    sbt builds the project and runs the `Main` of the project:

After waiting a few seconds for the cluster to form the output should start look _something_ like this (scroll all the way to the right to see the Actor output):
 
```
[INFO] [07/21/2017 17:41:53.320] [ClusterSystem-akka.actor.default-dispatcher-16] [akka.tcp://ClusterSystem@127.0.0.1:51983/user/producer] Produced work: 3
[INFO] [07/21/2017 17:41:53.322] [ClusterSystem-akka.actor.default-dispatcher-3] [akka.tcp://ClusterSystem@127.0.0.1:2551/user/master/singleton] Accepted work: 3bce4d6d-eaae-4da6-b316-0c6f566f2399
[INFO] [07/21/2017 17:41:53.328] [ClusterSystem-akka.actor.default-dispatcher-3] [akka.tcp://ClusterSystem@127.0.0.1:2551/user/master/singleton] Giving worker 2b646020-6273-437c-aa0d-4aad6f12fb47 some work 3bce4d6d-eaae-4da6-b316-0c6f566f2399
[INFO] [07/21/2017 17:41:53.328] [ClusterSystem-akka.actor.default-dispatcher-2] [akka.tcp://ClusterSystem@127.0.0.1:51980/user/worker] Got work: 3
[INFO] [07/21/2017 17:41:53.328] [ClusterSystem-akka.actor.default-dispatcher-16] [akka.tcp://ClusterSystem@127.0.0.1:51980/user/worker] Work is complete. Result 3 * 3 = 9.
[INFO] [07/21/2017 17:41:53.329] [ClusterSystem-akka.actor.default-dispatcher-19] [akka.tcp://ClusterSystem@127.0.0.1:2551/user/master/singleton] Work 3bce4d6d-eaae-4da6-b316-0c6f566f2399 is done by worker 2b646020-6273-437c-aa0d-4aad6f12fb47
```
   
Congratulations, you just ran your first Akka Cluster app. Now take a look at what happened under the covers. 

## What happens when you run it

When `Main` is run without any parameters, it starts 4 `ActorSystem`s in the same JVM which then form a single cluster. 

Two of the nodes have the role `frontend` and simulate having an external interface, such as a REST API, where workloads can be posted. 

One of the node has the role `worker` and accepts and processes workloads.

Two nodes have the role `backend` and contain an actor called `Master` which coordinates workloads from the frontend nodes, keep track of the workers, and delegate work to available workers. If one of these nodes goes down, the other one takes over its responsibilities.

A bird's eye perspective of the architecture would look like this:

TODO maybe match the image with the default setup
![Overview](images/overview.png)

The application was designed like this to support the following requirements:

 * elastic addition/removal of frontend nodes that receives work from clients
 * elastic addition/removal of worker actors and worker nodes
 * each worker hosting one or more workers
 * jobs should not be lost, and if a worker fails, the job should be retried
  
The design is based on Derek Wyatt's blog post 
[Balancing Workload Across Nodes with Akka 2](http://letitcrash.com/post/29044669086/balancing-workload-across-nodes-with-akka-2) which is a bit old but a good description of
the advantages of letting the workers pull work from the master instead of pushing work to
the workers.
 
Let's look at the details of each part of the application:

@@@index

* [A Closer Look at the Master](master.md)
* [The Frontend](frontend.md)
* [The Workers](worker.md)
* [Piecing it All Together](all-together.md)
* [Running the Application](running.md)
* [Next Steps](next-steps.md)

@@@