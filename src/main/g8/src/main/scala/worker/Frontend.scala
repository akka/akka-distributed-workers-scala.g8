package worker

import scala.concurrent.duration._
import akka.actor.Actor
import akka.pattern._
import akka.util.Timeout
import akka.cluster.singleton.{ClusterSingletonProxySettings, ClusterSingletonProxy}

object Frontend {
  case object Ok
  case object NotOk
}

class Frontend extends Actor {
  import Frontend._
  import context.dispatcher

  val masterProxy = context.actorOf(
    MasterSingleton.proxyProps(context.system),
    name = "masterProxy")

  def receive = {
    case work =>
      implicit val timeout = Timeout(5.seconds)
      (masterProxy ? work) map {
        case Master.Ack(_) => Ok
      } recover { case _ => NotOk } pipeTo sender()

  }

}