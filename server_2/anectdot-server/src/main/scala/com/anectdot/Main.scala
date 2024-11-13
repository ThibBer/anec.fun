package com.anectdot

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.adapter.*
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.Message
import akka.http.scaladsl.model.ws.TextMessage
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.Route
import akka.stream.CompletionStrategy
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import spray.json.*

import scala.concurrent.ExecutionContextExecutor
import scala.io.StdIn
import java.util.UUID

object Main extends JsonSupport {

  def main(args: Array[String]): Unit = {
    val commandRouter = new CommandRouter()
    implicit val system: ActorSystem[CommandRouterTrait] = ActorSystem(commandRouter.commandRouter(), "main-system")
    implicit val executionContext: ExecutionContextExecutor = system.executionContext

    val route: Route =
      path("ws" / IntNumber) { box_id =>
        handleWebSocketMessages(webSocketFlow(system, box_id))
      }

    val bindingFuture = Http().newServerAt("localhost", 8080).bind(route)

    println(
      "Server now online at ws://localhost:8080/ws\nPress RETURN to stop..."
    )
    StdIn.readLine()
    bindingFuture.flatMap(_.unbind()).onComplete(_ => system.terminate())
  }

  /** Creates a WebSocket flow.
    *
    * This method sets up the WebSocket flow for handling WebSocket connections.
    * It defines how messages are processed and what actions are taken when a
    * WebSocket connection is established, receives messages, or is closed.
    *
    * @return
    *   The WebSocket flow.
    */
  def webSocketFlow(
      commandRouter: ActorRef[CommandRouterTrait],
      box_id: Int
  ): Flow[Message, Message, Any] = {
    val uniqueId = UUID.randomUUID().toString
    val completionMatcher: PartialFunction[Any, CompletionStrategy] = {
      case "complete" => CompletionStrategy.draining
    }

    val failureMatcher: PartialFunction[Any, Throwable] = { case "fail" =>
      new RuntimeException("Stream failed")
    }

    val outgoing =
      Source.actorRef[TextMessage](
        completionMatcher,
        failureMatcher,
        bufferSize = 10,
        OverflowStrategy.fail
      )

    val incoming = Sink.foreach[Message] {
      case TextMessage.Strict(text) =>
        if (!text.startsWith("{") || !text.endsWith("}")) {
          println(s"WARN : invalid json input data ($text)")
        } else {
          val command = text.parseJson.convertTo[Command]
          commandRouter ! NewCommand(command, uniqueId)
        }
      case _ =>
    }

    Flow.fromSinkAndSourceMat(incoming, outgoing) { (_, actorRef) =>
      commandRouter ! RegisterWebSocketActor(uniqueId, actorRef.toTyped)
      (incoming, actorRef)
    }
  }
}
