package graphs

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ClosedShape, FlowShape, SinkShape, SourceShape}
import akka.stream.scaladsl.{Broadcast, Concat, Flow, GraphDSL, RunnableGraph, Sink, Source}

object OpenGraphs extends App {
  implicit val system = ActorSystem("open-graphs")
  implicit val materializer = ActorMaterializer()

  val source1 = Source(1 to 10)
  val source2 = Source(42 to 1000)

  val sourceGraph = Source.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      val concat = builder.add(Concat[Int](2))
      source1 ~> concat
      source2 ~> concat
      SourceShape(concat.out)
    }
  )

//  sourceGraph.to(Sink.foreach[Int](println)).run()

  val sink1 = Sink.foreach[Int](x => println(s"sink 1 $x"))
  val sink2 = Sink.foreach[Int](x => println(s"sink 2 $x"))

  val sinkGraph = Sink.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val broadcast = builder.add(Broadcast[Int](2))
      broadcast ~> sink1
      broadcast ~> sink2
      SinkShape(broadcast.in)
    }
  )

//  source1.to(sinkGraph).run()

  val flow1 = Flow[Int].map(_ + 1)
  val flow2 = Flow[Int].map(_ * 10)

  val flowGraph = Flow.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      val flow1Shape = builder.add(flow1)
      val flow2Shape = builder.add(flow2)
      flow1Shape ~> flow2Shape
      FlowShape(flow1Shape.in, flow2Shape.out)
    }
  )

  source1.via(flowGraph).to(sink1).run()
}
