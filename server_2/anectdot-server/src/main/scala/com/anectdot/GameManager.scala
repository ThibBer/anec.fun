package com.anectdot

import akka.actor.typed._
import akka.actor.typed.scaladsl._
import akka.http.scaladsl.model.ws.TextMessage
import com.anectdot.Main.commandResponseFormat
import spray.json._

import scala.collection.mutable

enum States:
  case STOPPED, STARTED, PAUSED, VOTING

/** The `GameManager` actor is responsible for managing the state of a game. It
  * handles commands from clients and the box actor, and broadcasts game state
  * updates to all connected clients. It also keeps track of the number of
  * connected clients and the current game state.
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

      /** Handles commands for managing the remote game session.
        *
        * Supported Commands:
        *   - `StartGameCommand(boxId, uniqueId)`: Starts the game if the
        *     required number of players are connected.
        *   - `StopGameCommand(boxId, uniqueId)`: Ends the game and resets the
        *     state of remote actors.
        *   - `ConnectRemote(boxId, uniqueId)`: Adds a remote actor and updates
        *     clients of the connection.
        *   - `DisconnectRemote(boxId, uniqueId)`: Removes a remote actor and
        *     informs clients of the disconnection.
        *   - `VoteCommand(boxId, vote, uniqueId)`: Records a vote when the game
        *     is in the VOTING phase.
        *   - `StartVoting(boxId, uniqueId)`: Sets the game state to VOTING.
        *   - `VoiceFlow(boxId, uniqueId, payload)`: Sends audio data to the
        *     game.
        *
        * @param boxId
        *   The identifier for the game session.
        * @param uniqueId
        *   The unique identifier for the remote actor or command.
        * @param vote
        *   The player's vote to be recorded.
        * @param payload
        *   The audio data to be sent to the game.
        *
        * @return
        *   The behavior for processing subsequent messages.
        */
      Behaviors.receiveMessage {
        case ConnectBox(boxId, uniqueId) =>
          boxActor match {
            case None =>
              boxActor = Some(context.self)
              val response =
                CommandResponse(uniqueId, "ConnectBox", "success")
              webSocketClients(boxId)(uniqueId) ! TextMessage(
                response.toJson.compactPrint
              )
            case Some(_) =>
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
          Behaviors.same

        case StartGameCommand(boxId, uniqueId) =>
          if (boxActor.isEmpty) {
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
            broadcastGameState(
              webSocketClients,
              States.STARTED,
              boxId
            )
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
            broadcastGameState(
              webSocketClients,
              States.STOPPED,
              boxId
            )
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
                "success",
                senderUniqueId = Some(uniqueId)
              )
              webSocketClients(boxId)(uniqueId) ! TextMessage(
                response.toJson.compactPrint
              )
              if (remoteWebSocketActors.size > 0) {
                for (remote <- remoteWebSocketActors) {
                  val remoteUniqueId = remote
                  if (remoteUniqueId != uniqueId) {
                    val response = CommandResponse(
                      uniqueId,
                      "ConnectRemote",
                      "success",
                      senderUniqueId = Some(remoteUniqueId)
                    )
                    webSocketClients(boxId)(uniqueId) ! TextMessage(
                      response.toJson.compactPrint
                    )
                  }
                }
              }
              broadcastRemoteConnection(webSocketClients, boxId, uniqueId)
              remoteWebSocketActors += uniqueId
          }
          Behaviors.same

        case VoteCommand(boxId, vote, uniqueId) =>
          if (gameState == States.VOTING) {
            context.log.info(s"Received vote: $vote")
            broadcastVote(webSocketClients, boxId, vote, uniqueId)
            voteNumber += 1
            if (voteNumber == remoteWebSocketActors.size) {
              gameState = States.STARTED
              broadcastGameState(
                webSocketClients,
                States.STARTED,
                boxId
              )
              // update the score of each remote
              scores(uniqueId) = scores.getOrElse(uniqueId, 0) + 1
              // TODO send broadcast command to all clients to tell the vote result
            }
          } else {
            context.log.info("Cannot vote, game is not in VOTING state")
            val response = CommandResponse(
              uniqueId,
              "VoteCommand",
              "failed",
              Some("game is not in VOTING state")
            )
            webSocketClients(boxId)(uniqueId) ! TextMessage(
              response.toJson.compactPrint
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

        case VoiceFlow(boxId, uniqueId, payload) =>
          payload match
            case None => context.log.info("payload None")
            case Some(payload) =>
              context.log.info(payload)
              webSocketClients(boxId)(uniqueId) ! TextMessage("Received")
          Behaviors.same
        case _ =>
          Behaviors.unhandled
      }
    }

  /** Broadcasts the current game state to all connected clients.
    *
    * This method iterates over all WebSocket clients connected to a specific
    * `boxId` and sends the current game state to each client. The game state is
    * represented by the `newState` parameter, which can be one of the
    * predefined `States`.
    *
    * @param webSocketClients
    *   The map of all connected clients, organized by box ID and unique client
    *   ID.
    * @param newState
    *   The new state of the game to be broadcasted to clients.
    * @param boxId
    *   The ID of the box for which the game state is being broadcasted.
    */
  private def broadcastGameState(
      webSocketClients: mutable.Map[Int, mutable.Map[String, ActorRef[
        TextMessage
      ]]],
      newState: States,
      boxId: Int
  ): Unit = {
    // Iterate over each client connected to the specified boxId
    webSocketClients(boxId).foreach { case (uniqueId, remote) =>
      // Match the current game state and prepare the appropriate response
      newState match {
        case States.VOTING =>
          val response = CommandResponse(
            uniqueId,
            "StatusCommand",
            "success",
            Some("VOTING")
          )
          println("broadcasting voting state")
          remote ! TextMessage(response.toJson.compactPrint)

        case States.PAUSED =>
          val response = CommandResponse(
            uniqueId,
            "StatusCommand",
            "success",
            Some("PAUSED")
          )
          remote ! TextMessage(response.toJson.compactPrint)

        case States.STARTED =>
          val response = CommandResponse(
            uniqueId,
            "StatusCommand",
            "success",
            Some("STARTED")
          )
          remote ! TextMessage(response.toJson.compactPrint)
          println("broadcasting started state")

        case States.STOPPED =>
          val response = CommandResponse(
            uniqueId,
            "StatusCommand",
            "success",
            Some("STOPPED")
          )
          remote ! TextMessage(response.toJson.compactPrint)
      }
    }
  }

  /** Broadcasts the vote information to all connected clients. This method is
    * used to inform all clients of the vote result.
    *
    * @param webSocketClients
    *   The map of all connected clients.
    * @param boxId
    *   The ID of the box.
    * @param vote
    *   The vote result.
    * @param senderUniqueId
    *   The unique ID of the sender.
    */
  private def broadcastVote(
      webSocketClients: mutable.Map[Int, mutable.Map[String, ActorRef[
        TextMessage
      ]]],
      boxId: Int,
      vote: String,
      senderUniqueId: String
  ): Unit = {
    // Iterate over all connected clients and send the vote result to each one.
    webSocketClients(boxId).foreach { case (uniqueId, remote) =>
      val response =
        CommandResponse(
          uniqueId,
          "VoteCommand",
          "success",
          senderUniqueId = Some(senderUniqueId),
          message = Some(vote)
        )
      // Send the vote result to the client.
      remote ! TextMessage(response.toJson.compactPrint)
    }
  }

  /** Broadcasts the connection of a remote to all connected clients. This
    * method sends the connection information to all clients that are connected
    * to the server, ensuring that they have the latest information about the
    * remote players.
    *
    * @param webSocketClients
    *   The map of all connected clients.
    * @param boxId
    *   The ID of the box.
    * @param senderUniqueId
    *   The unique ID of the sender.
    */
  private def broadcastRemoteConnection(
      webSocketClients: mutable.Map[Int, mutable.Map[String, ActorRef[
        TextMessage
      ]]],
      boxId: Int,
      senderUniqueId: String
  ): Unit = {
    webSocketClients(boxId).foreach { case (uniqueId, remote) =>
      val response = CommandResponse(
        uniqueId,
        "ConnectRemote",
        "success",
        senderUniqueId = Some(senderUniqueId)
      )
      remote ! TextMessage(response.toJson.compactPrint)
    }
  }
}
