package primer

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

object Backpressure extends App {
  implicit val system = ActorSystem("Backpressure")
  implicit val materializer = ActorMaterializer()
}
