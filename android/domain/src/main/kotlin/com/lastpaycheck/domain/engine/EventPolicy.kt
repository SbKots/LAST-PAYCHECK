package com.lastpaycheck.domain.engine

import com.lastpaycheck.domain.model.EventDefinition
import com.lastpaycheck.domain.model.GAME_DAY_MILLIS
import com.lastpaycheck.domain.model.GameEvent
import com.lastpaycheck.domain.model.GameSession
import com.lastpaycheck.domain.repository.RandomDraw
import com.lastpaycheck.domain.repository.RandomSource
import java.util.Random

data class EventDecision(val nextRandomState: Long, val event: GameEvent? = null)

fun interface EventPolicy {
    fun evaluate(session: GameSession): EventDecision
}

object NoEventsPolicy : EventPolicy {
    override fun evaluate(session: GameSession) = EventDecision(session.randomState)
}

class LimitedEventsPolicy(
    definitions: List<EventDefinition>,
    private val random: RandomSource
) : EventPolicy {
    private val definitions = definitions.toList()

    init {
        require(this.definitions.isNotEmpty())
        require(this.definitions.map { it.id }.toSet().size == this.definitions.size)
        require(
            this.definitions.all {
                it.id.isNotBlank() && it.description.isNotBlank() &&
                    it.moneyLossPercent in 0..10 && it.healthLoss in 0..100 &&
                    it.durationGameMillis >= 0 && it.extraWorkEnergyPerHour >= 0
            }
        )
    }

    override fun evaluate(session: GameSession): EventDecision {
        val previous = session.lastEvent
        val lastTime = previous?.occurredAt ?: 0
        val spacing = maxOf(3 * GAME_DAY_MILLIS, previous?.durationGameMillis ?: 0)
        if (session.clock.gameMillis - lastTime < spacing) {
            return EventDecision(session.randomState)
        }
        val draw = random.next(session.randomState, definitions.size + 1)
        val definition = definitions.getOrNull(draw.value)
            ?: return EventDecision(draw.nextState)
        val balance = session.player.moneyMinor
        val percent = definition.moneyLossPercent
        val lost = balance / 100 * percent + balance % 100 * percent / 100
        return EventDecision(
            draw.nextState,
            GameEvent(
                definition.id,
                definition.description,
                session.clock.gameMillis,
                lost,
                definition.healthLoss,
                definition.durationGameMillis,
                definition.extraWorkEnergyPerHour
            )
        )
    }
}

object SeededRandomSource : RandomSource {
    override fun next(state: Long, bound: Int): RandomDraw {
        val random = Random(state)
        return RandomDraw(random.nextInt(bound), random.nextLong())
    }
}
