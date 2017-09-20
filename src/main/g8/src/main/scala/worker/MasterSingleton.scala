package worker

import akka.actor.{ActorSystem, PoisonPill}
import akka.cluster.singleton._

import scala.concurrent.duration._

object MasterSingleton {

  private val singletonName = "master"
  private val singletonRole = "back-end"

  // #singleton
  def startSingleton(system: ActorSystem) = {
    val workTimeout = system.settings.config.getDuration("distributed-workers.work-timeout").getSeconds.seconds

    system.actorOf(
      ClusterSingletonManager.props(
        Master.props(workTimeout),
        PoisonPill,
        ClusterSingletonManagerSettings(system).withRole(singletonRole)
      ),
      singletonName)
  }
  // #singleton

  // #proxy
  def proxyProps(system: ActorSystem) = ClusterSingletonProxy.props(
    settings = ClusterSingletonProxySettings(system).withRole(singletonRole),
    singletonManagerPath = s"/user/$singletonName")
  // #proxy
}
