package be.unamur.anecdotfun

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives.{handleWebSocketMessages, path, pathEndOrSingleSlash}
import akka.stream.Materializer
import akka.stream.scaladsl.Flow

import scala.concurrent.ExecutionContextExecutor
import scala.io.StdIn

val address = "localhost"
val port = 8080

@main
def main(): Unit = {
  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: Materializer = Materializer(system)
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  // WebSocket handler
  val websocketHandler: Flow[Message, Message, Any] = Flow[Message].collect {
    case TextMessage.Strict(text) =>
      println(s"Incoming message : $text")
      TextMessage(s"Message received by server : $text")
  }

  // Define the WebSocket route
  val route = pathEndOrSingleSlash {
    handleWebSocketMessages(websocketHandler)
  }

  // Start the server on port 8080
  val server = Http().newServerAt(address, port).bind(route)

  println(s"Server is running at ws://$address:$port/\nPress RETURN to stop...")

  StdIn.readLine()
  server.flatMap(_.unbind()).onComplete(_ => system.terminate())
  println("Server is shut down")
}