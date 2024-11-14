package com.anectdot

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.model.ws.TextMessage

import scala.collection.mutable

sealed trait CommandRouterTrait
final case class NewCommand(command: Command, uniqueId: String)
    extends CommandRouterTrait
final case class RegisterWebSocketActor(
    uniqueId: String,
    box_id: Int,
    ref: ActorRef[TextMessage]
) extends CommandRouterTrait

/** The `CommandRouter` actor is responsible for routing commands within the
  * application. It acts as a central point for handling various commands and
  * directing them to the appropriate handlers.
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
      // Map of WebSocket clients and their unique ids for each box_id.
      // The key is the box_id, and the value is a map of unique ids to actor references.
      val webSocketClients =
        mutable.Map[Int, mutable.Map[String, ActorRef[TextMessage]]]()

      /** Handles incoming messages for the CommandRouter actor.
        *
        * @return
        *   Behavior of the actor.
        *
        * Handles the following messages:
        *
        *   - `RegisterWebSocketActor(box_id, ref)`: Registers a WebSocket
        *     client actor reference for the given `box_id`. Logs the
        *     registration and updates the `webSocketClients` map with the new
        *     reference.
        *
        *   - `NewCommand(command)`: Processes a new command. Retrieves or
        *     creates a `RemoteManager` actor for the given `command.box_id`. If
        *     a `RemoteManager` does not exist for the `box_id`, it logs the
        *     creation and spawns a new `RemoteManager` actor.
        *
        * @param box_id
        *   The identifier for the box associated with the WebSocket client or
        *   command.
        * @param ref
        *   The actor reference for the WebSocket client.
        * @param command
        *   The command to be processed, which contains a `box_id`.
        */
      Behaviors.receiveMessage {
        case RegisterWebSocketActor(uniqueId, box_id, ref) =>
          context.log.info(s"Registering WebSocket client with id: $uniqueId.")
          webSocketClients.get(box_id) match {
            case Some(innerMap) =>
              // Outer key exists, add to the inner map
              innerMap(uniqueId) = ref
            case None =>
              // Outer key does not exist, create a new inner map and add it
              webSocketClients(box_id) = mutable.Map(uniqueId -> ref)
          }
          Behaviors.same

        case NewCommand(command, wsUniqueId) =>
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
            case ConnectBox(box_id, uniqueId) =>
              context.log.info(s"connectbox command: $command")
              manager ! ConnectBox(box_id, wsUniqueId)

            case StartGameCommand(box_id, uniqueId) =>
              manager ! StartGameCommand(box_id, wsUniqueId)

            case StopGameCommand(box_id, uniqueId) =>
              manager ! StopGameCommand(box_id, wsUniqueId)

            case VoteCommand(box_id, remote_id, vote, uniqueId) =>
              manager ! VoteCommand(box_id, remote_id, vote, wsUniqueId)

            case ConnectRemote(box_id, remote_id, uniqueId) =>
              manager ! ConnectRemote(box_id, remote_id, wsUniqueId)

            case DisconnectRemote(box_id, remote_id, uniqueId) =>
              manager ! DisconnectRemote(box_id, remote_id, wsUniqueId)

            case _ =>
              context.log.info(s"Unknown command: $command")
          }

          Behaviors.same
      }
  }
}
