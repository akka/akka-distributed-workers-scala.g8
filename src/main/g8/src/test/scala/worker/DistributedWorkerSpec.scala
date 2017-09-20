package worker

import java.io.File

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, PoisonPill, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{CurrentClusterState, MemberUp}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{CurrentTopics, GetTopics, Subscribe, SubscribeAck}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.typesafe.config.ConfigFactory
import org.apache.commons.io.FileUtils
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object DistributedWorkerSpec {

  val clusterConfig = ConfigFactory.parseString("""
    akka {
      persistence {
        journal.plugin = "akka.persistence.journal.inmem"
        snapshot-store {
          plugin = "akka.persistence.snapshot-store.local"
          local.dir = "target/test-snapshots"
        }
      }
      extensions = ["akka.cluster.pubsub.DistributedPubSub"]
    }
    distributed-workers.consider-worker-dead-after = 10s
    distributed-workers.worker-registration-interval = 1s
    """).withFallback(ConfigFactory.load())

  class FlakyWorkExecutor extends Actor with ActorLogging {
    var i = 0

    override def postRestart(reason: Throwable): Unit = {
      i = 3
      super.postRestart(reason)
    }

    def receive = {
      case WorkExecutor.DoWork(n: Int) =>
        i += 1
        if (i == 3) {
          log.info("Cannot be trusted, crashing")
          throw new RuntimeException("Flaky worker")
        } else if (i == 5) {
          log.info("Cannot be trusted, stopping myself")
          context.stop(self)
        } else {
          val n2 = n * n
          val result = s"$n * $n = $n2"
          log.info("Cannot be trusted, but did complete work: {}", result)
          sender() ! WorkExecutor.WorkComplete(result)
        }
    }
  }

  class FastWorkExecutor extends Actor with ActorLogging {
    def receive = {
      case WorkExecutor.DoWork(n: Int) =>
        val n2 = n * n
        val result = s"$n * $n = $n2"
        sender() ! WorkExecutor.WorkComplete(result)
    }
  }

  class RemoteControllableFrontend extends FrontEnd {

    var currentWorkIdAndSender: Option[(String, ActorRef)] = None

    override def idle: Receive = {
      // just to be able to send one message at a time from the test
      currentWorkIdAndSender match {
        case Some((workId, originalSender)) => originalSender ! "ok-" + workId
        case None =>
      }
      currentWorkIdAndSender = None

      {
        case work: Work =>
          log.debug("Forwarding some work: {}", work)
          sendWork(work)
          currentWorkIdAndSender = Some((work.workId, sender()))
          context.become(busy(work))
      }
    }
  }
}

class DistributedWorkerSpec(_system: ActorSystem)
  extends TestKit(_system)
  with Matchers
  with FlatSpecLike
  with BeforeAndAfterAll
  with ImplicitSender {

  import DistributedWorkerSpec._

  val workTimeout = 3.seconds

  def this() = this(ActorSystem("DistributedWorkerSpec", DistributedWorkerSpec.clusterConfig))

  val backendSystem: ActorSystem = {
    val config = ConfigFactory.parseString("akka.cluster.roles=[back-end]").withFallback(clusterConfig)
    ActorSystem("DistributedWorkerSpec", config)
  }

  val workerSystem: ActorSystem = ActorSystem("DistributedWorkerSpec", clusterConfig)

  val storageLocations = List(
    "akka.persistence.journal.leveldb.dir",
    "akka.persistence.snapshot-store.local.dir").map(s => new File(system.settings.config.getString(s)))

  override def beforeAll(): Unit = {
    // make sure we do not use persisted data from a previous run
    storageLocations.foreach(dir => FileUtils.deleteDirectory(dir))
  }

  "Distributed workers" should "perform work and publish results" in {
    val clusterAddress = Cluster(backendSystem).selfAddress
    val clusterProbe = TestProbe()
    Cluster(backendSystem).subscribe(clusterProbe.ref, classOf[MemberUp])
    clusterProbe.expectMsgType[CurrentClusterState]
    Cluster(backendSystem).join(clusterAddress)
    clusterProbe.expectMsgType[MemberUp]

    backendSystem.actorOf(
      ClusterSingletonManager.props(
        Master.props(workTimeout),
        PoisonPill,
        ClusterSingletonManagerSettings(system).withRole("back-end")),
      "master")

    Cluster(workerSystem).join(clusterAddress)

    val masterProxy = workerSystem.actorOf(
      MasterSingleton.proxyProps(workerSystem),
      name = "masterProxy")
    val fastWorkerProps = Props(new Worker(masterProxy) {
      override def createWorkExecutor(): ActorRef = context.actorOf(Props(new FastWorkExecutor), "fast-executor")
    })

    for (n <- 1 to 3)
      workerSystem.actorOf(fastWorkerProps, "worker-" + n)

    val flakyWorkerProps = Props(new Worker(masterProxy) {
      override def createWorkExecutor(): ActorRef = {
        context.actorOf(Props(new FlakyWorkExecutor), "flaky-executor")
      }
    })
    val flakyWorker = workerSystem.actorOf(flakyWorkerProps, "flaky-worker")

    Cluster(system).join(clusterAddress)
    clusterProbe.expectMsgType[MemberUp]

    // allow posting work from the outside

    val frontend = system.actorOf(Props[RemoteControllableFrontend], "front-end")

    val results = TestProbe()
    DistributedPubSub(system).mediator ! Subscribe(Master.ResultsTopic, results.ref)
    expectMsgType[SubscribeAck]

    // make sure pub sub topics are replicated over to the back-end system before triggering any work
    within(10.seconds) {
      awaitAssert {
        DistributedPubSub(backendSystem).mediator ! GetTopics
        expectMsgType[CurrentTopics].getTopics() should contain(Master.ResultsTopic)
      }
    }

    // make sure we can get one piece of work through to fail fast if it doesn't
    frontend ! Work("1", 1)
    expectMsg("ok-1")
    within(10.seconds) {
      awaitAssert {
        results.expectMsgType[WorkResult].workId should be("1")
      }
    }


    // and then send in some actual work
    for (n <- 2 to 100) {
      frontend ! Work(n.toString, n)
      expectMsg(s"ok-$n")
    }
    system.log.info("99 work items sent")

    results.within(20.seconds) {
      val ids = results.receiveN(99).map { case WorkResult(workId, _) => workId }
      // nothing lost, and no duplicates
      ids.toVector.map(_.toInt).sorted should be((2 to 100).toVector)
    }

  }

  override def afterAll(): Unit = {
    import scala.concurrent.ExecutionContext.Implicits.global
    val allTerminated = Future.sequence(Seq(
      system.terminate(),
      backendSystem.terminate(),
      workerSystem.terminate()
    ))

    Await.ready(allTerminated, Duration.Inf)

    storageLocations.foreach(dir => FileUtils.deleteDirectory(dir))
  }

}