package primer

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}

import scala.concurrent.Future
import scala.util.{Failure, Success}

object MaterializingStreams extends App {
  implicit val system = ActorSystem("MaterializingStreams")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val source = Source(1 to 10)
  val sumSink = Sink.reduce[Int](_ + _)
  val sumFuture = source.runWith(sumSink)
  sumFuture.onComplete {
    case Success(value) => println(s"the sum of all elements is $value")
    case Failure(exception) => println(s"error computing sum of all elements $exception")
  }

  val flow = Flow[Int].map(_ * 2)
  val printSink = Sink.foreach[Int](println)
//  source.viaMat(flow)((sourceMat, flowMat) => flowMat)
  val graph = source.viaMat(flow)(Keep.right).toMat(printSink)(Keep.right)
  graph.run().onComplete {
    case Success(_) => println("stream processing finished")
    case Failure(exception) => println(s"error processing stream $exception")
  }

  // return the last element out of a source
  val f11 = source.runWith(Sink.last[Int])
  val f12 = source.toMat(Sink.last[Int])(Keep.right).run()

  // compute the total word count out of a stream of sentence (map, fold, reduce)
  val sentences = List("akka is decent", "scala is cool")
  val wordCountSink = Sink.fold[Int, String](0)((count, sentence) => count + sentence.split(" ").length)

  val f21 = Source(sentences)
    .map(_.split(" ").length)
    .runWith(Sink.reduce(_ + _))
  val f22 = Source(sentences).toMat(wordCountSink)(Keep.right).run()
}
