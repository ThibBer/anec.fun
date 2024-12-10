package com.anecdot

import akka.actor.typed.ActorRef
import akka.http.scaladsl.model.ws.TextMessage
import com.anecdot.PlayerType.Remote

enum PlayerType:
  case Unknown, Remote, Box

enum PlayerStatus:
  case Connected, Active, Inactive, Disconnected


case class Player(
    uniqueId: String,
    username: String,
    score: Int,
    actorRef: ActorRef[TextMessage],
    heartbeat: Long,
    playerType: PlayerType = PlayerType.Unknown,
    status: PlayerStatus = PlayerStatus.Connected
) {
}
