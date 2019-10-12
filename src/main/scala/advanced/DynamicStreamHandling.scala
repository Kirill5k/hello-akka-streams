package advanced

import akka.actor.ActorSystem
import akka.stream.scaladsl.{BroadcastHub, Keep, MergeHub, Sink, Source}
import akka.stream.{ActorMaterializer, KillSwitches}

import scala.concurrent.duration._
import scala.language.postfixOps

object DynamicStreamHandling extends App {
  implicit val system = ActorSystem("stream-handling")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  // Kill switch
  val killSwitchFlow = KillSwitches.single[Int]

  val counter = Source(LazyList.from(1))
    .throttle(1, 1 second)
    .log("counter")

  val anotherCounter = Source(LazyList.from(1))
    .throttle(2, 1 second)
    .log("anotherCounter")

  val sink = Sink.ignore

//  val killSwitch = counter
//    .viaMat(killSwitchFlow)(Keep.right)
//    .to(sink)
//    .run()
//
//  system.scheduler.scheduleOnce(3 seconds) {
//    killSwitch.shutdown()
//  }

  val sharedKillSwitch = KillSwitches.shared("oneButtonToRuleThemAll")

  counter.via(sharedKillSwitch.flow).runWith(Sink.ignore)
  anotherCounter.via(sharedKillSwitch.flow).runWith(Sink.ignore)

  system.scheduler.scheduleOnce(3 seconds) {
    sharedKillSwitch.shutdown()
  }

  // mergehub
  val dynamicMerge = MergeHub.source[Int]
  val materializedSink = dynamicMerge.to(Sink.foreach[Int](println)).run()

  Source(1 to 10).runWith(materializedSink)
  counter.runWith(materializedSink)

  // broadcasthub
  val dynamicBroadcast = BroadcastHub.sink[Int]
  val materializedSource = Source(1 to 100).runWith(dynamicBroadcast)
  materializedSource.runWith(Sink.ignore)
  materializedSource.runWith(Sink.foreach[Int](println))

  // combining mergehub and broadcasthub
  val (publisherPort, subscriberPort) = dynamicMerge.toMat(dynamicBroadcast)(Keep.both).run()

  subscriberPort.runWith(Sink.foreach(n => println(s"I received $n")))
  subscriberPort.map(_ * 10 + 1).runWith(Sink.foreach(n => println(s"Received updated $n")))

  Source(1 to 20).runWith(publisherPort)
  Source(10 to 20).runWith(publisherPort)
  Source.single(42).runWith(publisherPort)
}
