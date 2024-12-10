package com.anecdot

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.model.ws.TextMessage
import com.anecdot.DisconnectReason.LostConnection

import scala.collection.mutable

sealed trait CommandRouterTrait
final case class NewCommand(command: Command, uniqueId: String)
    extends CommandRouterTrait

final case class RegisterWebSocketActor(
    uniqueId: String,
    boxId: Int,
    ref: ActorRef[TextMessage]
) extends CommandRouterTrait

final case class Heartbeat(boxId: Int, uniqueId: String)
    extends CommandRouterTrait
final case class ClientLostConnection(boxId: Int, uniqueId: String)
    extends CommandRouterTrait

final case class GameManagerEvent(boxId: Int, event: String) extends CommandRouterTrait
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

      /** Handles incoming messages for the CommandRouter actor.
        *
        * @return
        *   Behavior of the actor.
        *
        * Handles the following messages:
        *
        *   - `RegisterWebSocketActor(boxId, ref)`: Registers a WebSocket client
        *     actor reference for the given `boxId`. Logs the registration and
        *     updates the `webSocketClients` map with the new reference.
        *
        *   - `NewCommand(command)`: Processes a new command. Retrieves or
        *     creates a `RemoteManager` actor for the given `command.boxId`. If
        *     a `RemoteManager` does not exist for the `boxId`, it logs the
        *     creation and spawns a new `RemoteManager` actor.
        *
        * @param boxId
        *   The identifier for the box associated with the WebSocket client or
        *   command.
        * @param ref
        *   The actor reference for the WebSocket client.
        * @param command
        *   The command to be processed, which contains a `boxId`.
        */
      Behaviors.receiveMessage {
        case RegisterWebSocketActor(uniqueId, boxId, ref) =>
          context.log.info(
            s"Registering WebSocket client with id: $uniqueId."
          )
          val manager = remoteManagers.getOrElseUpdate(
            boxId, {
              context.log.info(
                s"Creating new RemoteManager for boxId: ${boxId}"
              )
              context.spawn(
                GameManager(boxId, context.self),
                s"remote-manager-${boxId}"
              )
            }
          )
          manager ! AddClient(boxId, uniqueId, ref)
          Behaviors.same

        case NewCommand(command, wsUniqueId) =>
          context.log.info(
            s"${command.getClass.getSimpleName} command received for boxId: ${command.boxId}"
          )
          val manager = remoteManagers.getOrElseUpdate(
            command.boxId, {
              context.log.info(
                s"Creating new RemoteManager for boxId: ${command.boxId}"
              )
              context.spawn(
                GameManager(command.boxId,context.self),
                s"remote-manager-${command.boxId}"
              )
            }
          )

          // Routes incoming commands to the appropriate handler in the manager actor.
          command match {
            case ConnectBox(boxId, uniqueId) =>
              manager ! ConnectBox(boxId, wsUniqueId)

            case StartGameCommand(boxId, uniqueId) =>
              manager ! StartGameCommand(boxId, wsUniqueId)

            case StartRoundCommand(boxId, uniqueId) =>
              manager ! StartRoundCommand(boxId, wsUniqueId)

            case StopGameCommand(boxId, uniqueId) =>
              manager ! StopGameCommand(boxId, wsUniqueId)

            case VoteCommand(boxId, vote, uniqueId) =>
              manager ! VoteCommand(boxId, vote, wsUniqueId)

            case ConnectRemote(boxId, uniqueId, username) =>
              manager ! ConnectRemote(boxId, wsUniqueId, username)

            case DisconnectRemote(boxId, uniqueId) =>
              manager ! DisconnectRemote(boxId, wsUniqueId)

            case StartVoting(boxId, uniqueId) =>
              manager ! StartVoting(boxId, wsUniqueId)

            case VoiceFlow(boxId, uniqueId, payload) =>
              manager ! VoiceFlow(boxId, uniqueId, payload)

            case ScannedStickCommand(boxId, uniqueId, exploded) =>
              manager ! ScannedStickCommand(boxId, uniqueId, exploded)

            case ExplodedAnimationPlayed(boxId) =>
              manager ! ExplodedAnimationPlayed(boxId)

            case SetGameModeCommand(boxId, uniqueId, gameMode) =>
              manager ! SetGameModeCommand(boxId, uniqueId, gameMode)

            case RetrieveStateCommand(boxId, uniqueId) =>
              manager ! RetrieveStateCommand(boxId, uniqueId)

            case IdleGameCommand(boxId) =>
              manager ! IdleGameCommand(boxId)

            case _ =>
              context.log.info(s"Unknown command: $command")
          }
          Behaviors.same

        case Heartbeat(boxId, wsUniqueId) =>
          context.log.info(s"Heartbeat received $boxId, $wsUniqueId")
          remoteManagers.get(
            boxId
          ) foreach { manager =>
            manager ! HeartbeatClient(boxId, wsUniqueId)
          }
          Behaviors.same
        case ClientLostConnection(boxId, uniqueId) =>
          remoteManagers.get(boxId) foreach { manager =>
            manager ! ClientDisconnected(boxId, uniqueId, LostConnection)
          }
          Behaviors.same
        case GameManagerEvent(boxId, event) =>
          context.log.info(s"GameManagerEvent: $event, $boxId")
          if (event == "game-over") {
            remoteManagers.remove(boxId).foreach { manager =>
              context.stop(manager)
            }
          }
          Behaviors.same
      }
  }
}
