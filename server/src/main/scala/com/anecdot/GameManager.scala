package com.anecdot

import akka.actor.typed._
import akka.actor.typed.scaladsl._
import akka.http.scaladsl.model.ws.TextMessage
import com.anecdot.Main.commandResponseFormat
import com.anecdot.Main.gameStateSnapshotFormat
import spray.json.DefaultJsonProtocol._
import spray.json._

import scala.collection.mutable
import scala.concurrent.duration.DurationInt
import scala.util.Random

/** The `GameManager` actor is responsible for managing the state of a game. It
  * handles commands from clients and the box actor, and broadcasts game state
  * updates to all connected clients. It also keeps track of the number of
  * connected clients and the current game state.
  */
object GameManager {

  // Global variable for the different themes
  object Global {
    var themes: List[String] = List("voyage", "ecole", "famille", "travail", "fete", "sport")
  }

  def apply(
      webSocketClients: mutable.Map[Int, mutable.Map[String, ActorRef[
        TextMessage
      ]]]
  ): Behavior[Command] =
    Behaviors.withTimers { timers =>
      Behaviors.setup { context =>
        val minPlayers: Int = 2
        var remoteWebSocketActors: Map[String, String] = Map()
        var boxActor: Option[ActorRef[Command]] = None
        var voteNumber: Int = 0
        val votes = mutable.Map[String, String]()
        val scores = mutable.Map[String, Int]()
        var gameState = States.STOPPED
        var isStickExploded = false
        var isStickExplodedConfirmationReceived = false
        var gameMode = GameMode.THEME
        var theme: String = ""
        implicit val system: ActorSystem[Nothing] =
          ActorSystem(Behaviors.empty, "DebugSystem")

        val speechToText = SpeechToText()

        var speakerId: String = ""
        var voteResult: String = ""

        /** Handles commands for managing the remote game session.
          *
          * Supported Commands:
          *   - `StartGameCommand(boxId, uniqueId)`: Starts the game if the
          *     required number of players are connected.
          *   - `StopGameCommand(boxId, uniqueId)`: Ends the game and resets the
          *     state of remote actors.
          *   - `ConnectRemote(boxId, uniqueId)`: Adds a remote actor and
          *     updates clients of the connection.
          *   - `DisconnectRemote(boxId, uniqueId)`: Removes a remote actor and
          *     informs clients of the disconnection.
          *   - `VoteCommand(boxId, vote, uniqueId)`: Records a vote when the
          *     game is in the VOTING phase.
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
                  CommandResponse(
                    uniqueId.getOrElse(""),
                    "ConnectBox",
                    ResponseState.SUCCESS
                  )
                webSocketClients(boxId)(uniqueId.getOrElse("")) ! TextMessage(
                  response.toJson.compactPrint
                )

              case Some(_) =>
                val response = CommandResponse(
                  uniqueId.getOrElse(""),
                  "ConnectBox",
                  ResponseState.FAILED,
                  Some(s"box $boxId is already in used")
                )
                webSocketClients(boxId)(uniqueId.getOrElse("")) ! TextMessage(
                  response.toJson.compactPrint
                )
            }

            Behaviors.same

          case StartGameCommand(boxId, uniqueId) =>
            if (gameState == States.STARTED) {
              val response = CommandResponse(
                uniqueId,
                "StartGameCommand",
                ResponseState.FAILED,
                Some("Game is already started")
              )
              webSocketClients(boxId)(uniqueId) ! TextMessage(
                response.toJson.compactPrint
              )
            } else if (boxActor.isEmpty) {
              val response = CommandResponse(
                uniqueId,
                "StartGameCommand",
                ResponseState.FAILED,
                Some("box is not connected")
              )
              webSocketClients(boxId)(uniqueId) ! TextMessage(
                response.toJson.compactPrint
              )
            } else if (remoteWebSocketActors.size < minPlayers) {
              val response = CommandResponse(
                uniqueId,
                "StartGameCommand",
                ResponseState.FAILED,
                Some("not enough players")
              )
              webSocketClients(boxId)(uniqueId) ! TextMessage(
                response.toJson.compactPrint
              )
            } else {
              val response = CommandResponse(
                uniqueId,
                "StartGameCommand",
                ResponseState.SUCCESS
              )
              webSocketClients(boxId)(uniqueId) ! TextMessage(
                response.toJson.compactPrint
              )

              gameState = States.STARTED
              broadcastGameState(webSocketClients, gameState, boxId, theme)
            }

            Behaviors.same

          case StartRoundCommand(boxId, uniqueId) =>
            val commandName = "StartRoundCommand"

            if (gameState == States.ROUND_STARTED) {
              val response = CommandResponse(
                uniqueId,
                commandName,
                ResponseState.FAILED,
                Some("Round is already started")
              )
              webSocketClients(boxId)(uniqueId) ! TextMessage(
                response.toJson.compactPrint
              )
            } else if (
              gameState != States.STARTED && gameState != States.ROUND_STOPPED
            ) {
              val response = CommandResponse(
                uniqueId,
                commandName,
                ResponseState.FAILED,
                Some("Game is not started")
              )
              webSocketClients(boxId)(uniqueId) ! TextMessage(
                response.toJson.compactPrint
              )
            } else {
              val response =
                CommandResponse(uniqueId, commandName, ResponseState.SUCCESS)
              webSocketClients(boxId)(uniqueId) ! TextMessage(
                response.toJson.compactPrint
              )

              theme = Global.themes(Random.nextInt(Global.themes.length))
              println(s"Élément choisi aléatoirement : $theme")
              timers.startSingleTimer(StickExploded(boxId), 10.seconds)
              gameState = States.ROUND_STARTED
              broadcastGameState(webSocketClients, gameState, boxId, theme)
            }

            Behaviors.same

          case StartVoting(boxId, uniqueId) =>
            if (gameState == States.ROUND_STARTED) {
              gameState = States.VOTING
              broadcastGameState(webSocketClients, States.VOTING, boxId, theme)
            }

            Behaviors.same

          case StopGameCommand(boxId, uniqueId) =>
            if (gameState == States.STOPPED) {
              val response = CommandResponse(
                uniqueId,
                "StopGameCommand",
                ResponseState.FAILED,
                Some("Game is already stopped")
              )
              webSocketClients(boxId)(uniqueId) ! TextMessage(
                response.toJson.compactPrint
              )
            } else {
              if (gameState == States.ROUND_STARTED) {
                context.self ! StopRoundCommand(boxId)
              }

              remoteWebSocketActors = Map()
              scores.clear()
              gameState = States.STOPPED
              broadcastGameState(webSocketClients, gameState, boxId, theme)
            }

            Behaviors.same

          case ConnectRemote(boxId, uniqueId, username) =>
            boxActor match {
              case None =>
                val response = CommandResponse(
                  uniqueId.getOrElse(""),
                  "ConnectRemote",
                  ResponseState.FAILED,
                  Some(s"Box $boxId is not connected")
                )
                webSocketClients(boxId)(uniqueId.getOrElse("")) ! TextMessage(
                  response.toJson.compactPrint
                )
              case Some(_)
                  if remoteWebSocketActors.contains(uniqueId.getOrElse("")) =>
                val response = CommandResponse(
                  uniqueId.getOrElse(""),
                  "ConnectRemote",
                  ResponseState.FAILED,
                  Some("Remote is already connected")
                )
                webSocketClients(boxId)(uniqueId.getOrElse("")) ! TextMessage(
                  response.toJson.compactPrint
                )
              case Some(_) =>
                // Send the list of connected remotes to the new remote
                remoteWebSocketActors.foreach {
                  case (remoteUniqueId, username) =>
                    val response = CommandResponse(
                      uniqueId.getOrElse(""),
                      "ConnectRemote",
                      "success",
                      senderUniqueId = Some(remoteUniqueId),
                      message = Some(username)
                    )
                    webSocketClients(boxId)(
                      uniqueId.getOrElse("")
                    ) ! TextMessage(
                      response.toJson.compactPrint
                    )
                }

                remoteWebSocketActors += (uniqueId.getOrElse("") -> username)

                // Send the new remote to all connected remotes
                broadcastRemoteConnection(
                  webSocketClients,
                  boxId,
                  uniqueId.getOrElse(""),
                  username
                )
                context.log.info(
                  "remote connected and broadcasted to all remotes"
                )
            }

            Behaviors.same

          case StopRoundCommand(boxId) =>
            gameState = States.ROUND_STOPPED
            broadcastGameState(webSocketClients, gameState, boxId, theme)

            voteNumber = 0
            votes.clear()
            isStickExploded = false

            Behaviors.same

          case VoteCommand(boxId, vote, uniqueId, isSpeaker) =>
            if (gameState != States.VOTING) {
              val response = CommandResponse(
                uniqueId,
                "VoteCommand",
                ResponseState.FAILED,
                Some("Game is not in VOTING state, vote not registered")
              )
              webSocketClients(boxId)(uniqueId) ! TextMessage(
                response.toJson.compactPrint
              )
            } else {
              broadcastVote(webSocketClients, boxId, vote, uniqueId)
              voteNumber += 1

              if (isSpeaker) {
                speakerId = uniqueId
                voteResult = vote
              } else {
                votes(uniqueId) = vote
              }

              if (voteNumber == remoteWebSocketActors.size) {
                logger.info("All remotes voted")
                // update the score of each remote
                if (votes.getOrElse(uniqueId, "") == voteResult) {
                  scores(uniqueId) = scores.getOrElse(
                    uniqueId,
                    0
                  ) + 1 // Every player can compute their own score, this is just for backup
                }

                // Send the vote result to all clients
                remoteWebSocketActors.foreach { case (userId, username) =>
                  val response = CommandResponse(
                    userId,
                    "VoteResult",
                    ResponseState.SUCCESS,
                    Some(voteResult),
                    senderUniqueId = Some(speakerId)
                  )
                  webSocketClients(boxId)(userId) ! TextMessage(
                    response.toJson.compactPrint
                  )
                }

                context.self ! StopRoundCommand(boxId)
              }
            }
            Behaviors.same

          case DisconnectRemote(boxId, uniqueId) =>
            var responseCommand: Option[CommandResponse] = None

            if (!remoteWebSocketActors.contains(uniqueId)) {
              responseCommand = Some(
                CommandResponse(
                  uniqueId,
                  "DisconnectRemote",
                  ResponseState.FAILED,
                  Some(s"Remote $uniqueId is not connected")
                )
              )
            } else {
              remoteWebSocketActors -= uniqueId
              gameState = States.STARTED
              responseCommand = Some(
                CommandResponse(
                  uniqueId,
                  "DisconnectRemote",
                  ResponseState.SUCCESS,
                  Some(s"Remote $uniqueId disconnected")
                )
              )
            }

            webSocketClients(boxId)(uniqueId) ! TextMessage(
              responseCommand.toJson.compactPrint
            )
            Behaviors.same

          case VoiceFlow(boxId, uniqueId, payload) =>
            context.log.info(s"VoiceFlow $uniqueId for box $boxId")
            // TODO: make intent configurable
            payload match {
              case None =>
                speechToText
                  .recognize()
                  .via(
                    speechToText.detectIntent(Array("voyage", "ecole", "autre"))
                  )
                  .runForeach(result => {
                    // TODO: do something with the result
                    print(s"Run: $result")
                    webSocketClients(boxId)(uniqueId) ! TextMessage(result)
                    gameState = States.VOTING
                    broadcastGameState(webSocketClients, gameState, boxId, theme)
                  })
              case Some(payload) =>
                speechToText.addPayload(payload)
            }
            Behaviors.same

          case StickExploded(boxId) =>
            logger.info("Stick exploded")
            broadcastStickExploded(webSocketClients, boxId)
            isStickExploded = true
            Behaviors.same

          case ScannedStickCommand(boxId, uniqueId) =>
            if (isStickExploded) {
              broadcastAnnecdotTeller(webSocketClients, boxId, uniqueId)
              broadcastGameState(webSocketClients, States.STICK_EXPLODED, boxId, theme)
            }
            Behaviors.same

          case RetrieveStateCommand(boxId, uniqueId) =>
            val snapshot = GameStateSnapshot(
              players = remoteWebSocketActors.map { case (id, username) =>
                id -> Map(
                  "username" -> username,
                  "vote" -> votes.getOrElse(id, "")
                )
              }.toMap,
              playerScores = scores.toMap,
              stickExploded = isStickExploded,
              annecdotTellerId = speakerId,
              state = gameState.toString
            )

            val response = CommandResponse(
              uniqueId,
              "RetrieveStateCommand",
              ResponseState.SUCCESS,
              message = Some(snapshot.toJson.compactPrint)
            )
            webSocketClients(boxId)(uniqueId) ! TextMessage(
              response.toJson.compactPrint
            )
            Behaviors.same

          case ClientDisconnected(boxId, uniqueId) =>
            broadcastClientDisconnected(webSocketClients, boxId, uniqueId)
            Behaviors.same

          case SetGameModeCommand(boxId, userId, mode) =>
            if (gameState != States.STOPPED) {
              val response = CommandResponse(
                userId,
                "SetGameModeCommand",
                ResponseState.FAILED,
                Some("Can't switch game mode while game is not stopped")
              )

              webSocketClients(boxId)(userId) ! TextMessage(
                response.toJson.compactPrint
              )
            } else {
              webSocketClients(boxId)(userId) ! TextMessage(
                CommandResponse(
                  userId,
                  "SetGameModeCommand",
                  ResponseState.SUCCESS
                ).toJson.compactPrint
              )

              gameMode = mode
              logger.debug(s"Game mode changed to $mode")
              broadcastGameMode(webSocketClients, boxId, gameMode)
            }

            Behaviors.same

          case _ => Behaviors.unhandled
        }
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
      boxId: Int,
      newTheme: String
  ): Unit = {
    println(s"Broadcasting state (${newState.toString})")

    // Iterate over each client connected to the specified boxId
    webSocketClients(boxId).foreach { case (uniqueId, remote) =>
      val response = CommandResponse(
        uniqueId,
        "StatusCommand",
        ResponseState.SUCCESS,
        Some(newState.toString)
      )

      println(s"\t - $uniqueId")
      remote ! TextMessage(response.toJson.compactPrint)
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
          ResponseState.SUCCESS,
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
      senderUniqueId: String,
      username: String
  ): Unit = {
    webSocketClients(boxId).foreach { case (uniqueId, remote) =>
      val response = CommandResponse(
        uniqueId,
        "ConnectRemote",
        ResponseState.SUCCESS,
        senderUniqueId = Some(senderUniqueId),
        message = Some(username)
      )
      remote ! TextMessage(response.toJson.compactPrint)
    }
  }

  private def broadcastStickExploded(
      webSocketClients: mutable.Map[Int, mutable.Map[String, ActorRef[
        TextMessage
      ]]],
      boxId: Int
  ): Unit = {
    webSocketClients(boxId).foreach { case (uniqueId, remote) =>
      val response = CommandResponse(
        uniqueId,
        "StickExploded",
        ResponseState.SUCCESS
      )
      remote ! TextMessage(response.toJson.compactPrint)
    }
  }

  private def broadcastAnnecdotTeller(
      webSocketClients: mutable.Map[Int, mutable.Map[String, ActorRef[
        TextMessage
      ]]],
      boxId: Int,
      senderUniqueId: String
  ): Unit = {
    webSocketClients(boxId).foreach { case (uniqueId, remote) =>
      val response = CommandResponse(
        uniqueId,
        "AnnecdotTeller",
        ResponseState.SUCCESS,
        senderUniqueId = Some(senderUniqueId)
      )
      remote ! TextMessage(response.toJson.compactPrint)
    }
  }

  private def broadcastClientDisconnected(
      webSocketClients: mutable.Map[Int, mutable.Map[String, ActorRef[
        TextMessage
      ]]],
      boxId: Int,
      senderUniqueId: String
  ): Unit = {
    webSocketClients(boxId).foreach { case (uniqueId, remote) =>
      val response = CommandResponse(
        uniqueId,
        "ClientDisconnected",
        ResponseState.SUCCESS,
        senderUniqueId = Some(senderUniqueId)
      )
      remote ! TextMessage(response.toJson.compactPrint)
    }
  }

  private def broadcastGameMode(
      webSocketClients: mutable.Map[Int, mutable.Map[String, ActorRef[
        TextMessage
      ]]],
      boxId: Int,
      gameMode: String
  ): Unit = {
    webSocketClients(boxId).foreach { case (uniqueId, remote) =>
      val response = CommandResponse(
        uniqueId,
        "GameModeChanged",
        ResponseState.SUCCESS,
        Some(gameMode)
      )
      remote ! TextMessage(response.toJson.compactPrint)
    }
  }
}
