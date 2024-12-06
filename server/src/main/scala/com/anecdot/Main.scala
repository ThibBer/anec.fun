package com.anecdot

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

private val logger = LoggerFactory.getLogger(getClass)
private val jsonRegex = """\{.*}""".r

object Main extends JsonCommandSupport {

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

    val bindingFuture = Http().newServerAt("0.0.0.0", 8080).bind(route)

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
  private def webSocketFlow(
      commandRouter: ActorRef[CommandRouterTrait],
      boxId: Int
  )(implicit system: ActorSystem[?]): Flow[Message, Message, Any] = {

    var uniqueId = UUID.randomUUID().toString

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
      case TextMessage.Strict("heartbeat") => // Keep connection alive
      case TextMessage.Strict(text @ jsonRegex()) =>
        logger.info(s"Received message: $text")
        try {
          val command = text.parseJson.convertTo[Command]
          command match {
            case ConnectBox(_, Some(providedUniqueId)) =>
              uniqueId = providedUniqueId.asInstanceOf[String]
              logger.info(s"Reusing provided uniqueId: $uniqueId")
            case ConnectBox(_, None) =>
              logger.info(
                s"New connection, using generated uniqueId: $uniqueId"
              )
            case ConnectRemote(boxId, Some(providedUniqueId), username) =>
              uniqueId = providedUniqueId
              logger.info(s"Reusing provided uniqueId: $uniqueId")
            case ConnectRemote(boxId, None, username) =>
              logger.info(
                s"New connection, using generated uniqueId: $uniqueId"
              )
            case _ => {
              logger.info(s"Received command: $command")
            }

          }
          commandRouter ! NewCommand(command, uniqueId)
        } catch {
          case e: Exception => logger.error(e.getMessage)
        }
      case TextMessage.Strict(text) =>
        logger.info(s"Ignored message: $text")
      case _ =>
    }

    // Apply watchTermination directly to the Flow
    Flow
      .fromSinkAndSourceMat(incoming, outgoing) { (_, actorRef) =>
        commandRouter ! RegisterWebSocketActor(
          uniqueId,
          boxId,
          actorRef.toTyped
        )
        val response =
          CommandResponse(uniqueId, "Connection", ResponseState.SUCCESS)
        actorRef ! TextMessage(response.toJson.compactPrint)
        actorRef
      }
      .watchTermination() { (_, termination) =>
        termination.onComplete { _ =>
          logger.info("WebSocket connection closed.")
          commandRouter ! NewCommand(ClientDisconnected(boxId, uniqueId), uniqueId)
        }(system.executionContext)
      }
  }
}
