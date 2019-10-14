package advanced

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, Sink, Source}

import scala.util.{Failure, Success}

object Substreams extends App {
  implicit val system = ActorSystem("substreams")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  // grouping stream by function
  val wordsSource = Source(List("akka", "is", "cool", "substreams", "scala"))
  val wordsGroups = wordsSource.groupBy(10, word => if (word.isEmpty) '\u0000' else word.toLowerCase.charAt(0))

  wordsGroups.to(Sink.fold(0)((count, word) => {
    val newCount = count + 1
    println(s"received a word $word, count is $newCount")
    newCount
  }))
    .run()

  // merge substreams back
  val testSource = Source(List(
    "this is sentence 1",
    "yet another sentence",
    "and one more sentence"
  ))

  val totalCharCountFuture = testSource.groupBy(2, string => string.length % 2)
    .map(_.length)
    .mergeSubstreamsWithParallelism(2)
    .toMat(Sink.reduce[Int](_ + _))(Keep.right)
    .run()

  totalCharCountFuture.onComplete {
    case Success(value) => println(s"total char count: $value")
    case Failure(exception) => println(s"char computation failed: $exception")
  }

  // splitting a stream into substream when condition is met
  val text = "this is sentence 1\n" +
  "yet another sentence\n" +
  "and one more sentence\n"

  val anotherCharCountFuture = Source(text.toList)
    .splitWhen(_ == '\n')
    .filter(_ != '\n')
    .map(_ => 1)
    .mergeSubstreams
    .toMat(Sink.reduce[Int](_ + _))(Keep.right)
    .run()

  anotherCharCountFuture.onComplete {
    case Success(value) => println(s"another total char count: $value")
    case Failure(exception) => println(s"another char computation failed: $exception")
  }

  // flattening substreams
  val simpleSource = Source(1 to 10)
  simpleSource
    .flatMapConcat(x => Source(x to (3 * x)))
    .runWith(Sink.foreach(println))

  simpleSource
    .flatMapMerge(2, x => Source(x to (3 * x)))
    .runWith(Sink.foreach(println))
}
