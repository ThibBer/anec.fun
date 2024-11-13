package com.anectdot

import akka.actor.typed.*
import akka.actor.typed.scaladsl.*

object Remote {

  def apply(): Behavior[Command] = Behaviors.setup { context =>
    new CommandBehavior(context)
  }

  class CommandBehavior(context: ActorContext[Command])
      extends AbstractBehavior[Command](context) {

    var received = 0
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
