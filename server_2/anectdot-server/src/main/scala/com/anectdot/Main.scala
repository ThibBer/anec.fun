package com.anectdot

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.Message
import akka.http.scaladsl.model.ws.TextMessage
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.CompletionStrategy
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import org.slf4j.LoggerFactory
import spray.json._

import java.util.UUID
import scala.concurrent.ExecutionContextExecutor
import scala.io.StdIn

object Main extends JsonSupport {
  private val logger = LoggerFactory.getLogger(getClass)
  def main(args: Array[String]): Unit = {
    val commandRouter = new CommandRouter()
    implicit val system: ActorSystem[CommandRouterTrait] =
      ActorSystem(commandRouter.commandRouter(), "main-system")
    implicit val executionContext: ExecutionContextExecutor =
      system.executionContext

    val route: Route =
      path("ws" / IntNumber) { boxId =>
        handleWebSocketMessages(webSocketFlow(system, boxId))
      }

    val bindingFuture = Http().newServerAt("localhost", 8080).bind(route)

    logger.info(
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
      boxId: Int
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
          logger.warn(s"Invalid json input data ($text)")
        } else {
          val command = text.parseJson.convertTo[Command]
          commandRouter ! NewCommand(command, uniqueId)
        }
      case _ =>
    }

    Flow.fromSinkAndSourceMat(incoming, outgoing) { (_, actorRef) =>
      commandRouter ! RegisterWebSocketActor(uniqueId, boxId, actorRef.toTyped)
      val response =
        CommandResponse(uniqueId, "Connection", "success")
      actorRef ! TextMessage(
        response.toJson.compactPrint
      )
    }
  }
}
