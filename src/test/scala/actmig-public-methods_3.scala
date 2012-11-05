/**
 * NOTE: Code snippets from this test are included in the Actor Migration Guide. In case you change
 * code in these tests prior to the 2.10.0 release please send the notification to @vjovanov.
 */
package scala.actors.migration
import scala.collection.mutable.ArrayBuffer
import scala.actors._
import scala.actors.migration._
import scala.util._
import scala.concurrent._
import scala.concurrent.duration._
import java.util.concurrent.{ TimeUnit, CountDownLatch }
import scala.concurrent.duration._
import scala.actors.migration.pattern._
import scala.concurrent.ExecutionContext.Implicits.global

class PublicMethods3 extends PartestSuite {
  val checkFile = "actmig-public-methods"
  import org.junit._

  val NUMBER_OF_TESTS = 8

  // used for sorting non-deterministic output
  val buff = ArrayBuffer[String]()
  val latch = new CountDownLatch(NUMBER_OF_TESTS)
  val toStop = ArrayBuffer[ActorRef]()

  def append(v: String) = synchronized {
    buff += v
  }

  @Test(timeout = 10000)
  def test(): Unit = {

    val respActor = ActorDSL.actor(new ActWithStash {
      def receive = {
        case (x: String, time: Long) =>
          Thread.sleep(time)
          reply(x + " after " + time)
        case "forward" =>
          if (self == sender)
            append("forward succeeded")
          latch.countDown()
        case str: String =>
          append(str)
          latch.countDown()
        case x =>
          context.stop(self)
      }
    })

    toStop += respActor

    respActor ! "bang"

    {
      val msg = ("bang qmark", 0L)
      val res = respActor.?(msg)(Timeout(Duration.Inf))
      append(Await.result(res, Duration.Inf).toString)
      latch.countDown()
    }

    {
      val msg = ("bang qmark", 1L)
      val res = respActor.?(msg)(Timeout(5 seconds))

      val promise = Promise[Option[Any]]()
      res.onComplete(v => promise.success(v.toOption))
      append(Await.result(promise.future, Duration.Inf).toString)

      latch.countDown()
    }

    {
      val msg = ("bang qmark", 5000L)
      val res = respActor.?(msg)(Timeout(1 millisecond))
      val promise = Promise[Option[Any]]()
      res.onComplete(v => promise.success(v.toOption))
      append(Await.result(promise.future, Duration.Inf).toString)
      latch.countDown()
    }

    {
      val msg = ("bang bang in the future", 0L)
      val fut = respActor.?(msg)(Timeout(Duration.Inf))
      append(Await.result(fut, Duration.Inf).toString)
      latch.countDown()
    }

    {
      val handler: PartialFunction[Any, String] = {
        case x: String => x
      }

      val msg = ("typed bang bang in the future", 0L)
      val fut = (respActor.?(msg)(Timeout(Duration.Inf)))
      append((Await.result(fut.map(handler), Duration.Inf)).toString)
      latch.countDown()
    }

    // test reply (back and forth communication)
    {
      val a = ActorDSL.actor(new ActWithStash {
        val msg = ("reply from an actor", 0L)
        override def preStart() = {
          respActor ! msg
        }

        def receive = {
          case a: String =>
            append(a)
            sender ! msg
            context.become {
              case a: String =>
                append(a)
                latch.countDown()
                context.stop(self)
            }
        }
      })
    }

    // test forward method
    {
      val a = ActorDSL.actor(new ActWithStash {
        val msg = ("forward from an actor", 0L)
        override def preStart() = { respActor ! msg }
        def receive = {
          case a: String =>
            append(a)
            sender forward ("forward")
            context.stop(self)
        }
      })
    }
    // output
    latch.await(10, TimeUnit.SECONDS)
    if (latch.getCount() > 0) {
      println("Error: Tasks have not finished!!!")
    }

    buff.sorted.foreach(println)
    toStop.foreach(_ ! PoisonPill)
    assertPartest()
  }
}
