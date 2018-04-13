package worker

import akka.actor.{ActorLogging, ActorRef, Cancellable, Props, Timers}
import akka.cluster.pubsub.{DistributedPubSub, DistributedPubSubMediator}
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}

import scala.concurrent.duration.{Deadline, FiniteDuration, _}

/**
 * The master actor keep tracks of all available workers, and all scheduled and ongoing work items
 */
object Master {

  val ResultsTopic = "results"

  def props(workTimeout: FiniteDuration): Props =
    Props(new Master(workTimeout))

  case class Ack(workId: String)

  private sealed trait WorkerStatus
  private case object Idle extends WorkerStatus
  private case class Busy(workId: String, deadline: Deadline) extends WorkerStatus
  private case class WorkerState(ref: ActorRef, status: WorkerStatus, staleWorkerDeadline: Deadline)

  private case object CleanupTick

}

class Master(workTimeout: FiniteDuration) extends Timers with PersistentActor with ActorLogging {
  import Master._
  import WorkState._
  import context.dispatcher

  override val persistenceId: String = "master"

  val considerWorkerDeadAfter: FiniteDuration =
    context.system.settings.config.getDuration("distributed-workers.consider-worker-dead-after").getSeconds.seconds
  def newStaleWorkerDeadline(): Deadline = considerWorkerDeadAfter.fromNow

  timers.startPeriodicTimer("cleanup", CleanupTick, workTimeout / 2)

  val mediator: ActorRef = DistributedPubSub(context.system).mediator

  // the set of available workers is not event sourced as it depends on the current set of workers
  private var workers = Map[String, WorkerState]()

  // workState is event sourced to be able to make sure work is processed even in case of crash
  private var workState = WorkState.empty


  override def receiveRecover: Receive = {

    case SnapshotOffer(_, workStateSnapshot: WorkState) =>
      // If we would have  logic triggering snapshots in the actor
      // we would start from the latest snapshot here when recovering
      log.info("Got snapshot work state")
      workState = workStateSnapshot

    case event: WorkDomainEvent =>
      // only update current state by applying the event, no side effects
      workState = workState.updated(event)
      log.info("Replayed {}", event.getClass.getSimpleName)

    case RecoveryCompleted =>
      log.info("Recovery completed")

  }

  override def receiveCommand: Receive = {
    case MasterWorkerProtocol.RegisterWorker(workerId) =>
      if (workers.contains(workerId)) {
        workers += (workerId -> workers(workerId).copy(ref = sender(), staleWorkerDeadline = newStaleWorkerDeadline()))
      } else {
        log.info("Worker registered: {}", workerId)
        val initialWorkerState = WorkerState(
          ref = sender(),
          status = Idle,
          staleWorkerDeadline = newStaleWorkerDeadline())
        workers += (workerId -> initialWorkerState)

        if (workState.hasWork)
          sender() ! MasterWorkerProtocol.WorkIsReady
      }

    // #graceful-remove
    case MasterWorkerProtocol.DeRegisterWorker(workerId) =>
      workers.get(workerId) match {
        case Some(WorkerState(_, Busy(workId, _), _)) =>
          // there was a workload assigned to the worker when it left
          log.info("Busy worker de-registered: {}", workerId)
          persist(WorkerFailed(workId)) { event ⇒
            workState = workState.updated(event)
            notifyWorkers()
          }
        case Some(_) =>
          log.info("Worker de-registered: {}", workerId)
        case _ =>
      }
      workers -= workerId
    // #graceful-remove

    case MasterWorkerProtocol.WorkerRequestsWork(workerId) =>
      if (workState.hasWork) {
        workers.get(workerId) match {
          case Some(workerState @ WorkerState(_, Idle, _)) =>
            val work = workState.nextWork
            persist(WorkStarted(work.workId)) { event =>
              workState = workState.updated(event)
              log.info("Giving worker {} some work {}", workerId, work.workId)
              val newWorkerState = workerState.copy(
                status = Busy(work.workId, Deadline.now + workTimeout),
                staleWorkerDeadline = newStaleWorkerDeadline())
              workers += (workerId -> newWorkerState)
              sender() ! work
            }
          case _ =>
        }
      }

    case MasterWorkerProtocol.WorkIsDone(workerId, workId, result) =>
      // idempotent - redelivery from the worker may cause duplicates, so it needs to be
      if (workState.isDone(workId)) {
        // previous Ack was lost, confirm again that this is done
        sender() ! MasterWorkerProtocol.Ack(workId)
      } else if (!workState.isInProgress(workId)) {
        log.info("Work {} not in progress, reported as done by worker {}", workId, workerId)
      } else {
        log.info("Work {} is done by worker {}", workId, workerId)
        changeWorkerToIdle(workerId, workId)
        persist(WorkCompleted(workId, result)) { event ⇒
          workState = workState.updated(event)
          mediator ! DistributedPubSubMediator.Publish(ResultsTopic, WorkResult(workId, result))
          // Ack back to original sender
          sender ! MasterWorkerProtocol.Ack(workId)
        }
      }

    case MasterWorkerProtocol.WorkFailed(workerId, workId) =>
      if (workState.isInProgress(workId)) {
        log.info("Work {} failed by worker {}", workId, workerId)
        changeWorkerToIdle(workerId, workId)
        persist(WorkerFailed(workId)) { event ⇒
          workState = workState.updated(event)
          notifyWorkers()
        }
      }

    // #persisting
    case work: Work =>
      // idempotent
      if (workState.isAccepted(work.workId)) {
        sender() ! Master.Ack(work.workId)
      } else {
        log.info("Accepted work: {}", work.workId)
        persist(WorkAccepted(work)) { event ⇒
          // Ack back to original sender
          sender() ! Master.Ack(work.workId)
          workState = workState.updated(event)
          notifyWorkers()
        }
      }
    // #persisting

    // #pruning
    case CleanupTick =>
      workers.foreach {
        case (workerId, WorkerState(_, Busy(workId, timeout), _)) if timeout.isOverdue() =>
          log.info("Work timed out: {}", workId)
          workers -= workerId
          persist(WorkerTimedOut(workId)) { event ⇒
            workState = workState.updated(event)
            notifyWorkers()
          }


        case (workerId, WorkerState(_, Idle, lastHeardFrom)) if lastHeardFrom.isOverdue() =>
          log.info("Too long since heard from worker {}, pruning", workerId)
          workers -= workerId

        case _ => // this one is a keeper!
      }
    // #pruning
  }

  def notifyWorkers(): Unit =
    if (workState.hasWork) {
      workers.foreach {
        case (_, WorkerState(ref, Idle, _)) => ref ! MasterWorkerProtocol.WorkIsReady
        case _                           => // busy
      }
    }

  def changeWorkerToIdle(workerId: String, workId: String): Unit =
    workers.get(workerId) match {
      case Some(workerState @ WorkerState(_, Busy(`workId`, _), _)) ⇒
        val newWorkerState = workerState.copy(status = Idle, staleWorkerDeadline = newStaleWorkerDeadline())
        workers += (workerId -> newWorkerState)
      case _ ⇒
        // ok, might happen after standby recovery, worker state is not persisted
    }

  def tooLongSinceHeardFrom(lastHeardFrom: Long) =
    System.currentTimeMillis() - lastHeardFrom > considerWorkerDeadAfter.toMillis

}