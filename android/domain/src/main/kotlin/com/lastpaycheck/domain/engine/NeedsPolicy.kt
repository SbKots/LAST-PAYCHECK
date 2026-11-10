package com.lastpaycheck.domain.engine

import com.lastpaycheck.domain.model.Activity
import com.lastpaycheck.domain.model.Needs
import com.lastpaycheck.domain.model.TimedActivity
import com.lastpaycheck.domain.model.WorkShift

internal class NeedsPolicy {
    fun advance(
        needs: Needs,
        activity: Activity?,
        requestedMillis: Long,
        extraWorkEnergyPerHour: Int
    ): NeedsAdvance {
        val energyRate = when (activity) {
            is TimedActivity.Sleep -> 10L
            is WorkShift -> -2L - activity.job.extraEnergyPerHour - extraWorkEnergyPerHour
            else -> -2L
        }
        val satietyExhaustion = ceilDivide(needs.satietyUnits, 3)
        val energyExhaustion = if (energyRate < 0) {
            ceilDivide(needs.energyUnits, -energyRate)
        } else {
            Long.MAX_VALUE
        }
        val damageStartsAt = minOf(satietyExhaustion, energyExhaustion)
        val timeToDeath = if (damageStartsAt < requestedMillis) {
            damageStartsAt + ceilDivide(needs.healthUnits, 5)
        } else {
            Long.MAX_VALUE
        }
        val elapsed = minOf(requestedMillis, timeToDeath)
        val damagedMillis = (elapsed - minOf(elapsed, damageStartsAt))
        val moodRate = if (activity is TimedActivity.Rest) 14 else -1
        return NeedsAdvance(
            Needs(
                satietyUnits = clamp(needs.satietyUnits - 3 * elapsed),
                energyUnits = clamp(needs.energyUnits + energyRate * elapsed),
                moodUnits = clamp(needs.moodUnits + moodRate * elapsed),
                healthUnits = clamp(needs.healthUnits - 5 * damagedMillis)
            ),
            elapsed
        )
    }

    private fun clamp(value: Long) = value.coerceIn(0, Needs.MAX_UNITS)

    private fun ceilDivide(value: Long, divisor: Long) =
        value / divisor + if (value % divisor == 0L) 0 else 1
}

internal data class NeedsAdvance(val needs: Needs, val elapsedGameMillis: Long)
