package worker

import java.util.concurrent.ThreadLocalRandom

import akka.actor.{Actor, Props}

import scala.concurrent.duration._

/**
 * Work executor is the actor actually performing the work.
 */
object WorkExecutor {
  def props = Props(new WorkExecutor)

  case class DoWork(n: Int)
  case class WorkComplete(result: String)
}

class WorkExecutor extends Actor {
  import WorkExecutor._
  import context.dispatcher

  def receive = {
    case DoWork(n: Int) =>
      val n2 = n * n
      val result = s"$n * $n = $n2"

      // simulate that the processing time varies
      val randomProcessingTime = ThreadLocalRandom.current.nextInt(1, 3).seconds
      context.system.scheduler.scheduleOnce(randomProcessingTime, sender(), WorkComplete(result))
  }

}