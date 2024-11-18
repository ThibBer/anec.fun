package com.anectdot

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._

sealed trait Command {
  def boxId: Int
}

case class StartGameCommand(boxId: Int, uniqueId: String) extends Command
case class StopGameCommand(boxId: Int, uniqueId: String) extends Command
case class VoteCommand(boxId: Int, vote: String, uniqueId: String)
    extends Command
case class ConnectRemote(boxId: Int, uniqueId: String) extends Command
case class DisconnectRemote(boxId: Int, uniqueId: String) extends Command

case class ConnectBox(boxId: Int, uniqueId: String) extends Command

case class StartVoting(boxId: Int, uniqueId: String) extends Command

case class StickExploded(boxId: Int) extends Command
case class CommandResponse(
    uniqueId: String,
    commandType: String,
    status: String,
    message: Option[String] = None,
    senderUniqueId: Option[String] = None
)

case class VoiceFlow(boxId: Int, uniqueId: String, payload: Option[String])
    extends Command

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {

  implicit val startGameFormat: RootJsonFormat[StartGameCommand] = jsonFormat2(
    StartGameCommand.apply
  )
  implicit val stopGameFormat: RootJsonFormat[StopGameCommand] = jsonFormat2(
    StopGameCommand.apply
  )
  implicit val voteFormat: RootJsonFormat[VoteCommand] = jsonFormat3(
    VoteCommand.apply
  )
  implicit val connectRemoteFormat: RootJsonFormat[ConnectRemote] = jsonFormat2(
    ConnectRemote.apply
  )
  implicit val disconnectRemoteFormat: RootJsonFormat[DisconnectRemote] =
    jsonFormat2(DisconnectRemote.apply)

  implicit val connectBoxFormat: RootJsonFormat[ConnectBox] = jsonFormat2(
    ConnectBox.apply
  )

  implicit val startVotingFormat: RootJsonFormat[StartVoting] = jsonFormat2(
    StartVoting.apply
  )

  implicit val commandResponseFormat: RootJsonFormat[CommandResponse] =
    jsonFormat5(CommandResponse.apply)

  implicit val voiceFlowFormat: RootJsonFormat[VoiceFlow] = jsonFormat3(
    VoiceFlow.apply
  )

  implicit val stickExplodedFormat: RootJsonFormat[StickExploded] =
    jsonFormat1(StickExploded.apply)


  implicit object CommandJsonFormat extends RootJsonFormat[Command] {
    def write(command: Command): JsValue = command match {
      case cmd: StartGameCommand                           => cmd.toJson
      case cmd: StopGameCommand                            => cmd.toJson
      case cmd: VoteCommand                                => cmd.toJson
      case cmd: ConnectRemote                              => cmd.toJson
      case cmd: DisconnectRemote                           => cmd.toJson
      case cmd: ConnectBox                                 => cmd.toJson
      case cmd: StartVoting                                => cmd.toJson
      case cmd: VoiceFlow                                  => cmd.toJson
      case cmd: StickExploded                              => cmd.toJson
      case com.anectdot.VoteSubmittedNotification(_, _)    => ???
      case com.anectdot.GameStateChangedNotification(_, _) => ???
    }

    def read(json: JsValue): Command = {
      val fields = json.asJsObject.fields
      fields.get("commandType") match {
        case Some(JsString("StartGameCommand")) =>
          json.convertTo[StartGameCommand]
        case Some(JsString("StopGameCommand")) =>
          json.convertTo[StopGameCommand]
        case Some(JsString("VoteCommand"))   => json.convertTo[VoteCommand]
        case Some(JsString("ConnectRemote")) => json.convertTo[ConnectRemote]
        case Some(JsString("DisconnectRemote")) =>
          json.convertTo[DisconnectRemote]
        case Some(JsString("ConnectBox"))  => json.convertTo[ConnectBox]
        case Some(JsString("StartVoting")) => json.convertTo[StartVoting]
        case Some(JsString("VoiceFlow"))   => json.convertTo[VoiceFlow]
        case _ => {
          logger.error(s"Invalid command: $json")
          throw DeserializationException("Unknown command type")
        }
      }
    }
  }
}
case class VoteSubmittedNotification(vote: String, uniqueId: String)
    extends Command {
  def boxId: Int = -1
}

case class GameStateChangedNotification(newState: States, uniqueId: String)
    extends Command {
  def boxId: Int = -1
}
