package graphs

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, FlowShape, SinkShape}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Keep, Sink, Source}

import scala.concurrent.Future
import scala.util.{Failure, Success}

object GraphMaterializedValues extends App {

  implicit val system = ActorSystem("graph-materialized-values")
  implicit val materializer = ActorMaterializer()

  val wordsSource = Source(List("akka", "is", "cool", "better", "than", "java"))
  val printer = Sink.foreach[String](println)
  val counter = Sink.fold[Int, String](0)((count, _) => count+1)

  val complexWordSink = Sink.fromGraph(
    GraphDSL.create(counter) { implicit builder => counterShape =>
      import GraphDSL.Implicits._
      val broadcast = builder.add(Broadcast[String](2))
      val lowerCaseFilter = builder.add(Flow[String].filter(word => word == word.toLowerCase))
      val shortStringFilter = builder.add(Flow[String].filter(_.length < 5))

      broadcast ~> lowerCaseFilter ~> printer
      broadcast ~> shortStringFilter ~> counterShape

      SinkShape(broadcast.in)
    }
  )

  import system.dispatcher
//  val shortStringCountFuture = wordsSource.toMat(complexWordSink)(Keep.right).run()
//  shortStringCountFuture.onComplete {
//    case Success(count) => println(s"the total number of short strings is $count")
//    case Failure(exception) => println(s"the count of short strings failed $exception")
//  }

  def enhanceFlow[A, B](flow: Flow[A, B, _]): Flow[A, B, Future[Int]] = {
    val elementCounter = Sink.fold[Int, B](0)((count, _) => count+1)
    Flow.fromGraph(GraphDSL.create(elementCounter) { implicit builder => elementCounterShape =>
      import GraphDSL.Implicits._

      val flowShape = builder.add(flow)
      val broadcast = builder.add(Broadcast[B](2))

      flowShape ~> broadcast ~> elementCounterShape

      FlowShape(flowShape.in, broadcast.out(1))
    })
  }

  val elementsCounter = Source(5 to 10).viaMat(enhanceFlow(Flow[Int].map(_ * 10)))(Keep.right).toMat(Sink.foreach[Int](println))(Keep.left).run()
  elementsCounter.onComplete {
    case Success(count) => println(s"the total number of elements is $count")
    case Failure(exception) => println(s"the count of elements failed $exception")
  }
}
