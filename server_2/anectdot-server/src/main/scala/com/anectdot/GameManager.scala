package com.anectdot

import akka.actor.typed._
import akka.actor.typed.scaladsl._
import akka.http.scaladsl.model.ws.TextMessage
import com.anectdot.Main.commandResponseFormat
import spray.json._

import scala.collection.mutable

enum States:
  case STOPPED, STARTED, PAUSED, VOTING

/** The `RemoteManager` actor is the main game manager for the Anectdot game.
  */
object GameManager {
  def apply(
      webSocketClients: mutable.Map[Int, mutable.Map[String, ActorRef[
        TextMessage
      ]]]
  ): Behavior[Command] =
    Behaviors.setup { context =>
      val minPlayers: Int = 2
      var remoteWebSocketActors = Set[String]()
      var boxActor: Option[ActorRef[Command]] = None
      var voteNumber: Int = 0
      val scores = mutable.Map[String, Int]()
      var gameState = States.STOPPED

      /** Processes commands for managing the remote game session.
        *
        * Supported Commands:
        *   - `StartGameCommand(boxId, uniqueId)`: Initiates the game if the
        *     required number of players are connected.
        *   - `StopGameCommand(boxId, uniqueId)`: Ends the game and resets the
        *     state of remote actors.
        *   - `ConnectRemote(boxId, uniqueId)`: Adds a remote actor and updates
        *     clients of the connection.
        *   - `DisconnectRemote(boxId, uniqueId)`: Removes a remote actor and
        *     informs clients of the disconnection.
        *   - `VoteCommand(boxId, vote, uniqueId)`: Accepts a vote when the
        *     game is in the VOTING phase.
        *
        * @param boxId
        *   The identifier for the game session.
        * @param uniqueId
        *   The unique identifier for the remote actor or command.
        * @param vote
        *   The player's vote to be recorded.
        *
        * @return
        *   The behavior for processing subsequent messages.
        */
      Behaviors.receiveMessage {
        case ConnectBox(boxId, uniqueId) =>
          boxActor match {
            case None => {
              boxActor = Some(context.self)
              val response =
                CommandResponse(uniqueId, "ConnectBox", "success")
              webSocketClients(boxId)(uniqueId) ! TextMessage(
                response.toJson.compactPrint
              )
            }
            case Some(_) => {
              val response = CommandResponse(
                uniqueId,
                "ConnectBox",
                "failed",
                Some(s"box $boxId is already in used")
              )
              webSocketClients(boxId)(uniqueId) ! TextMessage(
                response.toJson.compactPrint
              )
            }
          }
          Behaviors.same

        case StartGameCommand(boxId, uniqueId) =>
          if (boxActor == None) {
            val response = CommandResponse(
              uniqueId,
              "StartGameCommand",
              "failed",
              Some("box is not connected")
            )
            webSocketClients(boxId)(uniqueId) ! TextMessage(
              response.toJson.compactPrint
            )
          } else if (remoteWebSocketActors.size < minPlayers) {
            val response = CommandResponse(
              uniqueId,
              "StartGameCommand",
              "failed",
              Some("not enough players")
            )
            webSocketClients(boxId)(uniqueId) ! TextMessage(
              response.toJson.compactPrint
            )
          } else {
            val response =
              CommandResponse(uniqueId, "StartGameCommand", "success")
            webSocketClients(boxId)(uniqueId) ! TextMessage(
              response.toJson.compactPrint
            )
            gameState = States.STARTED
            // start game bomb timer
            broadcastGameState(webSocketClients, States.STARTED, boxId)
          }
          Behaviors.same

        case StartVoting(boxId, uniqueId) =>
          if (gameState == States.STARTED) {
            gameState = States.VOTING
            broadcastGameState(webSocketClients, States.VOTING, boxId)
          } else {
            val response = CommandResponse(
              uniqueId,
              "StartVoting",
              "failed",
              Some("game is not in STARTED state")
            )
            webSocketClients(boxId)(uniqueId) ! TextMessage(
              response.toJson.compactPrint
            )
          }
          Behaviors.same

        case StopGameCommand(boxId, uniqueId) =>
          if (gameState == States.STARTED || gameState == States.PAUSED) {
            remoteWebSocketActors = Set.empty[String]
            scores.clear()
            gameState = States.STOPPED
            broadcastGameState(webSocketClients, States.STOPPED, boxId)
            // TODO also broadcast winner to all clients
          } else {
            val response = CommandResponse(
              uniqueId,
              "StopGameCommand",
              "failed",
              Some("game is not in STARTED or PAUSED state")
            )
            webSocketClients(boxId)(uniqueId) ! TextMessage(
              response.toJson.compactPrint
            )
          }
          Behaviors.same

        case ConnectRemote(boxId, uniqueId) =>
          boxActor match {
            case None =>
              val response = CommandResponse(
                uniqueId,
                "ConnectRemote",
                "failed",
                Some("box is not connected")
              )
              webSocketClients(boxId)(uniqueId) ! TextMessage(
                response.toJson.compactPrint
              )
            case Some(_) if remoteWebSocketActors.contains(uniqueId) =>
              val response = CommandResponse(
                uniqueId,
                "ConnectRemote",
                "failed",
                Some("remote is already connected")
              )
              webSocketClients(boxId)(uniqueId) ! TextMessage(
                response.toJson.compactPrint
              )
            case Some(_) =>
              val response = CommandResponse(
                uniqueId,
                "ConnectRemote",
                "success"
              )
              webSocketClients(boxId)(uniqueId) ! TextMessage(
                response.toJson.compactPrint
              )
              remoteWebSocketActors += uniqueId
          }
          Behaviors.same

        case VoteCommand(boxId, vote, uniqueId) =>
          if (gameState == States.VOTING) {
            context.log.info(s"Received vote: $vote")
            broadcastVote(webSocketClients, boxId)
            voteNumber += 1
            if (voteNumber == remoteWebSocketActors.size) {
              gameState = States.STARTED
              broadcastGameState(webSocketClients, States.STARTED, boxId)
              // update the score of each remote
              scores(uniqueId) = scores.getOrElse(uniqueId, 0) + 1
              // TODO send broadcast command to all clients to tell the vote result
            }
          } else {
            context.log.info("Cannot vote, game is not in VOTING state")
            webSocketClients(boxId)(uniqueId) ! TextMessage(
              "Cannot vote, game is not in VOTING state"
            )
          }
          Behaviors.same

        case DisconnectRemote(boxId, uniqueId) =>
          if (!remoteWebSocketActors.contains(uniqueId)) {
            webSocketClients(boxId)(uniqueId) ! TextMessage(
              s"Remote $uniqueId is not connected"
            )
          } else {
            remoteWebSocketActors -= uniqueId
            context.log.info(s"Remote $uniqueId disconnected")
            gameState = States.STARTED
            webSocketClients(boxId)(uniqueId) ! TextMessage(
              s"Remote $uniqueId disconnected"
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
      boxId: Int
  ): Unit = {
    webSocketClients(boxId).foreach {
      case (uniqueId, remote) => {
        newState match {
          case States.VOTING => {
            val response = CommandResponse(uniqueId, "VOTING", "success")
            remote ! TextMessage(response.toJson.compactPrint)
          }
          case States.PAUSED => {
            val response = CommandResponse(uniqueId, "PAUSED", "success")
            remote ! TextMessage(response.toJson.compactPrint)
          }
          case States.STARTED => {
            val response = CommandResponse(uniqueId, "STARTED", "success")
            remote ! TextMessage(response.toJson.compactPrint)
          }
          case States.STOPPED => {
            val response = CommandResponse(uniqueId, "STOPPED", "success")
            remote ! TextMessage(response.toJson.compactPrint)
          }
        }
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
      boxId: Int
  ): Unit = {
    webSocketClients(boxId).foreach {
      case (uniqueId, remote) => {
        val response = CommandResponse(uniqueId, "VoteCommand", "success")
        remote ! TextMessage(response.toJson.compactPrint)
      }
    }
  }
}
