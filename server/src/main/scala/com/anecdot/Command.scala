package com.anecdot

sealed trait Command {
  def boxId: Int
}

case class StartGameCommand(boxId: Int, uniqueId: String) extends Command
case class StartRoundCommand(boxId: Int, uniqueId: String) extends Command
case class StopGameCommand(boxId: Int, uniqueId: String) extends Command
case class VoteCommand(boxId: Int, vote: String, uniqueId: String, isSpeaker:Boolean) extends Command
case class ConnectRemote(boxId: Int, uniqueId: String, username: String) extends Command
case class DisconnectRemote(boxId: Int, uniqueId: String) extends Command
case class ConnectBox(boxId: Int, uniqueId: String) extends Command
case class StartVoting(boxId: Int, uniqueId: String) extends Command
case class StickExploded(boxId: Int) extends Command
case class ScannedStickCommand(boxId: Int, uniqueId: String) extends Command
case class StopRoundCommand(boxId: Int) extends Command

case class CommandResponse(
    uniqueId: String,
    commandType: String,
    status: String,
    message: Option[String] = None,
    senderUniqueId: Option[String] = None
)

case class VoiceFlow(boxId: Int, uniqueId: String, payload: Option[Array[Byte]] ) extends Command

case class VoteSubmittedNotification(vote: String, uniqueId: String) extends Command {
  def boxId: Int = -1
}

case class GameStateChangedNotification(newState: States, uniqueId: String) extends Command {
  def boxId: Int = -1
}
