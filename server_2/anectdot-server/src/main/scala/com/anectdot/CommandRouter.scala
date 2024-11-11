package com.anectdot

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.model.ws.TextMessage

import scala.collection.mutable

sealed trait CommandRouterTrait
final case class NewCommand(command: Command) extends CommandRouterTrait
final case class RegisterWebSocketActor(
    box_id: Int,
    ref: ActorRef[TextMessage]
) extends CommandRouterTrait

/**
 * The `CommandRouter` actor is responsible for routing commands within the application.
 * It acts as a central point for handling various commands and directing them to the appropriate handlers.
 */
class CommandRouter {

  /** Creates a behavior for the CommandRouter actor. This method sets up the
    * actor context and initializes the behavior.
    *
    * @return
    *   The behavior for the CommandRouter actor.
    */
  def commandRouter(): Behavior[CommandRouterTrait] = Behaviors.setup {
    context =>
      val remoteManagers = mutable.Map[Int, ActorRef[Command]]()
      val webSocketClients = mutable.Map[Int, ActorRef[TextMessage]]()

      /**
       * Handles incoming messages for the CommandRouter actor.
       *
       * @return Behavior of the actor.
       *
       * Handles the following messages:
       *
       * - `RegisterWebSocketActor(box_id, ref)`: Registers a WebSocket client actor reference for the given `box_id`.
       *   Logs the registration and updates the `webSocketClients` map with the new reference.
       *
       * - `NewCommand(command)`: Processes a new command. Retrieves or creates a `RemoteManager` actor for the given `command.box_id`.
       *   If a `RemoteManager` does not exist for the `box_id`, it logs the creation and spawns a new `RemoteManager` actor.
       *
       * @param box_id The identifier for the box associated with the WebSocket client or command.
       * @param ref The actor reference for the WebSocket client.
       * @param command The command to be processed, which contains a `box_id`.
       */
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
              context.spawn(
                RemoteManager(webSocketClients),
                s"remote-manager-${command.box_id}"
              )
            }
          )

          // Routes incoming commands to the appropriate handler in the manager actor.
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
}
