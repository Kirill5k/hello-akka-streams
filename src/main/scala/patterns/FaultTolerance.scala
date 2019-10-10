package patterns

import akka.actor.ActorSystem
import akka.stream.Supervision.{Resume, Stop}
import akka.stream.{ActorAttributes, ActorMaterializer}
import akka.stream.scaladsl.{RestartSource, Sink, Source}

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Random

object FaultTolerance extends App {
  implicit val system = ActorSystem("fault-tolerance")
  implicit val materializer = ActorMaterializer()

  // logging
  val faultySource = Source(1 to 10).map(e => if (e == 6) throw new RuntimeException else e)
  faultySource
    .log("trackingElements")
    .to(Sink.ignore)
//    .run()

  // gracefully terminating a stream
  faultySource.recover {
    case _: RuntimeException => Int.MinValue
  }.log("gracefulSource").to(Sink.ignore)
//      .run()

  // recover with another stream
  faultySource.recoverWithRetries(3, {
    case _: RuntimeException => Source(90 to 99)
  }).log("faultySource").to(Sink.ignore)
//    .run()

  // backoff supervision
  val restartSource = RestartSource.onFailuresWithBackoff(
    minBackoff = 1 second,
    maxBackoff = 30 seconds,
    randomFactor = 0.2
  )(() => {
    val randonNumber = new Random().nextInt(19)
    Source(1 to 10).map(e => if (e == randonNumber) throw new RuntimeException else e)
  }).log("restartBackoffSource").to(Sink.ignore)
//    .run()

  // supervision strategy
  val supervisedSource = Source(1 to 20)
    .map(e => if (e == 13) throw new RuntimeException else e)
    .log("supervisedSource")
    .withAttributes(ActorAttributes.supervisionStrategy {
      case _: RuntimeException => Resume
      case _ => Stop
    })
    .to(Sink.ignore)
    .run()

}
