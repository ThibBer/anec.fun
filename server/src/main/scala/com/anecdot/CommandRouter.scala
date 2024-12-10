package com.anecdot

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.model.ws.TextMessage

import scala.collection.mutable
import scala.concurrent.duration._

sealed trait CommandRouterTrait
final case class NewCommand(command: Command, uniqueId: String)
    extends CommandRouterTrait

final case class RegisterWebSocketActor(
    uniqueId: String,
    boxId: Int,
    ref: ActorRef[TextMessage]
) extends CommandRouterTrait

final case class UpdateWebSocketActor(
    uniqueId: String,
    newUniqueId: String,
    boxId: Int,
    ref: ActorRef[TextMessage]
) extends CommandRouterTrait

final case class Heartbeat(boxId: Int, uniqueId: String)
    extends CommandRouterTrait
final case class ClientLostConnection(boxId: Int, uniqueId: String)
    extends CommandRouterTrait

final case class CheckInactiveClients() extends CommandRouterTrait

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
      // Map of WebSocket clients and their unique ids for each boxId.
      // The key is the boxId, and the value is a map of unique ids to actor references.
      val webSocketClients =
        mutable.Map[Int, mutable.Map[String, ActorRef[TextMessage]]]()
      val heartbeatLogs = mutable.Map[(Int, String), Long]()
      val heartbeatTimeout = 30000

      Behaviors.withTimers { timers =>

        timers.startTimerWithFixedDelay(
          "CheckInactiveClients",
          CheckInactiveClients(),
          10.seconds
        )

        /** Handles incoming messages for the CommandRouter actor.
          *
          * @return
          *   Behavior of the actor.
          *
          * Handles the following messages:
          *
          *   - `RegisterWebSocketActor(boxId, ref)`: Registers a WebSocket
          *     client actor reference for the given `boxId`. Logs the
          *     registration and updates the `webSocketClients` map with the new
          *     reference.
          *
          *   - `NewCommand(command)`: Processes a new command. Retrieves or
          *     creates a `RemoteManager` actor for the given `command.boxId`.
          *     If a `RemoteManager` does not exist for the `boxId`, it logs the
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
            webSocketClients.get(boxId) match {
              case Some(innerMap) =>
                // Outer key exists, add to the inner map
                innerMap(uniqueId) = ref
              case None =>
                // Outer key does not exist, create a new inner map and add it
                webSocketClients(boxId) = mutable.Map(uniqueId -> ref)
            }
            Behaviors.same

          case UpdateWebSocketActor(uniqueId, newUniqueId, boxId, ref) =>
            context.log.info(
              s"Updating WebSocket client with id: $uniqueId to $newUniqueId."
            )
            // Replace the old uniqueId with the new one
            webSocketClients(boxId) -= uniqueId
            webSocketClients(boxId)(newUniqueId) = ref
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
                  GameManager(webSocketClients),
                  s"remote-manager-${command.boxId}"
                )
              }
            )

            // Routes incoming commands to the appropriate handler in the manager actor.
            command match {
              case ConnectBox(boxId, uniqueId) =>
                manager ! ConnectBox(boxId, Some(wsUniqueId))

              case StartGameCommand(boxId, uniqueId) =>
                manager ! StartGameCommand(boxId, wsUniqueId)

              case StartRoundCommand(boxId, uniqueId) =>
                manager ! StartRoundCommand(boxId, wsUniqueId)

              case StopGameCommand(boxId, uniqueId) =>
                manager ! StopGameCommand(boxId, wsUniqueId)

              case VoteCommand(boxId, vote, uniqueId) =>
                manager ! VoteCommand(boxId, vote, wsUniqueId)

              case ConnectRemote(boxId, uniqueId, username) =>
                manager ! ConnectRemote(boxId, Some(wsUniqueId), username)

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
            heartbeatLogs((boxId, wsUniqueId)) = System.currentTimeMillis()
            context.log.info(s"Heartbeat received $boxId, $wsUniqueId")
            Behaviors.same
          case CheckInactiveClients() =>
            val currentTime = System.currentTimeMillis()
            val inactiveClients = heartbeatLogs.filter {
              case (_, lastHeartbeat) =>
                currentTime - lastHeartbeat > heartbeatTimeout
            }.keys
            context.log.info(
              s"Checking inactive clients: ${inactiveClients.mkString(", ")}"
            )
            inactiveClients.foreach {
              case (boxId, uniqueId) => {
                webSocketClients.get(boxId) match {
                  case Some(innerMap) =>
                    innerMap.remove(uniqueId)
                    val keyToRemove = (boxId, uniqueId)
                    heartbeatLogs.remove(keyToRemove)
                  case None => // Do nothing
                }
              }
            }
            remoteManagers
              .filter { case (boxId, manager) =>
                webSocketClients.get(boxId) match {
                  case Some(innerMap) => innerMap.isEmpty
                  case None           => true
                }
              }
              .keys
              .foreach { boxId =>
                webSocketClients.remove(boxId)
                remoteManagers.remove(boxId).foreach { manager =>
                  context.stop(manager)
                }
              }
            Behaviors.same
          case ClientLostConnection(boxId, uniqueId) =>
            val keyToRemove = (boxId, uniqueId)
            heartbeatLogs.remove(keyToRemove)
            webSocketClients.get(boxId) foreach { innerMap =>
              innerMap.remove(uniqueId)
            }
            remoteManagers.get(boxId) foreach { manager =>
              manager ! ClientDisconnected(boxId, uniqueId)
            }
            Behaviors.same
        }
      }
  }
}
