package com.anecdot

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._

trait JsonCommandSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val startGameFormat: RootJsonFormat[StartGameCommand] = jsonFormat2(StartGameCommand.apply)
  implicit val startRoundFormat: RootJsonFormat[StartRoundCommand] = jsonFormat2(StartRoundCommand.apply)
  implicit val stopGameFormat: RootJsonFormat[StopGameCommand] = jsonFormat2(StopGameCommand.apply)
  implicit val voteFormat: RootJsonFormat[VoteCommand] = jsonFormat4(VoteCommand.apply)
  implicit val connectRemoteFormat: RootJsonFormat[ConnectRemote] = jsonFormat3(ConnectRemote.apply)
  implicit val disconnectRemoteFormat: RootJsonFormat[DisconnectRemote] = jsonFormat2(DisconnectRemote.apply)
  implicit val connectBoxFormat: RootJsonFormat[ConnectBox] = jsonFormat2(ConnectBox.apply)
  implicit val startVotingFormat: RootJsonFormat[StartVoting] = jsonFormat2(StartVoting.apply)
  implicit val commandResponseFormat: RootJsonFormat[CommandResponse] = jsonFormat5(CommandResponse.apply)
  implicit val voiceFlowFormat: RootJsonFormat[VoiceFlow] = jsonFormat3(VoiceFlow.apply)
  implicit val stickExplodedFormat: RootJsonFormat[StickExploded] = jsonFormat1(StickExploded.apply)
  implicit val scanStickFormat: RootJsonFormat[ScannedStickCommand] = jsonFormat2(ScannedStickCommand.apply)

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
      case com.anecdot.VoteSubmittedNotification(_, _)    => ???
      case com.anecdot.GameStateChangedNotification(_, _) => ???
    }

    def read(json: JsValue): Command = {
      val fields = json.asJsObject.fields

      fields.get("commandType") match {
        case Some(JsString("StartGameCommand")) => json.convertTo[StartGameCommand]
        case Some(JsString("StartRoundCommand")) => json.convertTo[StartRoundCommand]
        case Some(JsString("StopGameCommand")) => json.convertTo[StopGameCommand]
        case Some(JsString("VoteCommand")) => json.convertTo[VoteCommand]
        case Some(JsString("ConnectRemote")) => json.convertTo[ConnectRemote]
        case Some(JsString("DisconnectRemote")) => json.convertTo[DisconnectRemote]
        case Some(JsString("ConnectBox")) => json.convertTo[ConnectBox]
        case Some(JsString("StartVoting")) => json.convertTo[StartVoting]
        case Some(JsString("VoiceFlow")) => json.convertTo[VoiceFlow]
        case Some(JsString("ScannedStickCommand")) => json.convertTo[ScannedStickCommand]
        case _ =>
          logger.error(s"Invalid command: $json")
          throw DeserializationException("Unknown command type")
      }
    }
  }
}
