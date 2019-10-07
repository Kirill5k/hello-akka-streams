package patterns

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.Timeout

import scala.concurrent.duration._
import scala.language.postfixOps

object ActorsIntegration extends App {
  implicit val system = ActorSystem("ActorsIntegration")
  implicit val materializer = ActorMaterializer()

  class SimpleActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case s: String =>
        log.info(s"received a string $s")
        sender() ! s"$s$s"
      case n: Int =>
        log.info(s"received a number $n")
        sender() ! (2 * n)
      case _ =>
    }
  }

  case object StreamInit
  case object StreamAck
  case object StreamComplete
  case class StreamFail(ex: Throwable)

  class DestinationActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case StreamInit =>
        log.info("stream init")
        sender() ! StreamAck
      case StreamComplete =>
        log.info("stream complete")
        context.stop(self)
      case StreamFail(ex) =>
        log.error(s"stream failed $ex")
      case message =>
        log.info(s"message received $message")
        sender() ! StreamAck
    }
  }

  val simpleActor = system.actorOf(Props[SimpleActor], "simple-actor")
  val destinationActor = system.actorOf(Props[DestinationActor], "destination-actor")

  val numbersSource = Source(1 to 10)
  implicit val time = Timeout(2 seconds)
  val actorBasedFlow = Flow[Int].ask[Int](parallelism = 4)(simpleActor)

//  numbersSource.via(actorBasedFlow).to(Sink.ignore).run()
//  numbersSource.ask[Int](parallelism = 4)(simpleActor).to(Sink.ignore)

  val actorPoweredSource = Source.actorRef[Int](bufferSize = 10, overflowStrategy = OverflowStrategy.dropHead)
//  val materializedActorRef = actorPoweredSource.to(Sink.foreach[Int](n => println(s"actor powered flow got number $n"))).run()
//  materializedActorRef ! 10
  // terminating stream:
//  materializedActorRef ! akka.actor.Status.Success("complete")

  /*
  Actor as destination:
  - init message
  - ack message to confirm reception
  - complete message
  - function to generate a message in case of failure
   */
  val actorPoweredSink = Sink.actorRefWithAck[Int](
    destinationActor,
    onInitMessage = StreamInit,
    ackMessage = StreamAck,
    onCompleteMessage = StreamComplete,
    onFailureMessage = throwable => StreamFail(throwable)
  )

  Source(1 to 10).to(actorPoweredSink).run()
}
