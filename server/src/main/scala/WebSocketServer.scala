package be.unamur.anecdotfun

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, TextMessage}
// import akka.http.scaladsl.server.Directives.{handleWebSocketMessages, pathEndOrSingleSlash}
import akka.http.scaladsl.server.Directives._
import akka.stream.Materializer
import akka.stream.scaladsl.Flow

// import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.io.StdIn
import controllers.WebSocketController

class WebSocketServer(implicit
    val system: ActorSystem,
    val materializer: Materializer,
    val executionContext: ExecutionContextExecutor
) {

  // Handler WebSocket
  val websocketHandler: Flow[Message, Message, Any] =
    WebSocketController.websocketFlow

  // Define the route
  val route = pathEndOrSingleSlash {
    handleWebSocketMessages(websocketHandler)
  }

  private var bindingFuture: Future[Http.ServerBinding] = _

  // Start server
  def start(address: String, port: Int): Unit = {
    bindingFuture = Http().newServerAt(address, port).bind(route)
    bindingFuture.foreach { binding =>
      println(s"WebSocket server started at ws://$address:$port/")
    }
  }

  // Stop server
  def stop(): Unit = {
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => println("WebSocket server stopped."))
  }
}
