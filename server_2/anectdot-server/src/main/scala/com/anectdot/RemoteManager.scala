package com.anectdot

import akka.actor.typed._
import akka.actor.typed.scaladsl._
import scala.collection.mutable
import akka.http.scaladsl.model.ws.TextMessage

final val MIN_PLAYERS = 2
enum States:
  case STOPPED, STARTED, PAUSED, VOTING

var game_state = States.STOPPED

object RemoteManager {

  def apply(
      webSocketClients: mutable.Map[Int, ActorRef[TextMessage]]
  ): Behavior[Command] =
    Behaviors.setup { context =>

      val remoteActors = mutable.Map[Int, ActorRef[Command]]()

      Behaviors.receiveMessage {
        case StartGameCommand(box_id) =>
          if (remoteActors.size < MIN_PLAYERS) {
            context.log.info(
              s"Cannot start game for box_id: $box_id, not enough players"
            )
            webSocketClients(box_id) ! TextMessage(
              s"Cannot start game for box_id: $box_id, not enough players"
            )
          } else {
            context.log.info(s"Starting the game for box_id: $box_id")
            game_state = States.STARTED
            broadcastGameState(webSocketClients, States.STARTED)
            webSocketClients(box_id) ! TextMessage(
              s"Cannot start game for box_id: $box_id, not enough players"
            )
          }
          Behaviors.same

        case StopGameCommand(box_id) =>
          context.log.info(s"Stopping the game for box_id: $box_id")
          remoteActors.clear()
          game_state = States.STOPPED
          broadcastGameState(webSocketClients, States.STOPPED)
          webSocketClients(box_id) ! TextMessage(
            s"Stopping the game for box_id: $box_id"
          )
          Behaviors.same

        case ConnectRemote(box_id, remote_id) =>
          val remoteActor = remoteActors.getOrElseUpdate(
            remote_id, {
              context.log.info(s"Remote $remote_id connected")
              webSocketClients(box_id) ! TextMessage(
                s"Remote $remote_id connected"
              )

              context.spawn(Remote(), s"remote-$remote_id")
            }
          )
          Behaviors.same

        case DisconnectRemote(box_id, remote_id) =>
          remoteActors.remove(remote_id)
          context.log.info(s"Remote $remote_id disconnected")
          game_state = States.STARTED
          webSocketClients(box_id) ! TextMessage(
            s"Remote $remote_id disconnected"
          )
          Behaviors.same

        case VoteCommand(box_id, vote) =>
          if (game_state == States.VOTING) {
            context.log.info(s"Received vote: $vote")
            webSocketClients(box_id) ! TextMessage(
              s"Received vote for box_id: $box_id, vote : $vote"
            )
            broadcastVote(webSocketClients, vote)
          } else {
            context.log.info(s"Cannot vote, game is not in VOTING state")
            webSocketClients(box_id) ! TextMessage(
              s"Cannot vote, game is not in VOTING state"
            )
          }
          Behaviors.same

        case _ =>
          Behaviors.unhandled
      }
    }

  private def broadcastGameState(
      webSocketClients: mutable.Map[Int, ActorRef[TextMessage]],
      newState: States
  ): Unit = {
    webSocketClients.values.foreach { remote =>
      remote ! TextMessage(
        s"Game state changed to: $newState"
      )
    }
  }

  private def broadcastVote(
      webSocketClients: mutable.Map[Int, ActorRef[TextMessage]],
      vote: String
  ): Unit = {
    webSocketClients.values.foreach { remote =>
      remote ! TextMessage(
        s"Vote received: $vote"
      )
    }
  }
}
