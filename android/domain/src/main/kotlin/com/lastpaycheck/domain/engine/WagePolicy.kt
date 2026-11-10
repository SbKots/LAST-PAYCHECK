package com.lastpaycheck.domain.engine

import com.lastpaycheck.domain.model.GAME_HOUR_MILLIS
import com.lastpaycheck.domain.model.PlayerState
import com.lastpaycheck.domain.model.ShiftResult
import com.lastpaycheck.domain.model.WorkShift
import java.math.BigInteger

internal class WagePolicy {
    fun settle(shift: WorkShift, player: PlayerState): Pair<PlayerState, ShiftResult> {
        val job = shift.job
        val wage = calculateWage(shift, player.needs.moodUnits)
        val debtPaid = minOf(wage, player.rentDebtMinor)
        val totalTasks = shift.correct.toLong() + player.taskRemainder
        val experience = (totalTasks / 10).toInt()
        val next = player.copy(
            moneyMinor = Math.addExact(player.moneyMinor, wage - debtPaid),
            rentDebtMinor = player.rentDebtMinor - debtPaid,
            experience = Math.addExact(player.experience, experience),
            taskRemainder = (totalTasks % 10).toInt()
        )
        val result = ShiftResult(
            correct = shift.correct,
            errors = shift.errors,
            workedGameMillis = job.durationHours * GAME_HOUR_MILLIS - shift.remainingGameMillis,
            wageMinor = wage,
            debtPaidMinor = debtPaid,
            experienceGained = experience
        )
        return next to result
    }

    private fun calculateWage(shift: WorkShift, moodUnits: Long): Long {
        if (shift.correct == 0) return 0
        val job = shift.job
        val attempts = (shift.correct.toLong() + shift.errors).toBigInteger()
        val moodDivisor = if (moodUnits < 20 * GAME_HOUR_MILLIS) {
            BigInteger.valueOf(2)
        } else {
            BigInteger.ONE
        }
        val accuracyDenominator = attempts * moodDivisor
        val planCredit = minOf(shift.correct, job.plan).toBigInteger()
        val base = job.basePayMinor.toBigInteger() * accuracyDenominator
        val bonus = job.bonusCapMinor.toBigInteger() * shift.correct.toBigInteger()
        return ((base + bonus) * planCredit / (job.plan.toBigInteger() * accuracyDenominator))
            .longValueExact()
    }
}
