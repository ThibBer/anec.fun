package com.anectdot

import spray.json._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport

sealed trait Command {
  def box_id: Int
}

case class StartGameCommand(box_id: Int) extends Command
case class StopGameCommand(box_id: Int) extends Command
case class VoteCommand(box_id: Int, vote: String) extends Command
case class ConnectRemote(box_id: Int, remote_id: Int) extends Command
case class DisconnectRemote(box_id: Int, remote_id: Int) extends Command

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {

  implicit val startGameFormat: RootJsonFormat[StartGameCommand] = jsonFormat1(
    StartGameCommand.apply
  )
  implicit val stopGameFormat: RootJsonFormat[StopGameCommand] = jsonFormat1(
    StopGameCommand.apply
  )
  implicit val voteFormat: RootJsonFormat[VoteCommand] = jsonFormat2(
    VoteCommand.apply
  )
  implicit val connectRemoteFormat: RootJsonFormat[ConnectRemote] = jsonFormat2(
    ConnectRemote.apply
  )
  implicit val disconnectRemoteFormat: RootJsonFormat[DisconnectRemote] =
    jsonFormat2(DisconnectRemote.apply)

  implicit object CommandJsonFormat extends RootJsonFormat[Command] {
    def write(command: Command): JsValue = command match {
      case cmd: StartGameCommand                        => cmd.toJson
      case cmd: StopGameCommand                         => cmd.toJson
      case cmd: VoteCommand                             => cmd.toJson
      case cmd: ConnectRemote                           => cmd.toJson
      case cmd: DisconnectRemote                        => cmd.toJson
      case com.anectdot.VoteSubmittedNotification(_)    => ???
      case com.anectdot.GameStateChangedNotification(_) => ???
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
        case _ => throw new DeserializationException("Unknown command type")
      }
    }
  }
}
case class VoteSubmittedNotification(vote: String) extends Command {
  def box_id: Int = -1
}

case class GameStateChangedNotification(newState: States) extends Command {
  def box_id: Int = -1
}
