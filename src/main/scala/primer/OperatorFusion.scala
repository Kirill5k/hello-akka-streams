package primer

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}

object OperatorFusion extends App {
  implicit val system = ActorSystem("OperatorFusion")
  implicit val materializer = ActorMaterializer()

  val simpleSource = Source(1 to 1000)
  val simpleFlow1 = Flow[Int].map(_ + 1)
  val simpleFlow2 = Flow[Int].map(_ * 10)
  val simpleSink = Sink.foreach[Int](println)

  // run on the same actor
//  simpleSource.via(simpleFlow1).via(simpleFlow2).to(simpleSink).run()

  val complexFlow1 = Flow[Int].map { x =>
    Thread.sleep(1000)
    x + 10
  }

  val complexFlow2 = Flow[Int].map { x =>
    Thread.sleep(1000)
    x * 10
  }

  // async boundary
//  simpleSource
//    .via(simpleFlow1).async // runs on one actor
//    .via(simpleFlow2).async // runs on another actor
//    .to(simpleSink).run() // runs on third actor

  // ordering guarantees
  Source(1 to 3)
    .map(el => { println(s"Flow A $el"); el}).async
    .map(el => { println(s"Flow B $el"); el}).async
    .map(el => { println(s"Flow C $el"); el}).async
    .runWith(Sink.ignore)
}
