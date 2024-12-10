package com.anecdot

import akka.actor.typed.*
import akka.actor.typed.scaladsl.*
import akka.http.scaladsl.model.ws.TextMessage
import com.anecdot.CategoryJsonProtocol.categoryFormat
import com.anecdot.Main.commandResponseFormat
import com.anecdot.Main.gameStateSnapshotFormat
import com.anecdot.PlayerStatus.{Active, Connected}
import com.anecdot.PlayerType.{Box, Remote, Unknown}
import spray.json.DefaultJsonProtocol.*
import spray.json.*

import scala.collection.mutable
import scala.concurrent.duration.DurationInt
import scala.util.Random

val timeStickExplosion = (20, 40)

/** The `GameManager` actor is responsible for managing the state of a game. It
  * handles commands from clients and the box actor, and broadcasts game state
  * updates to all connected clients. It also keeps track of the number of
  * connected clients and the current game state.
  */
object GameManager {

  // Global variable for the different themes
  object Global {
    var themeSubjects: Array[String] =
      Array("voyage", "ecole", "famille", "travail", "fete", "sport")
    var emotionSubjects: Array[String] =
      Array("joie", "tristesse", "peur", "surprise", "dégoût")
  }

  def apply(
      boxId: Int,
      commandRouter: ActorRef[CommandRouterTrait]
  ): Behavior[Command] =
    Behaviors.withTimers { timers =>

      timers.startTimerWithFixedDelay(
        "CheckInactiveClients",
        CheckInactiveClients(),
        30.seconds
      )

      Behaviors.setup { context =>
        val minPlayers: Int = 2
        val players = mutable.Map[String, Player]()
        var boxActor: Option[ActorRef[Command]] = None
        val votes = mutable.Map[String, String]()
        var gameState = States.IDLE
        var isStickExploded = false
        var isStickExplodedConfirmationReceived = false
        var gameMode = GameMode.THEME
        var subject: String = ""
        implicit val system: ActorSystem[Nothing] =
          ActorSystem(Behaviors.empty, "DebugSystem")

        val speechToText = SpeechToText()

        var voteResult: String = ""
        var anecdoteSpeakerId: String = ""
        var detectedIntent: Option[Category] = None

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
          case AddClient(boxId, uniqueId, ref) =>
            val player = players.get(uniqueId)
            // update ref and status of player
            if (player.isDefined) {
              players.update(
                uniqueId,
                player.get.copy(
                  status = Active,
                  actorRef = ref,
                  heartbeat = System.currentTimeMillis()
                )
              )
            } else {
              // add new player
              players += (uniqueId -> Player(
                uniqueId,
                "",
                0,
                ref,
                System.currentTimeMillis(),
                Unknown,
                Connected
              ))
            }
            Behaviors.same
          case ConnectBox(boxId, uniqueId) =>
            boxActor match {
              case None =>
                boxActor = Some(context.self)
                players.update(
                  uniqueId,
                  players(uniqueId)
                    .copy(playerType = Box, status = Active, username = "Box")
                )
                players(uniqueId).actorRef ! TextMessage(
                  CommandResponse(
                    uniqueId,
                    "ConnectBox",
                    ResponseState.SUCCESS
                  ).toJson.compactPrint
                )
                Behaviors.same
              case Some(_) =>
                val client = players(uniqueId)
                players(uniqueId) = client.copy(
                  status = Active,
                  heartbeat = System.currentTimeMillis()
                )
                client.actorRef ! TextMessage(
                  CommandResponse(
                    uniqueId,
                    "ConnectBox",
                    ResponseState.SUCCESS,
                    Some(s"Box $boxId successfully reconnected")
                  ).toJson.compactPrint
                )

                sendGameStateToClient(
                  uniqueId,
                  client.actorRef,
                  gameState
                )
                sendGameModeToClient(
                  uniqueId,
                  client.actorRef,
                  gameMode
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
              players(uniqueId).actorRef ! TextMessage(
                response.toJson.compactPrint
              )
            } else if (boxActor.isEmpty) {
              val response = CommandResponse(
                uniqueId,
                "StartGameCommand",
                ResponseState.FAILED,
                Some("box is not connected")
              )
              players(uniqueId).actorRef ! TextMessage(
                response.toJson.compactPrint
              )
            } else if (players.count(p => p._2.status == Active) < minPlayers) {
              val response = CommandResponse(
                uniqueId,
                "StartGameCommand",
                ResponseState.FAILED,
                Some("not enough players")
              )
              players(uniqueId).actorRef ! TextMessage(
                response.toJson.compactPrint
              )
            } else {
              val response = CommandResponse(
                uniqueId,
                "StartGameCommand",
                ResponseState.SUCCESS
              )
              players(uniqueId).actorRef ! TextMessage(
                response.toJson.compactPrint
              )

              gameState = States.STARTED
              broadcastGameState(players, gameState)
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
              players(uniqueId).actorRef ! TextMessage(
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
              players(uniqueId).actorRef ! TextMessage(
                response.toJson.compactPrint
              )
            } else {
              val response =
                CommandResponse(uniqueId, commandName, ResponseState.SUCCESS)
              players(uniqueId).actorRef ! TextMessage(
                response.toJson.compactPrint
              )

              subject = getRandomSubjectForGameMode(gameMode)
              println(s"Random picked subject for mode ($gameMode) : $subject")
              broadcastSubject(players, subject)
              gameState = States.ROUND_STARTED
              broadcastGameState(players, gameState)
              timers.startSingleTimer(
                StickExploded(boxId),
                Random
                  .between(timeStickExplosion._1, timeStickExplosion._2)
                  .seconds
              )
            }

            Behaviors.same

          case StartVoting(boxId, uniqueId) =>
            if (gameState == States.ROUND_STARTED) {
              gameState = States.VOTING
              broadcastGameState(players, States.VOTING)
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
              players(uniqueId).actorRef ! TextMessage(
                response.toJson.compactPrint
              )
            } else {
              if (gameState == States.ROUND_STARTED) {
                context.self ! StopRoundCommand(boxId)
              }

              players.foreach { case (uniqueId, player) =>
                players.update(uniqueId, player.copy(score = 0))
              }
              gameState = States.STOPPED
              broadcastGameState(players, gameState)
            }

            Behaviors.same

          case ConnectRemote(boxId, uniqueId, username) =>
            boxActor match {
              case None =>
                val response = CommandResponse(
                  uniqueId,
                  "ConnectRemote",
                  ResponseState.FAILED,
                  Some(s"Box $boxId is not connected")
                )
                players(uniqueId).actorRef ! TextMessage(
                  response.toJson.compactPrint
                )
              case Some(_) =>
                players.get(uniqueId) match {
                  case None =>
                    context.log.warn("Remote not found in players")
                  case Some(p) =>
                    if (p.status == Active) {
                      val response = CommandResponse(
                        uniqueId,
                        "ConnectRemote",
                        ResponseState.FAILED,
                        Some("Remote is already connected")
                      )
                      players(uniqueId).actorRef ! TextMessage(
                        response.toJson.compactPrint
                      )
                    } else {
                      val isUsernameExist = players.exists { case (_, player) =>
                        player.username == username
                      }
                      if (isUsernameExist) {
                        val response = CommandResponse(
                          uniqueId,
                          "ConnectRemote",
                          ResponseState.FAILED,
                          Some("Username is already taken")
                        )
                        players(uniqueId).actorRef ! TextMessage(
                          response.toJson.compactPrint
                        )
                        players -= uniqueId
                      } else {
                        players.update(
                          uniqueId,
                          p.copy(
                            status = Active,
                            playerType = Remote,
                            username = username,
                            heartbeat = System.currentTimeMillis()
                          )
                        )
                        // send players to the new remote
                        players
                          .filter(p =>
                            p._2.status == Active && p._2.playerType == Remote
                          )
                          .foreach { case (remoteUniqueId, player) =>
                            println(
                              s"Broadcasting remote connection $remoteUniqueId -> ${player.status}"
                            )
                            players(uniqueId).actorRef ! TextMessage(
                              CommandResponse(
                                uniqueId,
                                "ConnectRemote",
                                ResponseState.SUCCESS,
                                senderUniqueId = Some(remoteUniqueId),
                                message = Some(player.username)
                              ).toJson.compactPrint
                            )
                          }
                        // Send the new remote to all connected remotes
                        broadcastRemoteConnection(
                          players,
                          uniqueId,
                          username
                        )

                        sendGameModeToClient(
                          uniqueId,
                          players(uniqueId).actorRef,
                          gameMode
                        )

                        if (anecdoteSpeakerId != "") {
                          sendAnnecdotTellerToClient(
                            uniqueId,
                            players(uniqueId).actorRef,
                            anecdoteSpeakerId
                          )
                        }

                        if (isStickExploded) {
                          sendStickExplodedToClient(
                            uniqueId,
                            players(uniqueId).actorRef
                          )
                        }

                        context.log.info(
                          "remote connected and broadcasted to all remotes"
                        )
                      }
                    }
                }

            }
            Behaviors.same

          case StopRoundCommand(boxId) =>
            gameState = States.ROUND_STOPPED
            broadcastGameState(players, gameState)

            votes.clear()
            isStickExploded = false
            anecdoteSpeakerId = ""

            Behaviors.same

          case VoteCommand(boxId, vote, uniqueId) =>
            if (gameState != States.VOTING) {
              val response = CommandResponse(
                uniqueId,
                "VoteCommand",
                ResponseState.FAILED,
                Some("Game is not in VOTING state, vote not registered")
              )
              players(uniqueId).actorRef ! TextMessage(
                response.toJson.compactPrint
              )
            } else {
              if (uniqueId == anecdoteSpeakerId) {
                val response = CommandResponse(
                  uniqueId,
                  "VoteCommand",
                  ResponseState.FAILED,
                  Some("Speaker can't judge his own anecdote")
                )
                players(uniqueId).actorRef ! TextMessage(
                  response.toJson.compactPrint
                )
              } else {
                broadcastVote(players, vote, uniqueId)
                votes(uniqueId) = vote

                if (
                  votes.keys.size == (players
                    .count(p => p._2.status == Active) - 1)
                ) {
                  logger.info("All remotes voted")

                  val trueVotesCount = votes.values.count(v => v == "true")
                  logger.info(
                    s"True votes count : $trueVotesCount/${players
                        .count(p => p._2.status == Active) - 1}"
                  )
                  players(anecdoteSpeakerId) = players(anecdoteSpeakerId).copy(
                    score = players(anecdoteSpeakerId).score + trueVotesCount
                  )

                  if (detectedIntent.isEmpty) {
                    logger.info(
                      "Detected intend null, ignoring result to compute score"
                    )
                  } else {
                    val intent = detectedIntent.getOrElse("")
                    if (intent == subject) {
                      players(anecdoteSpeakerId) =
                        players(anecdoteSpeakerId).copy(
                          score = players(anecdoteSpeakerId).score + 1
                        )
                      logger.info(
                        s"Detected intend ($intent) match with required subject ($subject), +1 to speaker score"
                      )
                    } else {
                      logger.info(
                        s"Detected intend ($intent) mismatch with required subject ($subject)"
                      )
                    }
                  }

                  logger.info(
                    s"New speaker score : ${players(anecdoteSpeakerId).score}"
                  )

                  // Send the vote result to all clients
                  players.foreach { case (userId, player) =>
                    val response = CommandResponse(
                      userId,
                      "VoteResult",
                      ResponseState.SUCCESS,
                      Some(players(anecdoteSpeakerId).score.toString),
                      senderUniqueId = Some(anecdoteSpeakerId)
                    )
                    player.actorRef ! TextMessage(
                      response.toJson.compactPrint
                    )
                  }

                  context.self ! StopRoundCommand(boxId)
                }
              }
            }
            Behaviors.same

          case DisconnectRemote(boxId, uniqueId) =>
            // this command will remove the remote from the game forever, it's not possible to reconnect
            var responseCommand: Option[CommandResponse] = None

            if (
              !players.contains(uniqueId) || players(uniqueId).status != Active
            ) {
              responseCommand = Some(
                CommandResponse(
                  uniqueId,
                  "DisconnectRemote",
                  ResponseState.FAILED,
                  Some(s"Remote $uniqueId is not connected")
                )
              )
            } else {
              responseCommand = Some(
                CommandResponse(
                  uniqueId,
                  "DisconnectRemote",
                  ResponseState.SUCCESS,
                  Some(s"Remote $uniqueId disconnected")
                )
              )
            }

            players(uniqueId).actorRef ! TextMessage(
              responseCommand.toJson.compactPrint
            )
            if (players.contains(uniqueId)) {
              players -= uniqueId
              broadcastClientDisconnected(players, uniqueId)
            }

            Behaviors.same

          case VoiceFlow(boxId, uniqueId, payload) =>
            context.log.info(s"VoiceFlow $uniqueId for box $boxId")

//            MOCK
//            Thread.sleep(5000)
//            gameState = States.VOTING
//            broadcastGameState(webSocketClients, gameState, boxId)

            payload match {
              case None =>
                speechToText
                  .recognize()
                  .via(
                    speechToText.detectIntent(getSubjectsFromGameMode(gameMode))
                  )
                  .runForeach(result => {
                    println(s"Intent result: $result")

                    val parsedJson = result.parseJson
                    detectedIntent = Some(parsedJson.convertTo[Category])

                    gameState = States.VOTING
                    broadcastGameState(players, gameState)
                  })
              case Some(payload) =>
                speechToText.addPayload(payload)
            }
            Behaviors.same

          case StickExploded(boxId) =>
            logger.info("Stick exploded")
            broadcastStickExploded(players)
            isStickExploded = true
            Behaviors.same

          case ScannedStickCommand(boxId, uniqueId, exploded) =>
            if (exploded && anecdoteSpeakerId == "") {
              val responseCommand =
                CommandResponse(
                  uniqueId,
                  "PlayStickExploded",
                  ResponseState.SUCCESS
                )
              players(uniqueId).actorRef ! TextMessage(
                responseCommand.toJson.compactPrint
              )
              logger.info("sent PlayStickExploded")
              anecdoteSpeakerId = uniqueId
            } else if (!exploded) {
              broadcastStickScan(players)
            }
            Behaviors.same

          case ExplodedAnimationPlayed(boxId) =>
            broadcastGameState(players, States.STICK_EXPLODED)
            broadcastAnnecdotTeller(players, anecdoteSpeakerId)
            Behaviors.same

          case RetrieveStateCommand(boxId, uniqueId) =>
            val snapshot = GameStateSnapshot(
              players = players.map { case (id, player) =>
                id -> Map(
                  "username" -> player.username,
                  "vote" -> votes.getOrElse(id, "")
                )
              }.toMap,
              playerScores = players.map { case (id, player) =>
                id -> player.score
              }.toMap,
              stickExploded = isStickExploded,
              annecdotTellerId = anecdoteSpeakerId,
              state = gameState.toString
            )

            val response = CommandResponse(
              uniqueId,
              "RetrieveStateCommand",
              ResponseState.SUCCESS,
              message = Some(snapshot.toJson.compactPrint)
            )
            players(uniqueId).actorRef ! TextMessage(
              response.toJson.compactPrint
            )
            Behaviors.same

          case ClientDisconnected(boxId, uniqueId, reason) =>
            logger.debug(
              s"$uniqueId in game $boxId disconnected from websocket"
            )
            reason match {
              case DisconnectReason.ClientDisconnected =>
                broadcastClientDisconnected(players, uniqueId)
                players -= uniqueId
              case DisconnectReason.LostConnection =>
                players.update(
                  uniqueId,
                  players(uniqueId).copy(status = PlayerStatus.Disconnected)
                )
            }
            Behaviors.same

          case SetGameModeCommand(boxId, userId, mode) =>
            if (
              gameState != States.STOPPED && gameState != States.ROUND_STOPPED && gameState != States.IDLE
            ) {
              val response = CommandResponse(
                userId,
                "SetGameModeCommand",
                ResponseState.FAILED,
                Some("Can't switch game mode while game is not stopped")
              )

              players(userId).actorRef ! TextMessage(
                response.toJson.compactPrint
              )
            } else {
              players(userId).actorRef ! TextMessage(
                CommandResponse(
                  userId,
                  "SetGameModeCommand",
                  ResponseState.SUCCESS
                ).toJson.compactPrint
              )

              gameMode = mode
              logger.debug(s"Game mode changed to $mode")
              broadcastGameMode(players, gameMode)
            }

            Behaviors.same

          case IdleGameCommand(boxId) =>
            gameState = States.IDLE
            logger.debug(s"Game state changed to $gameState")
            broadcastGameState(players, gameState)

            Behaviors.same
          case HeartbeatClient(boxId, uniqueId) =>
            players.get(uniqueId) match {
              case Some(player) =>
                players.update(
                  uniqueId,
                  player.copy(heartbeat = System.currentTimeMillis())
                )
              case None =>
                context.log.warn(s"Player $uniqueId not found")
            }
            Behaviors.same
          case CheckInactiveClients() =>
            val currentTime = System.currentTimeMillis()
            val inactiveClients = players.filter { case (_, player) =>
              currentTime - player.heartbeat > 30000
            }.keys
            context.log.info(
              s"Checking inactive clients: ${inactiveClients.mkString(", ")}"
            )
            inactiveClients.foreach { uniqueId =>
              players.get(uniqueId) foreach { player =>
                players.update(
                  uniqueId,
                  player.copy(status = PlayerStatus.Inactive)
                )

              }
            }
            if (players.count(p => p._2.status == Active) == 0) {
              context.log.info("No active players, stopping game")
              commandRouter ! GameManagerEvent(boxId, "game-over")
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
    * @param players
    *   The map of all connected clients
    * @param newState
    *   The new state of the game to be broadcasted to clients.
    */
  private def broadcastGameState(
      players: mutable.Map[String, Player],
      newState: States
  ): Unit = {
    println(s"Broadcasting state (${newState.toString})")

    // Iterate over each client connected to the specified boxId
    players
      .foreach { case (uniqueId, remote) =>
        sendGameStateToClient(uniqueId, remote.actorRef, newState)
        println(s"\t - $uniqueId")
      }
  }

  private def sendGameStateToClient(
      uniqueId: String,
      client: ActorRef[TextMessage],
      newState: States
  ): Unit = {
    val response = CommandResponse(
      uniqueId,
      "StatusCommand",
      ResponseState.SUCCESS,
      Some(newState.toString)
    )

    client ! TextMessage(response.toJson.compactPrint)
  }

  /** Broadcasts the vote information to all connected clients. This method is
    * used to inform all clients of the vote result.
    *
    * @param webSocketClients
    *   The map of all connected clients.
    * @param vote
    *   The vote result.
    * @param senderUniqueId
    *   The unique ID of the sender.
    */
  private def broadcastVote(
      webSocketClients: mutable.Map[String, Player],
      vote: String,
      senderUniqueId: String
  ): Unit = {
    // Iterate over all connected clients and send the vote result to each one.
    webSocketClients
      .foreach { case (uniqueId, remote) =>
        val response =
          CommandResponse(
            uniqueId,
            "VoteCommand",
            ResponseState.SUCCESS,
            senderUniqueId = Some(senderUniqueId),
            message = Some(vote)
          )
        // Send the vote result to the client.
        remote.actorRef ! TextMessage(response.toJson.compactPrint)
      }
  }

  private def broadcastStickScan(
      webSocketClients: mutable.Map[String, Player]
  ): Unit = {
    webSocketClients
      .foreach { case (uniqueId, remote) =>
        val response =
          CommandResponse(
            uniqueId,
            "StickScanned",
            ResponseState.SUCCESS
          )
        // Send the vote result to the client.
        remote.actorRef ! TextMessage(response.toJson.compactPrint)
      }
  }

  /** Broadcasts the connection of a remote to all connected clients. This
    * method sends the connection information to all clients that are connected
    * to the server, ensuring that they have the latest information about the
    * remote players.
    *
    * @param players
    *   The map of all connected clients.
    * @param senderUniqueId
    *   The unique ID of the sender.
    */
  private def broadcastRemoteConnection(
      players: mutable.Map[String, Player],
      senderUniqueId: String,
      username: String
  ): Unit = {
    players
      .foreach { case (uniqueId, remote) =>
        val response = CommandResponse(
          uniqueId,
          "ConnectRemote",
          ResponseState.SUCCESS,
          senderUniqueId = Some(senderUniqueId),
          message = Some(username)
        )
        remote.actorRef ! TextMessage(response.toJson.compactPrint)
      }
  }

  private def broadcastStickExploded(
      webSocketClients: mutable.Map[String, Player]
  ): Unit = {
    webSocketClients
      .foreach { case (uniqueId, remote) =>
        sendStickExplodedToClient(uniqueId, remote.actorRef)
      }
  }

  private def sendStickExplodedToClient(
      uniqueId: String,
      client: ActorRef[TextMessage]
  ): Unit = {
    val response = CommandResponse(
      uniqueId,
      "StickExploded",
      ResponseState.SUCCESS
    )

    client ! TextMessage(response.toJson.compactPrint)
  }

  private def broadcastAnnecdotTeller(
      webSocketClients: mutable.Map[String, Player],
      senderUniqueId: String
  ): Unit = {
    webSocketClients
      .foreach { case (uniqueId, remote) =>
        sendAnnecdotTellerToClient(uniqueId, remote.actorRef, senderUniqueId)
      }
  }

  private def sendAnnecdotTellerToClient(
      uniqueId: String,
      client: ActorRef[TextMessage],
      senderUniqueId: String
  ): Unit = {
    val response = CommandResponse(
      uniqueId,
      "AnnecdotTeller",
      ResponseState.SUCCESS,
      senderUniqueId = Some(senderUniqueId)
    )

    client ! TextMessage(response.toJson.compactPrint)
  }

  private def broadcastClientDisconnected(
      players: mutable.Map[String, Player],
      senderUniqueId: String
  ): Unit = {
    logger.debug(s"Broadcast client $senderUniqueId disconnect")
    players
      .foreach { case (uniqueId, remote) =>
        val response = CommandResponse(
          uniqueId,
          "ClientDisconnected",
          ResponseState.SUCCESS,
          senderUniqueId = Some(senderUniqueId)
        )

        logger.debug(s"\t$uniqueId")
        remote.actorRef ! TextMessage(response.toJson.compactPrint)
      }
  }

  private def broadcastSubject(
      players: mutable.Map[String, Player],
      subject: String
  ): Unit = {
    logger.debug(s"Broadcast subject $subject")
    players
      .foreach { case (uniqueId, remote) =>
        val response = CommandResponse(
          uniqueId,
          "SubjectChanged",
          ResponseState.SUCCESS,
          message = Some(subject)
        )

        logger.debug(s"\t$uniqueId")
        remote.actorRef ! TextMessage(response.toJson.compactPrint)
      }
  }

  private def broadcastGameMode(
      webSocketClients: mutable.Map[String, Player],
      gameMode: String
  ): Unit = {
    logger.debug(s"Broadcast game mode $gameMode")
    webSocketClients
      .foreach { case (uniqueId, remote) =>
        sendGameModeToClient(uniqueId, remote.actorRef, gameMode)
        logger.debug(s"\t$uniqueId")
      }
  }

  private def sendGameModeToClient(
      uniqueId: String,
      client: ActorRef[TextMessage],
      gameMode: String
  ): Unit = {
    val response = CommandResponse(
      uniqueId,
      "GameModeChanged",
      ResponseState.SUCCESS,
      Some(gameMode)
    )
    client ! TextMessage(response.toJson.compactPrint)
  }

  private def getRandomSubjectForGameMode(gameMode: String): String = {
    val subjects = getSubjectsFromGameMode(gameMode)
    subjects(Random.nextInt(subjects.length))
  }

  private def getSubjectsFromGameMode(gameMode: String): Array[String] = {
    if (gameMode == GameMode.THEME)
      Global.themeSubjects
    else
      Global.emotionSubjects
  }
}
