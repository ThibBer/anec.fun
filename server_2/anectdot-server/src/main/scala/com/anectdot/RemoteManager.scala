package com.anectdot

import akka.actor.typed._
import akka.actor.typed.scaladsl._
import akka.http.scaladsl.model.ws.TextMessage
import com.anectdot.Main.commandResponseFormat
import spray.json._

import scala.collection.mutable

enum States:
  case STOPPED, STARTED, PAUSED, VOTING

var game_state = States.STOPPED

/** The `RemoteManager` actor is the main game manager for the Anectdot game.
  */
object RemoteManager {
  def apply(
      webSocketClients: mutable.Map[Int, mutable.Map[String, ActorRef[
        TextMessage
      ]]]
  ): Behavior[Command] =
    Behaviors.setup { context =>
      val MIN_PLAYERS: Int = 2
      val remoteActors = mutable.Map[Int, ActorRef[Command]]()
      var boxActor: Option[ActorRef[Command]] = None
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
        case ConnectBox(box_id, uniqueId) =>
          boxActor match {
            case None => {
              val response = CommandResponse(
                uniqueId,
                "StartGameCommand",
                "failed",
                Some("box is not connected")
              )
              webSocketClients(box_id)(uniqueId) ! TextMessage(
                response.toJson.compactPrint
              )
            }
            case Some(_) => {
              boxActor = Some(context.self)
              val response =
                CommandResponse(uniqueId, "StartGameCommand", "success")
              webSocketClients(box_id)(uniqueId) ! TextMessage(
                response.toJson.compactPrint
              )
            }
          }
          Behaviors.same

        case StartGameCommand(box_id, uniqueId) =>
          if (boxActor == None) {
            val response = CommandResponse(
              uniqueId,
              "StartGameCommand",
              "failed",
              Some("box is not connected")
            )
            webSocketClients(box_id)(uniqueId) ! TextMessage(
              response.toJson.compactPrint
            )
          } else if (remoteActors.size < MIN_PLAYERS) {
            val response = CommandResponse(
              uniqueId,
              "StartGameCommand",
              "failed",
              Some("not enough players")
            )
            webSocketClients(box_id)(uniqueId) ! TextMessage(
              response.toJson.compactPrint
            )
          } else {
            context.log.info(s"Starting the game for box_id: $box_id")
            val response =
              CommandResponse(uniqueId, "StartGameCommand", "success")
            webSocketClients(box_id)(uniqueId) ! TextMessage(
              response.toJson.compactPrint
            )
            game_state = States.STARTED
            // start game bomb timer
            broadcastGameState(webSocketClients, States.STARTED, box_id)
          }
          Behaviors.same

        case StartVoting(box_id, uniqueId) =>
          if (game_state == States.STARTED) {
            game_state = States.VOTING
            broadcastGameState(webSocketClients, States.VOTING, box_id)
          } else {
            val response = CommandResponse(
              uniqueId,
              "StartVoting",
              "failed",
              Some("game is not in STARTED state")
            )
            webSocketClients(box_id)(uniqueId) ! TextMessage(
              response.toJson.compactPrint
            )
          }
          Behaviors.same

        case StopGameCommand(box_id, uniqueId) =>
          if (game_state == States.STARTED || game_state == States.PAUSED) {
            context.log.info(s"Stopping the game for box_id: $box_id")
            remoteActors.clear()
            scores.clear()
            game_state = States.STOPPED
            broadcastGameState(webSocketClients, States.STOPPED, box_id)
            // TODO also broadcast winner to all clients
          } else {
            val response = CommandResponse(
              uniqueId,
              "StopGameCommand",
              "failed",
              Some("game is not in STARTED or PAUSED state")
            )
            webSocketClients(box_id)(uniqueId) ! TextMessage(
              response.toJson.compactPrint
            )
          }
          Behaviors.same

        case ConnectRemote(box_id, remote_id, uniqueId) =>
          boxActor match {
            case Some(_) =>
              val response = CommandResponse(
                uniqueId,
                "ConnectRemote",
                "failed",
                Some("box is not connected")
              )
              webSocketClients(box_id)(uniqueId) ! TextMessage(
                response.toJson.compactPrint
              )
            case None if game_state != States.STARTED =>
              val response = CommandResponse(
                uniqueId,
                "ConnectRemote",
                "failed",
                Some("game is not in STARTED state")
              )
              webSocketClients(box_id)(uniqueId) ! TextMessage(
                response.toJson.compactPrint
              )
            case None if remoteActors.contains(remote_id) =>
              val response = CommandResponse(
                uniqueId,
                "ConnectRemote",
                "failed",
                Some("remote is already connected")
              )
              webSocketClients(box_id)(uniqueId) ! TextMessage(
                response.toJson.compactPrint
              )
            case None =>
              val response = CommandResponse(
                uniqueId,
                "ConnectRemote",
                "failed",
                Some("unexpected error")
              )
              webSocketClients(box_id)(uniqueId) ! TextMessage(
                response.toJson.compactPrint
              )
          }
          Behaviors.same

        case VoteCommand(box_id, remote_id, vote, uniqueId) =>
          if (game_state == States.VOTING) {
            context.log.info(s"Received vote: $vote")
            broadcastVote(webSocketClients, vote, box_id)
            voteNumber += 1
            if (voteNumber == remoteActors.size) {
              game_state = States.STARTED
              broadcastGameState(webSocketClients, States.STARTED, box_id)
              // update the score of each remote
              scores(remote_id) = scores.getOrElse(remote_id, 0) + 1
              // TODO send broadcast command to all clients to tell the vote result
            }
          } else {
            context.log.info("Cannot vote, game is not in VOTING state")
            webSocketClients(box_id)(uniqueId) ! TextMessage(
              "Cannot vote, game is not in VOTING state"
            )
          }
          Behaviors.same

        case DisconnectRemote(box_id, remote_id, uniqueId) =>
          if (!remoteActors.contains(remote_id)) {
            webSocketClients(box_id)(uniqueId) ! TextMessage(
              s"Remote $remote_id is not connected"
            )
          } else {
            remoteActors.remove(remote_id)
            context.log.info(s"Remote $remote_id disconnected")
            game_state = States.STARTED
            webSocketClients(box_id)(uniqueId) ! TextMessage(
              s"Remote $remote_id disconnected"
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
      webSocketClients: mutable.Map[Int, mutable.Map[String, ActorRef[
        TextMessage
      ]]],
      newState: States,
      box_id: Int
  ): Unit = {
    webSocketClients(box_id).foreach {
      case (uniqueId, remote) => {
        val response = CommandResponse(uniqueId, "StartVoting", "success")
        remote ! TextMessage(response.toJson.compactPrint)
      }
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
      webSocketClients: mutable.Map[Int, mutable.Map[String, ActorRef[
        TextMessage
      ]]],
      vote: String,
      box_id: Int
  ): Unit = {
    webSocketClients(box_id).foreach {
      case (uniqueId, remote) => {
        val response = CommandResponse(uniqueId, "VoteCommand", "success")
        remote ! TextMessage(response.toJson.compactPrint)
      }
    }
  }
}
