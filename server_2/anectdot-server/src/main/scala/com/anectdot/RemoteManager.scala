package com.anectdot

import akka.actor.typed.*
import akka.actor.typed.scaladsl.*
import akka.http.scaladsl.model.ws.TextMessage

import scala.collection.mutable

final val MIN_PLAYERS = 2

enum States:
  case STOPPED, STARTED, PAUSED, VOTING

var game_state = States.STOPPED

/**
 * The `RemoteManager` actor is the main game manager for the Anectdot game.
 */
object RemoteManager {
  def apply(
      webSocketClients: mutable.Map[Int, ActorRef[TextMessage]]
  ): Behavior[Command] =
    Behaviors.setup { context =>

      val remoteActors = mutable.Map[Int, ActorRef[Command]]()

      /**
       * Handles various commands related to the remote game management.
       *
       * Commands:
       * - `StartGameCommand(box_id)`: Starts the game if the minimum number of players is met.
       * - `StopGameCommand(box_id)`: Stops the game and clears the remote actors.
       * - `ConnectRemote(box_id, remote_id)`: Connects a remote actor and notifies the clients.
       * - `DisconnectRemote(box_id, remote_id)`: Disconnects a remote actor and notifies the clients.
       * - `VoteCommand(box_id, vote)`: Registers a vote if the game is in the VOTING state.
       *
       * @param box_id The identifier for the game box.
       * @param remote_id The identifier for the remote actor.
       * @param vote The vote cast by a player.
       *
       * @return The same behavior to handle the next message.
       */
      Behaviors.receiveMessage {
        case StartGameCommand(box_id) =>
          if (remoteActors.size < MIN_PLAYERS) {
            context.log.info(
              s"Cannot start game for box_id: $box_id, not enough players"
            )
            webSocketClients(box_id) ! TextMessage(
              s"Cannot start game for box_id: $box_id, not enough players"
            )
          } else {
            context.log.info(s"Starting the game for box_id: $box_id")
            game_state = States.STARTED
            broadcastGameState(webSocketClients, States.STARTED)
            webSocketClients(box_id) ! TextMessage(
              s"Cannot start game for box_id: $box_id, not enough players"
            )
          }
          Behaviors.same

        case StopGameCommand(box_id) =>
          context.log.info(s"Stopping the game for box_id: $box_id")
          remoteActors.clear()
          game_state = States.STOPPED
          broadcastGameState(webSocketClients, States.STOPPED)
          webSocketClients(box_id) ! TextMessage(
            s"Stopping the game for box_id: $box_id"
          )
          Behaviors.same

        case ConnectRemote(box_id, remote_id) =>
          val remoteActor = remoteActors.getOrElseUpdate(
            remote_id, {
              context.log.info(s"Remote $remote_id connected")
              webSocketClients(box_id) ! TextMessage(
                s"Remote $remote_id connected"
              )

              context.spawn(Remote(), s"remote-$remote_id")
            }
          )
          Behaviors.same

        case DisconnectRemote(box_id, remote_id) =>
          remoteActors.remove(remote_id)
          context.log.info(s"Remote $remote_id disconnected")
          game_state = States.STARTED
          webSocketClients(box_id) ! TextMessage(
            s"Remote $remote_id disconnected"
          )
          Behaviors.same

        case VoteCommand(box_id, vote) =>
          if (game_state == States.VOTING) {
            context.log.info(s"Received vote: $vote")
            webSocketClients(box_id) ! TextMessage(
              s"Received vote for box_id: $box_id, vote : $vote"
            )
            broadcastVote(webSocketClients, vote)
          } else {
            context.log.info(s"Cannot vote, game is not in VOTING state")
            webSocketClients(box_id) ! TextMessage(
              s"Cannot vote, game is not in VOTING state"
            )
          }
          Behaviors.same

        case _ =>
          Behaviors.unhandled
      }
    }

  /**
   * Broadcasts the current game state to all connected clients.
   * This method is used to ensure that all clients have the latest
   * game state information.
   */
  private def broadcastGameState(
      webSocketClients: mutable.Map[Int, ActorRef[TextMessage]],
      newState: States
  ): Unit = {
    webSocketClients.values.foreach { remote =>
      remote ! TextMessage(
        s"Game state changed to: $newState"
      )
    }
  }

  /**
   * Broadcasts the vote to all connected clients.
   * This method sends the current vote status to all clients that are
   * connected to the server, ensuring that they have the latest vote information.
   *
   * @param vote The vote information to be broadcasted.
   */
  private def broadcastVote(
      webSocketClients: mutable.Map[Int, ActorRef[TextMessage]],
      vote: String
  ): Unit = {
    webSocketClients.values.foreach { remote =>
      remote ! TextMessage(
        s"Vote received: $vote"
      )
    }
  }
}
