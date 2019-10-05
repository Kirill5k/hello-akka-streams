package graphs

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, MergePreferred, RunnableGraph, Sink, Source, ZipWith}
import akka.stream.{ActorMaterializer, ClosedShape, OverflowStrategy}

object GraphCycles extends App {
  implicit val system = ActorSystem("graph-system")
  implicit val materializer = ActorMaterializer()

  val accelerator = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val source = builder.add(Source(1 to 1000))
    val merge = builder.add(Merge[Int](2))
    val incrementer = builder.add(Flow[Int].map{x => println(s"accelerating $x"); x+1})

    source ~> merge ~> incrementer
              merge <~ incrementer
    ClosedShape
  }

  val actualAccelerator = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val source = builder.add(Source(1 to 1000))
    val merge = builder.add(MergePreferred[Int](1))
    val incrementer = builder.add(Flow[Int].map{x => println(s"accelerating $x"); x+1})

    source ~> merge ~> incrementer
    merge.preferred <~ incrementer
    ClosedShape
  }

  val bufferedAccelerator = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val source = builder.add(Source(1 to 1000))
    val merge = builder.add(Merge[Int](2))
    val repeater = builder.add(Flow[Int].buffer(10, OverflowStrategy.dropHead).map{ x => println(s"accelerating $x"); Thread.sleep(100); x+1})

    source ~> merge ~> repeater
    merge <~ repeater
    ClosedShape
  }

//  RunnableGraph.fromGraph(bufferedAccelerator).run()

  val fibGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      val source1 = Source.single[BigInt](1)
      val source2 = Source.single[BigInt](1)
      val tupler = builder.add(ZipWith[BigInt, BigInt, (BigInt, BigInt)]((a, b) => (a, b)))
      val merge = builder.add(MergePreferred[(BigInt, BigInt)](1))
      val fib = builder.add(Flow[(BigInt, BigInt)].map{case(prev, last) => (last, last+prev)})
      val broadcast = builder.add(Broadcast[(BigInt, BigInt)](2))
      val output = builder.add(Sink.foreach[(BigInt, BigInt)]{case(prev,last) => println(s"$prev"); Thread.sleep(100)})

      source1 ~> tupler.in0; tupler.out ~> merge ~> fib ~> broadcast ~> output
      source2 ~> tupler.in1;
                                           merge.preferred <~ broadcast.out(1)


      ClosedShape
    }
  )

  fibGraph.run()
}
