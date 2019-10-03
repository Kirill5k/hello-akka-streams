package graphs

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ClosedShape}
import akka.stream.scaladsl.{Balance, Broadcast, Flow, GraphDSL, Merge, RunnableGraph, Sink, Source, Zip}

import scala.concurrent.duration._
import scala.language.postfixOps

object GraphBasics extends App {
  implicit val system = ActorSystem("graph-basics")
  implicit val materializer = ActorMaterializer()

  val input = Source(1 to 1000)
  val incrementer = Flow[Int].map(_ + 1)
  val multiplier = Flow[Int].map(_ * 10)
  val output = Sink.foreach[(Int, Int)](println)

  val sink1 = Sink.foreach[Int](x => println(s"sink 1 $x"))
  val sink2 = Sink.foreach[Int](x => println(s"sink 2 $x"))

  val graph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._
      val broadcast = builder.add(Broadcast[Int](2))
      val zip = builder.add(Zip[Int, Int])
      input ~> broadcast
      broadcast.out(0) ~> incrementer ~> zip.in0
      broadcast.out(1) ~> multiplier ~> zip.in1
      zip.out ~> output
      ClosedShape
    }
  )

//  graph.run()

  val oneSourceTwoSinksGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._
      val broadcast = builder.add(Broadcast[Int](2))
      input ~> broadcast
      broadcast.out(0) ~> sink1
      broadcast.out(1) ~> sink2
      ClosedShape
    }
  )

//  oneSourceTwoSinksGraph.run()

  val fastSource = input.throttle(5, 1 second)
  val slowSource = input.throttle(2, 1 second)

  val balancedGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._
      val merge = builder.add(Merge[Int](2))
      val balance = builder.add(Balance[Int](2))
      fastSource ~> merge ~> balance ~> sink1
      slowSource ~> merge; balance ~> sink2
      ClosedShape
    }
  )

  balancedGraph.run()
}
