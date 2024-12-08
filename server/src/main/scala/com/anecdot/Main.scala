package com.anecdot

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.Message
import akka.http.scaladsl.model.ws.TextMessage
import akka.http.scaladsl.server.Directives._
import akka.stream.CompletionStrategy
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import org.slf4j.LoggerFactory
import spray.json._

import java.util.UUID
import scala.concurrent.Await
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.Duration

private val logger = LoggerFactory.getLogger(getClass)
private val jsonRegex = """\{.*}""".r

object Main extends JsonCommandSupport {

  def main(args: Array[String]): Unit = {
    val commandRouter = new CommandRouter()
    implicit val system: ActorSystem[CommandRouterTrait] =
      ActorSystem(commandRouter.commandRouter(), "main-system")
    implicit val executionContext: ExecutionContextExecutor =
      system.executionContext

    val route = path("ws" / IntNumber) { boxId =>
      parameters("uniqueId".optional) { uniqueId =>
        handleWebSocketMessages(webSocketFlow(system, boxId, uniqueId))
      }
    }

    val bindingFuture = Http().newServerAt("0.0.0.0", 8080).bind(route)

    logger.info("Server now online at ws://localhost:8080/ws")
    Await.result(system.whenTerminated, Duration.Inf)

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
      boxId: Int,
      id: Option[String]
  )(implicit system: ActorSystem[?]): Flow[Message, Message, Any] = {
    var uniqueId: String = ""

    id match {
      case Some(value) =>
        uniqueId = value
        logger.info(s"Reuse unique id $value")
      case None =>
        uniqueId = UUID.randomUUID().toString
        logger.info(s"Generate new uniqueId $uniqueId")
    }

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
      case TextMessage.Strict("heartbeat") => commandRouter ! Heartbeat(boxId, uniqueId)
      case TextMessage.Strict(text @ jsonRegex()) =>
        logger.info(s"Received message: $text")

        try {
          val command = text.parseJson.convertTo[Command]
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
        commandRouter ! RegisterWebSocketActor(uniqueId, boxId, actorRef.toTyped)

        val response = CommandResponse(uniqueId, "Connection", ResponseState.SUCCESS)
        actorRef ! TextMessage(response.toJson.compactPrint)
        actorRef
      }
      .watchTermination() { (_, termination) =>
        termination.onComplete { _ =>
          logger.info(s"WebSocket connection closed for $uniqueId.")
          commandRouter ! ClientLostConnection(boxId, uniqueId)
        }(system.executionContext)
      }
  }
}
