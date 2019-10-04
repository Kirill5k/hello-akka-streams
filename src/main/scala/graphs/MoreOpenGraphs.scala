package graphs

import java.time.Instant

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ClosedShape, FanOutShape2, UniformFanInShape}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, RunnableGraph, Sink, Source, ZipWith}

object MoreOpenGraphs extends App {
  implicit val system = ActorSystem("more-open-graphs")
  implicit val materializez = ActorMaterializer()

  val max3StaticGraph = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._
    val max1 = builder.add(ZipWith[Int, Int, Int]((a, b) => Math.max(a, b)))
    val max2 = builder.add(ZipWith[Int, Int, Int]((a, b) => Math.max(a, b)))

    max1.out ~> max2.in0

    UniformFanInShape(max2.out, max1.in0,max1.in1, max2.in1)
  }

  val source1 = Source(1 to 10)
  val source2 = Source((1 to 10).map(_ => 5))
  val source3 = Source((1 to 10).reverse)

  val maxSink = Sink.foreach[Int](x => println(s"max is $x"))

  val max3RunnableGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      val max3Shape = builder.add(max3StaticGraph)
      source1 ~> max3Shape.in(0)
      source2 ~> max3Shape.in(1)
      source3 ~> max3Shape.in(2)
      max3Shape.out ~> maxSink
      ClosedShape
    }
  )

//  max3RunnableGraph.run()

  case class Transaction(id: String, source: String, recipient: String, amount: Int, date: Instant)

  val transactionSource = Source(List(
    Transaction("23125125", "Bob", "Alice", 100, Instant.now()),
    Transaction("97873", "John", "Phil", 10000, Instant.now()),
    Transaction("97732523", "Jim", "Bob", 700, Instant.now())
  ))

  val bankProcessor = Sink.foreach[Transaction](println)
  val transactionAnalysisService = Sink.foreach[String](txnId => println(s"suspicious transaction id: $txnId"))

  val suspiciousTransactionStaticGraph = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val broadcast = builder.add(Broadcast[Transaction](2))
    val suspiciousTxnFilter = builder.add(Flow[Transaction].filter(_.amount > 1000))
    val txnIdExtranctor = builder.add(Flow[Transaction].map[String](_.id))

    broadcast.out(0) ~> suspiciousTxnFilter ~> txnIdExtranctor
    new FanOutShape2(broadcast.in, broadcast.out(1), txnIdExtranctor.out)
  }

  val suspiciousTransactionRunnableGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      val transactionProcessor = builder.add(suspiciousTransactionStaticGraph)
      transactionSource ~> transactionProcessor.in
      transactionProcessor.out0 ~> bankProcessor
      transactionProcessor.out1 ~> transactionAnalysisService
      ClosedShape
    }
  )

  suspiciousTransactionRunnableGraph.run()
}
