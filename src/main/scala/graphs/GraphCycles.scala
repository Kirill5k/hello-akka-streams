package graphs

import akka.actor.ActorSystem
import akka.stream.scaladsl.MergePreferred
import akka.stream.{ActorMaterializer, ClosedShape, OverflowStrategy}
import akka.stream.scaladsl.{Flow, GraphDSL, Merge, RunnableGraph, Source}

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

  RunnableGraph.fromGraph(bufferedAccelerator).run()
}
