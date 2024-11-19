package be.unamur.anecdotfun

import akka.Done
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{DateTime, StatusCode}
import akka.http.scaladsl.model.ws.{Message, TextMessage, WebSocketRequest, WebSocketUpgradeResponse}
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Keep, Sink, Source, SourceQueueWithComplete}
import spray.json.JsObject
import system.dispatcher

import scala.concurrent.Future

class WebSocketClient(address: String) {
  private val (queue: SourceQueueWithComplete[Message], source) =
    Source.queue[Message](bufferSize = 10, overflowStrategy = OverflowStrategy.backpressure).preMaterialize()

  private val incoming: Sink[Message, Future[Done]] =
    Sink.foreach[Message] {
      case message: TextMessage.Strict =>
        onReceiveMessage(message.text)
      case _ =>
    }

  private val webSocketFlow: Flow[Message, Message, Future[WebSocketUpgradeResponse]] =
    Http().webSocketClientFlow(WebSocketRequest(address))

  private val (upgradeResponse, closed) =
    source
      .viaMat(webSocketFlow)(Keep.right)
      .toMat(incoming)(Keep.both)
      .run()

  upgradeResponse.map { upgrade =>
    if (upgrade.response.status.isSuccess()) {
      onConnectionEstablished(upgrade.response.status)
    } else {
      onConnectionFailed(upgrade.response.status)
    }
  }

  closed.onComplete { _ =>
    onConnectionClosed()
  }

  def send(obj: JsObject): Unit = {
    send(obj.toString)
  }

  def send(message: String): Unit = {
    println("Send websocket message : " + message)

    val offerResult = queue.offer(TextMessage(message))
    //  offerResult.map {
    //    case QueueOfferResult.Enqueued => println("Message enqueued successfully.")
    //    case QueueOfferResult.Dropped => println("Message was dropped.")
    //    case QueueOfferResult.Failure(ex) => println(s"Failed to enqueue message: ${ex.getMessage}")
    //    case QueueOfferResult.QueueClosed => println("Queue was closed.")
    //  }
  }

  var onReceiveMessage: String => Unit = _ => {}
  var onConnectionEstablished: StatusCode => Unit = _ => {}
  var onConnectionFailed: StatusCode => Unit = _ => {}
  var onConnectionClosed: () => Unit = () => {}
}
