/**
 * Copyright (C) 2009-2017 Lightbend Inc. <http://www.lightbend.com>
 */
package worker

import akka.actor.{ActorIdentity, ActorPath, ActorSystem, Identify, Props}
import akka.pattern.ask
import akka.persistence.journal.leveldb.{SharedLeveldbJournal, SharedLeveldbStore}
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.duration._

object Main {

  val backendPortRange = 2000 to 2999
  val frontendPortRange = 3000 to 3999

  def main(args: Array[String]): Unit = {
    args.headOption.map(_.toInt) match {
      case None => startClusterInSameJvm()
      case Some(port) if backendPortRange.contains(port) => startBackend(port)
      case Some(port) if frontendPortRange.contains(port) => startFrontend(port)
      case Some(port) => startWorker(port, args.lift(1).map(_.toInt).getOrElse(1))
    }
  }

  def startClusterInSameJvm(): Unit = {
    // two backend nodes
    startBackend(2551)
    startBackend(2552)
    // two frontend nodes
    startFrontend(3000)
    startFrontend(3001)
    // two worker nodes with two worker actors each
    startWorker(5001, 2)
    startWorker(5002, 2)
  }

  /**
   * Start a node with the role backend on the given port. (This may also
   * start the shared journal, see below for details)
   */
  def startBackend(port: Int): Unit = {
    val system = ActorSystem("ClusterSystem", config(port, "backend"))
    startupSharedJournal(
      system,
      startStore = (port == 2551),
      path = ActorPath.fromString("akka.tcp://ClusterSystem@127.0.0.1:2551/user/store"))
    MasterSingleton.startSingleton(system)
  }

  /**
   * Start a front end node that will submit work to the backend nodes
   */
  def startFrontend(port: Int): Unit = {
    val system = ActorSystem("ClusterSystem", config(port, "frontend"))
    system.actorOf(Frontend.props, "frontend")
    system.actorOf(WorkResultConsumer.props, "consumer")
  }

  /**
   * Start a worker node, with n actual workers that will accept and process workloads
   */
  def startWorker(port: Int, workers: Int): Unit = {
    val system = ActorSystem("ClusterSystem", config(port, "worker"))
    for {
      n <- 1 to workers
    } {
      system.actorOf(Worker.props(), s"worker-$n")
    }
  }


  def config(port: Int, role: String): Config =
    ConfigFactory.parseString(s"""
      akka.remote.netty.tcp.port=$port
      akka.cluster.roles=[$role]
    """).withFallback(ConfigFactory.load())

  /**
   * To simplify the sample we run a shared journal inside of the actor system. This avoids having
   * the need to set up an actual (distributed) database and configure connections for that. For a
   * real application you would run an actual database backing persistence.
   *
   * This means we add a single point of failure to the system, if the node running the journal is
   * killed the sample will break.
   */
  def startupSharedJournal(system: ActorSystem, startStore: Boolean, path: ActorPath): Unit = {
    if (startStore)
      system.actorOf(Props[SharedLeveldbStore], "store")
    // register the shared journal
    import system.dispatcher
    implicit val timeout = Timeout(15.seconds)
    val f = (system.actorSelection(path) ? Identify(None))
    f.onSuccess {
      case ActorIdentity(_, Some(ref)) => SharedLeveldbJournal.setStore(ref, system)
      case _ =>
        system.log.error("Shared journal not started at {}", path)
        system.terminate()
    }
    f.onFailure {
      case _ =>
        system.log.error("Lookup of shared journal at {} timed out", path)
        system.terminate()
    }
  }
}
