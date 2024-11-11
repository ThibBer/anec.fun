package com.anectdot

import akka.actor.typed.{ActorSystem, ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol._
import spray.json._
import scala.collection.mutable
import scala.io.StdIn
import scala.concurrent.Future
import scala.util.{Success, Failure}
import akka.stream.OverflowStrategy
import akka.actor.typed.scaladsl.adapter._
import akka.stream.CompletionStrategy

object Main extends JsonSupport {

  def main(args: Array[String]): Unit = {

    implicit val system: ActorSystem[CommandRouter] =
      ActorSystem(commandRouter(), "main-system")
    implicit val executionContext = system.executionContext

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

  import akka.actor.typed.ActorRef

  sealed trait CommandRouter
  final case class NewCommand(command: Command) extends CommandRouter
  final case class RegisterWebSocketActor(
      box_id: Int,
      ref: ActorRef[TextMessage]
  ) extends CommandRouter

  def commandRouter(): Behavior[CommandRouter] = Behaviors.setup { context =>
    val remoteManagers = mutable.Map[Int, ActorRef[Command]]()
    val webSocketClients = mutable.Map[Int, ActorRef[TextMessage]]()

    Behaviors.receiveMessage {
      case RegisterWebSocketActor(box_id, ref) =>
        context.log.info(s"Registering WebSocket client for box_id: $box_id")
        webSocketClients(box_id) = ref
        Behaviors.same

      case NewCommand(command) =>
        val manager = remoteManagers.getOrElseUpdate(
          command.box_id, {
            context.log.info(
              s"Creating new RemoteManager for box_id: ${command.box_id}"
            )
            // Pass the WebSocket client reference to the RemoteManager
            context.spawn(
              RemoteManager(webSocketClients),
              s"remote-manager-${command.box_id}"
            )
          }
        )

        command match {
          case StartGameCommand(box_id) =>
            manager ! StartGameCommand(box_id)

          case StopGameCommand(box_id) =>
            manager ! StopGameCommand(box_id)

          case VoteCommand(box_id, vote) =>
            manager ! VoteCommand(box_id, vote)

          case ConnectRemote(box_id, remote_id) =>
            manager ! ConnectRemote(box_id, remote_id)

          case DisconnectRemote(box_id, remote_id) =>
            manager ! DisconnectRemote(box_id, remote_id)

          case _ =>
            context.log.info(s"Unknown command: $command")
        }

        Behaviors.same
    }
  }

  // WebSocket flow with commandRouter handling and bidirectional communication
  def webSocketFlow(
      commandRouter: ActorRef[CommandRouter],
      box_id: Int
  ): Flow[Message, Message, Any] = {

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
        OverflowStrategy.backpressure
      )

    val incoming = Sink.foreach[Message] {
      case TextMessage.Strict(text) =>
        val command = text.parseJson.convertTo[Command]
        commandRouter ! NewCommand(command)
      case _ =>
    }

    Flow.fromSinkAndSourceMat(incoming, outgoing) { (_, actorRef) =>
      commandRouter ! RegisterWebSocketActor(box_id, actorRef.toTyped)
    }
  }

}
