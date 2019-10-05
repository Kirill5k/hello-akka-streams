package graphs

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, BidiShape, ClosedShape}
import akka.stream.scaladsl.{Flow, GraphDSL, RunnableGraph, Sink, Source}

object BidirectionalFlow extends App {
  implicit val system = ActorSystem("bidirectional-flow")
  implicit val materializer = ActorMaterializer()

  def encrypt(n: Int)(text: String) = text.map(c => (c+n).toChar)
  def decrypt(n: Int)(text: String) = text.map(c => (c-n).toChar)

  val bidiCryptoStaticGraph = GraphDSL.create() { implicit builder =>
    val encrKey = 3
    val encrFlowShape = builder.add(Flow[String].map(encrypt(encrKey)))
    val decrFlowShape = builder.add(Flow[String].map(decrypt(encrKey)))
//    BidiShape(encrFlowShape.in, encrFlowShape.out, decrFlowShape.in, decrFlowShape.out)
    BidiShape.fromFlows(encrFlowShape, decrFlowShape)
  }

  val unencryptedText = List("akka", "is", "cool", "testing", "bidirectional", "flow")
  val unencryptedTextSource = Source(unencryptedText)

  val encryptedSource = Source(unencryptedText.map(encrypt(3)))

  /*
  val cryptoBidiGrapg = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val unencrSourceShape = builder.add(unencryptedTextSource)
      val encrSourceShape = builder.add(encryptedSource)
      val bidi = builder.add(bidiCryptoStaticGraph)

      val encrSinkShape = builder.add(Sink.foreach[String](s => println(s"encrypted $s")))
      val decrSinkShape = builder.add(Sink.foreach[String](s => println(s"decrypted $s")))

      unencrSourceShape ~> bidi.in1; bidi.out1 ~> encrSinkShape
      encrSourceShape <~ bidi.in2; bidi.out2 <~ decrSinkShape
      ClosedShape
    }
  )

  cryptoBidiGrapg.run()
   */
}
