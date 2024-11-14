package com.anectdot

import akka.actor.typed._
import akka.actor.typed.scaladsl._

object Remote {

  def apply(): Behavior[Command] = Behaviors.setup { context =>
    new CommandBehavior(context)
  }

  class CommandBehavior(context: ActorContext[Command])
      extends AbstractBehavior[Command](context) {

    override def onMessage(message: Command): Behavior[Command] = {
      message match {
        case VoteSubmittedNotification(vote, uniqueId) =>
          context.log.info(s"Received vote notification: $vote")
          Behaviors.same

        case GameStateChangedNotification(newState, uniqueId) =>
          context.log.info(
            s"Received game state change notification: $newState"
          )
          Behaviors.same

        case _ =>
          Behaviors.unhandled
      }
    }
  }
}
