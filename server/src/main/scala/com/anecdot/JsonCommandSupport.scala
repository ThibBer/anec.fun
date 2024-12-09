package com.anecdot

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._

trait JsonCommandSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val startGameFormat: RootJsonFormat[StartGameCommand] = jsonFormat2(StartGameCommand.apply)
  implicit val startRoundFormat: RootJsonFormat[StartRoundCommand] = jsonFormat2(StartRoundCommand.apply)
  implicit val stopGameFormat: RootJsonFormat[StopGameCommand] = jsonFormat2(StopGameCommand.apply)
  implicit val voteFormat: RootJsonFormat[VoteCommand] = jsonFormat3(VoteCommand.apply)
  implicit val connectRemoteFormat: RootJsonFormat[ConnectRemote] = jsonFormat3(ConnectRemote.apply)
  implicit val disconnectRemoteFormat: RootJsonFormat[DisconnectRemote] = jsonFormat2(DisconnectRemote.apply)
  implicit val connectBoxFormat: RootJsonFormat[ConnectBox] = jsonFormat2(ConnectBox.apply)
  implicit val startVotingFormat: RootJsonFormat[StartVoting] = jsonFormat2(StartVoting.apply)
  implicit val commandResponseFormat: RootJsonFormat[CommandResponse] = jsonFormat6(CommandResponse.apply)
  implicit val gameStateSnapshotFormat: RootJsonFormat[GameStateSnapshot] = jsonFormat5(GameStateSnapshot.apply)
  implicit val voiceFlowFormat: RootJsonFormat[VoiceFlow] = jsonFormat3(VoiceFlow.apply)
  implicit val stickExplodedFormat: RootJsonFormat[StickExploded] = jsonFormat1(StickExploded.apply)
  implicit val scanStickFormat: RootJsonFormat[ScannedStickCommand] = jsonFormat2(ScannedStickCommand.apply)
  implicit val setGameModeFormat: RootJsonFormat[SetGameModeCommand] = jsonFormat3(SetGameModeCommand.apply)
  implicit val idleGameFormat: RootJsonFormat[IdleGameCommand] = jsonFormat1(IdleGameCommand.apply)

  private val commandTypeMap: Map[String, JsValue => Command] = Map(
    "StartGameCommand" -> (_.convertTo[StartGameCommand]),
    "StartRoundCommand" -> (_.convertTo[StartRoundCommand]),
    "StopGameCommand" -> (_.convertTo[StopGameCommand]),
    "VoteCommand" -> (_.convertTo[VoteCommand]),
    "ConnectRemote" -> (_.convertTo[ConnectRemote]),
    "DisconnectRemote" -> (_.convertTo[DisconnectRemote]),
    "ConnectBox" -> (_.convertTo[ConnectBox]),
    "StartVoting" -> (_.convertTo[StartVoting]),
    "VoiceFlow" -> (_.convertTo[VoiceFlow]),
    "ScannedStickCommand" -> (_.convertTo[ScannedStickCommand]),
    "SetGameModeCommand" -> (_.convertTo[SetGameModeCommand]),
    "IdleGameCommand" -> (_.convertTo[IdleGameCommand])
  )

  implicit object CommandJsonFormat extends RootJsonFormat[Command] {
    def write(command: Command): JsValue = command match {
      case cmd: StartGameCommand                           => cmd.toJson
      case cmd: StartRoundCommand                          => cmd.toJson
      case cmd: StopGameCommand                            => cmd.toJson
      case cmd: VoteCommand                                => cmd.toJson
      case cmd: ConnectRemote                              => cmd.toJson
      case cmd: DisconnectRemote                           => cmd.toJson
      case cmd: ConnectBox                                 => cmd.toJson
      case cmd: StartVoting                                => cmd.toJson
      case cmd: VoiceFlow                                  => cmd.toJson
      case cmd: StickExploded                              => cmd.toJson
      case cmd: ScannedStickCommand                        => cmd.toJson
      case cmd: SetGameModeCommand                         => cmd.toJson
      case cmd: IdleGameCommand                         => cmd.toJson
      case com.anecdot.VoteSubmittedNotification(_, _)    => ???
      case com.anecdot.GameStateChangedNotification(_, _) => ???
      case com.anecdot.StopRoundCommand(_) => ???
    }

    def read(json: JsValue): Command = {
      val fields = json.asJsObject.fields
      fields.get("commandType") match {
        case Some(JsString(commandType)) =>
          commandTypeMap.get(commandType) match {
            case Some(deserializer) => deserializer(json)
            case None =>
              logger.error(s"Invalid command: $json")
              throw DeserializationException("Unknown command type")
          }
        case _ =>
          logger.error(s"Invalid command: $json")
          throw DeserializationException("Unknown command type")
      }
    }
  }
}
