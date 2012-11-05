/**
 * NOTE: Code snippets from this test are included in the Actor Migration Guide. In case you change
 * code in these tests prior to the 2.10.0 release please send the notification to @vjovanov.
 */
package scala.actors.migration
import scala.actors.Actor._
import scala.actors._
import scala.actors.migration._
import java.util.concurrent.{ TimeUnit, CountDownLatch }
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration._
import scala.concurrent.{ Promise, Await }

class LoopReact extends PartestSuite with ActorSuite {
  val checkFile = "actmig-loop-react"
  import org.junit._

  val finishedLWCR, finishedEH = Promise[Boolean]
  val finishedLWCR1, finishedEH1 = Promise[Boolean]

  def testLoopWithConditionReact() = {
    // Loop with Condition Snippet - before
    val myActor = actor {
      var c = true
      loopWhile(c) {
        react {
          case x: Int =>
            // do task
            println("do task")
            if (x == 42) {
              c = false
              finishedLWCR1.success(true)
            }
        }
      }
    }

    myActor.start()
    myActor ! 1
    myActor ! 42

    Await.ready(finishedLWCR1.future, 5 seconds)

    // Loop with Condition Snippet - migrated
    val myAkkaActor = ActorDSL.actor(new ActWithStash {

      def receive = {
        case x: Int =>
          // do task
          println("do task")
          if (x == 42) {
            finishedLWCR.success(true)
            context.stop(self)
          }
      }
    })
    myAkkaActor ! 1
    myAkkaActor ! 42
  }

  def exceptionHandling() = {
    // Stashing actor with act and exception handler
    val myActor = ActorDSL.actor(new ActWithStash {

      def receive = { case _ => println("Dummy method.") }
      override def act() = {
        loop {
          react {
            case "fail" =>
              throw new Exception("failed")
            case "work" =>
              println("working")
            case "die" =>
              finishedEH1.success(true)
              exit()
          }
        }
      }

      override def exceptionHandler = {
        case x: Exception => println("scala got exception")
      }

    })

    myActor ! "work"
    myActor ! "fail"
    myActor ! "die"

    Await.ready(finishedEH1.future, 5 seconds)
    // Stashing actor in Akka style
    val myAkkaActor = ActorDSL.actor(new ActWithStash {
      def receive = PFCatch({
        case "fail" =>
          throw new Exception("failed")
        case "work" =>
          println("working")
        case "die" =>
          finishedEH.success(true)
          context.stop(self)
      }, { case x: Exception => println("akka got exception") })
    })

    myAkkaActor ! "work"
    myAkkaActor ! "fail"
    myAkkaActor ! "die"
  }

  // As per Jim Mcbeath's blog (http://jim-mcbeath.blogspot.com/2008/07/actor-exceptions.html)
  class PFCatch(f: PartialFunction[Any, Unit],
    handler: PartialFunction[Exception, Unit])
    extends PartialFunction[Any, Unit] {

    def apply(x: Any) = {
      try {
        f(x)
      } catch {
        case e: Exception if handler.isDefinedAt(e) => handler(e)
      }
    }

    def isDefinedAt(x: Any) = f.isDefinedAt(x)
  }

  object PFCatch {
    def apply(f: PartialFunction[Any, Unit],
      handler: PartialFunction[Exception, Unit]) = new PFCatch(f, handler)
  }

  @Test(timeout = 10000)
  def test(): Unit = {
    testLoopWithConditionReact()
    Await.ready(finishedLWCR.future, 5 seconds)
    exceptionHandling()
    Await.ready(finishedEH.future, 5 seconds)
    assertPartest()
  }
}