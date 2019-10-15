package advanced

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{ActorMaterializer, Attributes, Inlet, Outlet, SinkShape, SourceShape}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}

import scala.collection.mutable
import scala.util.Random

object CustomOperators extends App {
  implicit val system = ActorSystem("custom-operators")
  implicit val materializer = ActorMaterializer()

  // custom source which emits random numbers until cancelled
  class RandomNumberGenerator(max: Int) extends GraphStage[SourceShape[Int]] {
    private val outPort = Outlet[Int]("randomGenerator")
    private val random = new Random

    override def shape: SourceShape[Int] = SourceShape(outPort)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
      setHandler(outPort, new OutHandler {
        override def onPull(): Unit = push(outPort, random.nextInt(max))
      })
    }
  }

  val randomNumberGenerator = Source.fromGraph(new RandomNumberGenerator(100))
//  randomNumberGenerator.runWith(Sink.foreach(println))

  // custom sink which will print elements in batches
  class BatchSink[T](batchSize: Int) extends GraphStage[SinkShape[T]] {
    private val inport = Inlet[T]("batcher")
    override def shape: SinkShape[T] = SinkShape[T](inport)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
      private val batch = new mutable.Queue[T]

      override def preStart(): Unit = {
        pull(inport)
      }

      setHandler(inport, new InHandler {
        override def onPush(): Unit = {
          val nextElement = grab(inport)
          batch.enqueue(nextElement)
          if (batch.size >= batchSize) {
            println(s"new batch ${batch.dequeueAll(_ => true).mkString("[", ",", "]")}")
          }
          pull(inport)
        }

        override def onUpstreamFinish(): Unit = {
          if (batch.nonEmpty) {
            println(s"stream finished with batch ${batch.dequeueAll(_ => true).mkString("[", ",", "]")}")
          }
        }
      })
    }
  }

  val batcherSink = Sink.fromGraph(new BatchSink[Int](10))

  randomNumberGenerator.to(batcherSink).run()
}
