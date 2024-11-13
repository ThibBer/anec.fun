package com.anectdot

import akka.actor.typed.*
import akka.actor.typed.scaladsl.*
import akka.http.scaladsl.model.ws.TextMessage
import spray.json._
import scala.collection.mutable
import com.anectdot.Main.CommandJsonFormat

val MIN_PLAYERS = 2

enum States:
  case STOPPED, STARTED, PAUSED, VOTING

var game_state = States.STOPPED

/** The `RemoteManager` actor is the main game manager for the Anectdot game.
  */
object RemoteManager {
  def apply(
      webSocketClients: mutable.Map[String, ActorRef[TextMessage]]
  ): Behavior[Command] =
    Behaviors.setup { context =>

      val remoteActors = mutable.Map[Int, ActorRef[Command]]()
      var boxActor: ActorRef[Command] = null
      var voteNumber: Int = 0
      var scores = mutable.Map[Int, Int]()

      /** Handles various commands related to the remote game management.
        *
        * Commands:
        *   - `StartGameCommand(box_id)`: Starts the game if the minimum number
        *     of players is met.
        *   - `StopGameCommand(box_id)`: Stops the game and clears the remote
        *     actors.
        *   - `ConnectRemote(box_id, remote_id)`: Connects a remote actor and
        *     notifies the clients.
        *   - `DisconnectRemote(box_id, remote_id)`: Disconnects a remote actor
        *     and notifies the clients.
        *   - `VoteCommand(box_id, vote)`: Registers a vote if the game is in
        *     the VOTING state.
        *
        * @param box_id
        *   The identifier for the game box.
        * @param remote_id
        *   The identifier for the remote actor.
        * @param vote
        *   The vote cast by a player.
        *
        * @return
        *   The same behavior to handle the next message.
        */
      Behaviors.receiveMessage {
        case StartGameCommand(box_id, uniqueId) =>
          if (boxActor == null) {
            webSocketClients(uniqueId) ! TextMessage(
              s"Cannot start game for box_id: $box_id, box is not connected"
            )
          } else if (remoteActors.size < MIN_PLAYERS) {
            context.log.info(
              s"Cannot start game for box_id: $box_id, not enough players"
            )
            webSocketClients(uniqueId) ! TextMessage(
              s"Cannot start game for box_id: $box_id, not enough players"
            )
          } else {
            val command: Command = StartGameCommand(box_id, uniqueId)
            val commandJson = command.toJson.prettyPrint
            webSocketClients(uniqueId) ! TextMessage(commandJson)
            context.log.info(s"Starting the game for box_id: $box_id")
            game_state = States.STARTED
            broadcastGameState(webSocketClients, States.STARTED)
          }
          Behaviors.same

        case StopGameCommand(box_id, uniqueId) =>
          if (game_state == States.STARTED || game_state == States.PAUSED) {
            context.log.info(s"Stopping the game for box_id: $box_id")
            remoteActors.clear()
            scores.clear()
            game_state = States.STOPPED
            broadcastGameState(webSocketClients, States.STOPPED)
            // TODO also broadcast winner to all clients
          } else {
            context.log.info(
              s"Cannot stop game for box_id: $box_id, game is not in STARTED or PAUSED state"
            )
            webSocketClients(uniqueId) ! TextMessage(
              s"Cannot stop game for box_id: $box_id, game is not in STARTED or PAUSED state"
            )
          }

          Behaviors.same

        case ConnectBox(box_id, uniqueId) =>
          if (boxActor != null) {
            webSocketClients(uniqueId) ! TextMessage(
              s"A box with id $box_id is already in use"
            )
          } else {
            boxActor = context.self
            webSocketClients(uniqueId) ! TextMessage(
              s"Box with id $box_id connected"
            )
          }

          Behaviors.same

        case ConnectRemote(box_id, remote_id, uniqueId) =>
          if (boxActor != null) {
            webSocketClients(uniqueId) ! TextMessage(
              s"Cannot connect remote for box_id: $box_id, box is not connected"
            )
          } else if (game_state != States.STARTED) {
            webSocketClients(uniqueId) ! TextMessage(
              s"Cannot connect remote for box_id: $box_id, game is not in STARTED state"
            )
          } else if (remoteActors.contains(remote_id)) {
            webSocketClients(uniqueId) ! TextMessage(
              s"Remote $remote_id is already connected"
            )
          } else {
            val remoteActor = remoteActors.getOrElseUpdate(
              remote_id, {
                context.log.info(s"Remote $remote_id connected")
                webSocketClients(uniqueId) ! TextMessage(
                  s"Remote $remote_id connected"
                )

                context.spawn(Remote(), s"remote-$remote_id")
              }
            )
          }

          Behaviors.same

        case DisconnectRemote(box_id, remote_id, uniqueId) =>
          if (!remoteActors.contains(remote_id)) {
            webSocketClients(uniqueId) ! TextMessage(
              s"Remote $remote_id is not connected"
            )
          } else {
            remoteActors.remove(remote_id)
            context.log.info(s"Remote $remote_id disconnected")
            game_state = States.STARTED
            webSocketClients(uniqueId) ! TextMessage(
              s"Remote $remote_id disconnected"
            )
          }

          Behaviors.same

        case StartVoting(box_id, uniqueId) =>
          if (game_state == States.STARTED) {
            context.log.info(s"Starting voting for box_id: $box_id")
            game_state = States.VOTING
            broadcastGameState(webSocketClients, States.VOTING)
          } else {
            context.log.info(
              s"Cannot start voting for box_id: $box_id, game is not in STARTED state"
            )
            webSocketClients(uniqueId) ! TextMessage(
              s"Cannot start voting for box_id: $box_id, game is not in STARTED state"
            )
          }
          Behaviors.same

        case VoteCommand(box_id, remote_id, vote, uniqueId) =>
          if (game_state == States.VOTING) {
            context.log.info(s"Received vote: $vote")
            broadcastVote(webSocketClients, vote)
            voteNumber += 1
            if (voteNumber == remoteActors.size) {
              game_state = States.STARTED
              broadcastGameState(webSocketClients, States.STARTED)
              // update the score of each remote
              scores(remote_id) = scores.getOrElse(remote_id, 0) + 1
              // TODO send broadcast command to all clients to tell the vote result
            }
          } else {
            context.log.info(s"Cannot vote, game is not in VOTING state")
            webSocketClients(uniqueId) ! TextMessage(
              s"Cannot vote, game is not in VOTING state"
            )
          }

          Behaviors.same

        case _ =>
          Behaviors.unhandled
      }
    }

  /** Broadcasts the current game state to all connected clients. This method is
    * used to ensure that all clients have the latest game state information.
    */
  private def broadcastGameState(
      webSocketClients: mutable.Map[String, ActorRef[TextMessage]],
      newState: States
  ): Unit = {
    webSocketClients.values.foreach { remote =>
      remote ! TextMessage(
        s"Game state changed to: $newState"
      )
    }
  }

  /** Broadcasts the vote to all connected clients. This method sends the
    * current vote status to all clients that are connected to the server,
    * ensuring that they have the latest vote information.
    *
    * @param vote
    *   The vote information to be broadcasted.
    */
  private def broadcastVote(
      webSocketClients: mutable.Map[String, ActorRef[TextMessage]],
      vote: String
  ): Unit = {
    webSocketClients.values.foreach { remote =>
      remote ! TextMessage(
        s"Vote received: $vote"
      )
    }
  }
}
