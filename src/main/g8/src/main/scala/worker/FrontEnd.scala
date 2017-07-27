/**
 * Copyright (C) 2009-2017 Lightbend Inc. <http://www.lightbend.com>
 */
package worker

import java.util.UUID
import java.util.concurrent.ThreadLocalRandom

import akka.actor.{Actor, ActorLogging, Cancellable, Props}
import akka.pattern._
import akka.util.Timeout

import scala.concurrent.duration._

/**
 * Dummy front-end that periodically sends a workload to the master.
 */
object FrontEnd {

  def props: Props = Props(new FrontEnd)

  private case object NotOk
  private case object Tick
  private case object Retry
}

// #front-end
class FrontEnd extends Actor with ActorLogging {
  import FrontEnd._
  import context.dispatcher

  val masterProxy = context.actorOf(
    MasterSingleton.proxyProps(context.system),
    name = "masterProxy")

  var workCounter = 0
  var tickTask: Option[Cancellable] = Some(scheduler.scheduleOnce(5.seconds, self, Tick))

  def nextWorkId(): String = UUID.randomUUID().toString

  def scheduler = context.system.scheduler


  def receive = idle

  def idle: Receive = {
    case Tick =>
      workCounter += 1
      log.info("Produced work: {}", workCounter)
      val work = Work(nextWorkId(), workCounter)
      sendWorkAndWaitForAccept(work)
  }

  def sendWorkAndWaitForAccept(work: Work) = {
    implicit val timeout = Timeout(5.seconds)
    (masterProxy ? work).recover {
      case _ => NotOk
    } pipeTo self

    context.become(busy(work))
  }

  def busy(workInProgress: Work): Receive = {
    case Master.Ack(_) =>
      val nextTick = ThreadLocalRandom.current.nextInt(3, 10).seconds
      tickTask = Some(scheduler.scheduleOnce(nextTick, self, Tick))
      context.become(idle)

    case NotOk =>
      log.info("Work not accepted, retry after a while")
      tickTask = Some(scheduler.scheduleOnce(3.seconds, self, Retry))

    case Retry =>
      sendWorkAndWaitForAccept(workInProgress)
  }

  override def postStop(): Unit = {
    tickTask.foreach(_.cancel())
  }
}
// #front-end