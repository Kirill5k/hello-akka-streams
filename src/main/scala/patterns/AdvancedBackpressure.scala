package patterns

import java.time.LocalDateTime

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Sink, Source}

object AdvancedBackpressure extends App {
  implicit val system = ActorSystem("advanced-backpressure")
  implicit val materializer = ActorMaterializer()

  val controlledFlow = Flow[Int].map(_ * 2).buffer(10, OverflowStrategy.dropHead)

  case class PagerEvent(description: String, date: LocalDateTime, nInstances: Int = 1)
  case class Notification(email: String, pagerEvent: PagerEvent)

  val events = List(
    PagerEvent("service-discovery-failed", LocalDateTime.now()),
    PagerEvent("illegal-elements-in-datapipeline", LocalDateTime.now()),
    PagerEvent("number-of-http-500-spiked", LocalDateTime.now()),
    PagerEvent("service-stopped-responding", LocalDateTime.now()),
  )

  val eventsSource = Source(events)
  val oncallEngineer = "bob@google.com"

  def sendEmail(notification: Notification): Unit = {
    Thread.sleep(1000)
    println(s"dear ${notification.email}, you have an event to ${notification.pagerEvent}")
  }

  val notificationSink = Flow[PagerEvent]
    .map(event => Notification(oncallEngineer, event))
    .to(Sink.foreach[Notification](sendEmail))

  val aggregateNotificationFlow = Flow[PagerEvent]
    .conflate((event1, event2) => {
      val nInstsances = event1.nInstances + event2.nInstances
      PagerEvent(s"you have $nInstsances events requiring your attention", LocalDateTime.now(), nInstsances)
    })
    .map(event => Notification(oncallEngineer, event))

  eventsSource
    .via(aggregateNotificationFlow)
    .async
    .to(Sink.foreach[Notification](sendEmail)).run()
}
