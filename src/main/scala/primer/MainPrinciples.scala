package primer

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}

object MainPrinciples extends App {
  implicit val system = ActorSystem("first-principles")
  implicit val materializer = ActorMaterializer()

  val source = Source(1 to 10) // producer
  val sink = Sink.foreach[Int](println) // consumer
  val graph = source.to(sink)

  graph.run()

  val flow = Flow[Int].map(_ + 1)
  val sourceWithFlow = source.via(flow)
  val flowWithSink = flow.to(sink)

//  sourceWithFlow.to(sink).run()
//  source.to(flowWithSink).run()
}
