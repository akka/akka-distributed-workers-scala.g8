package worker

import akka.actor.{ActorIdentity, ActorPath, ActorSystem, Identify, PoisonPill, Props}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings}
import akka.pattern.ask
import akka.persistence.journal.leveldb.{SharedLeveldbJournal, SharedLeveldbStore}
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.duration._

object Main {

  def main(args: Array[String]): Unit = {
    if (args.isEmpty) {
      // with no args, we run a few
      // different nodes in the same JVM
      startBackend(2551)
      startBackend(2552)
      Thread.sleep(2000)
      startWorker(0)
      Thread.sleep(2000)
      startFrontend(0)
    } else {
      // if there is an int arg we use it
      // to decide what kind of node this is
      val port = args(0).toInt
      if (2000 <= port && port <= 2999) startBackend(port)
      else if (3000 <= port && port <= 3999) startFrontend(port)
      else startWorker(port)
    }
  }

  def startBackend(port: Int): Unit = {
    val system = ActorSystem("ClusterSystem", configWithPortAndRole(port, "backend"))

    val shouldRunSharedStore = port == 2551
    startupSharedJournal(
      system,
      startStore = shouldRunSharedStore,
      path = ActorPath.fromString("akka.tcp://ClusterSystem@127.0.0.1:2551/user/store"))


    val workTimeout = 10.seconds
    system.actorOf(
      ClusterSingletonManager.props(
        Master.props(workTimeout),
        PoisonPill,
        ClusterSingletonManagerSettings(system).withRole("backend")
      ),
      "master")

  }

  def startFrontend(port: Int): Unit = {
    val system = ActorSystem("ClusterSystem", configWithPortAndRole(port, "frontent"))
    val frontend = system.actorOf(Props[Frontend], "frontend")
    system.actorOf(Props(classOf[WorkProducer], frontend), "producer")
    system.actorOf(Props[WorkResultConsumer], "consumer")
  }

  def startWorker(port: Int): Unit = {
    // load worker.conf
    val system = ActorSystem("ClusterSystem", configWithPortAndRole(port, "worker"))

    system.actorOf(Worker.props(Props[WorkExecutor]), "worker")
  }

  def startupSharedJournal(system: ActorSystem, startStore: Boolean, path: ActorPath): Unit = {
    // The shared leveldb journal is just for the guide, in a real application you
    // would be running an actual database to persist the journal to

    // Start the shared journal one one node (don't crash this SPOF)
    // This will not be needed with a distributed journal
    if (startStore)
      system.actorOf(Props[SharedLeveldbStore], "store")
    // register the shared journal
    import system.dispatcher
    implicit val timeout = Timeout(15.seconds)
    val f = system.actorSelection(path) ? Identify(None)
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

  def configWithPortAndRole(port: Int, role: String): Config =
    ConfigFactory.parseString(s"""
        akka.remote.netty.tcp.port=$port
        akka.cluster.roles=[$role]
      """
    ).withFallback(ConfigFactory.load()) // fallback to application.conf for other settings
}
