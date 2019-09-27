package primer

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Sink, Source}

object Backpressure extends App {
  implicit val system = ActorSystem("Backpressure")
  implicit val materializer = ActorMaterializer()

  val fastSource = Source(1 to 1000)
  val slowSink = Sink.foreach[Int]{ x =>
    Thread.sleep(1000)
    println(s"slow sink $x")
  }
  val simpleFlow = Flow[Int].map { x =>
    println(s"incoming: $x")
    x + 1
  }
  val bufferedFlow = simpleFlow.buffer(10, overflowStrategy = OverflowStrategy.backpressure)

  // not backpressure
//  fastSource.to(slowSink).run()

  // backpressure
//  fastSource.async
//    .via(simpleFlow).async
//    .to(slowSink)
//    .run()

  fastSource.async
    .via(bufferedFlow).async
    .to(slowSink)
    .run()
}
