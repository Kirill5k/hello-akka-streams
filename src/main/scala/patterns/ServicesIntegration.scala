package patterns

import java.time.{LocalDateTime, ZoneOffset}

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.Timeout

import scala.concurrent.Future

object ServicesIntegration extends App {

  implicit val system = ActorSystem("services-integration")
  implicit val materializer = ActorMaterializer()
//  import system.dispatcher // not recommended in practice; may starve the system
  implicit val dispatcher = system.dispatchers.lookup("dedicated-dispatcher")

  def genericService[A, B](element: A): Future[B] = ???

  case class PagerEvent(app: String, description: String, date: LocalDateTime)

  val eventSource = Source(List(
    PagerEvent("akka-infra", "infrastructure broke", LocalDateTime.now()),
    PagerEvent("fast-data-pipeline", "illegal elements in the data pipeline", LocalDateTime.now()),
    PagerEvent("akka-infra", "service stopped responding", LocalDateTime.now()),
    PagerEvent("super-frontend", "button doesnt work", LocalDateTime.now())
  ))

  class PagerActor extends Actor with ActorLogging {
    private val engineers = List("alice", "bob", "lady gaga")
    private val emails = Map(
      "alice" -> "alice@google.com",
      "bob" -> "bob@google.com",
      "lady gaga" -> "ladygaga@google.com",
    )

    private def processEvent(pagerEvent: PagerEvent) = {
      val engineerIndex = (pagerEvent.date.toInstant(ZoneOffset.UTC).toEpochMilli / (24 * 3600)) % engineers.length
      val engineer = engineers(engineerIndex.toInt)
      log.info(s"sending $engineer to the duty -> $pagerEvent")
      Thread.sleep(1000)
      emails(engineer)
    }

    override def receive: Receive = {
      case event: PagerEvent => sender() ! processEvent(event)
    }
  }

  object PagerService {
    private val engineers = List("alice", "bob", "lady gaga")
    private val emails = Map(
      "alice" -> "alice@google.com",
      "bob" -> "bob@google.com",
      "lady gaga" -> "ladygaga@google.com",
    )

    def processEvent(pagerEvent: PagerEvent) = Future {
      val engineerIndex = (pagerEvent.date.toInstant(ZoneOffset.UTC).toEpochMilli / (24 * 3600)) % engineers.length
      val engineer = engineers(engineerIndex.toInt)
      println(s"sending $engineer to the duty -> $pagerEvent")
      Thread.sleep(1000)
      emails(engineer)
    }
  }

  val infraEvents = eventSource.filter(_.app == "akka-infra")
  // guarantees the relative order of the elements
  val pagedEngineerEmails = infraEvents.mapAsync(parallelism = 4)(PagerService.processEvent)
  val pagedEmailsSink = Sink.foreach[String](email => println(s"successfully sent notification to $email"))

//  pagedEngineerEmails.to(pagedEmailsSink).run()


  import akka.pattern.ask
  import scala.concurrent.duration._
  implicit val timeout = Timeout(3 seconds)
  val pagerActor = system.actorOf(Props[PagerActor], "pager-actor")
  val alternativePagedEngineerEmails = infraEvents.mapAsync(parallelism = 4)(event => (pagerActor ? event).mapTo[String])
//  alternativePagedEngineerEmails.to(pagedEmailsSink).run()
}
