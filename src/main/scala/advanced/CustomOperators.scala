package advanced

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{ActorMaterializer, Attributes, FlowShape, Inlet, Outlet, SinkShape, SourceShape}
import akka.stream.stage.{GraphStage, GraphStageLogic, GraphStageWithMaterializedValue, InHandler, OutHandler}

import scala.collection.mutable
import scala.concurrent.{Future, Promise}
import scala.util.Random

object CustomOperators extends App {
  implicit val system = ActorSystem("custom-operators")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

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
          Thread.sleep(100)
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

//  randomNumberGenerator.to(batcherSink).run()

  // custom filter flow
  class FilterFlow[T](predicate: T => Boolean) extends GraphStage[FlowShape[T, T]] {
    private val input = Inlet[T]("fliterFlowInput")
    private val output = Outlet[T]("fliterFlowOutput")
    override def shape: FlowShape[T, T] = FlowShape[T, T](input, output)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
      setHandlers(input, output, new InHandler with OutHandler {
        override def onPush(): Unit = {
          try {
            val element = grab(input)
            if (predicate(element)) {
              push(output, element)
            } else {
              pull(input)
            }
          } catch {
            case e: Throwable => failStage(e)
          }
        }

        override def onPull(): Unit = pull(input)
      })
    }
  }

  val filterFlow = Flow.fromGraph(new FilterFlow[Int](_ > 80))

//  randomNumberGenerator.via(filterFlow).to(batcherSink).run()

  // materialized values in graph stages
  class CounterFlow[T]() extends GraphStageWithMaterializedValue[FlowShape[T, T], Future[Int]] {
    private val input = Inlet[T]("counterFlowInput")
    private val output = Outlet[T]("counterFlowOutput")
    override def shape: FlowShape[T, T] = FlowShape[T, T](input, output)

    override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Future[Int]) = {
      val promise = Promise[Int]
      val logic = new GraphStageLogic(shape) {
        var counter = 0
        setHandlers(input, output, new InHandler with OutHandler {
          override def onPush(): Unit = {
            val element = grab(input)
            counter += 1
            push(output, element)
          }

          override def onPull(): Unit = pull(input)

          override def onDownstreamFinish(): Unit = {
            promise.success(counter)
            super.onDownstreamFinish()
          }

          override def onUpstreamFinish(): Unit = {
            promise.success(counter)
            super.onUpstreamFinish()
          }

          override def onUpstreamFailure(ex: Throwable): Unit = {
            promise.failure(ex)
            super.onUpstreamFailure(ex)
          }
        })
      }
      (logic, promise.future)
    }
  }
}
