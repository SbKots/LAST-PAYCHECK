package com.lastpaycheck.domain.usecase

import com.lastpaycheck.domain.model.GAME_HOUR_MILLIS
import com.lastpaycheck.domain.model.GameClock
import com.lastpaycheck.domain.model.GameEffect
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.Needs
import com.lastpaycheck.domain.model.TransactionCategory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FinishShiftUseCaseTest {
    @Test
    fun paysReferenceWageOnceAndSettlesRentDebtAndExperience() = runTest {
        val initial = sampleSession()
        val game = Fixture(
            initial.copy(
                activeActivity = sampleShift(correct = 20, errors = 5, workedHours = 5),
                player = initial.player.copy(rentDebtMinor = 100, taskRemainder = 9),
                clock = GameClock(gameMillis = 5 * GAME_HOUR_MILLIS),
                workMillis = 5 * GAME_HOUR_MILLIS
            )
        ).open()
        val finish = FinishShiftUseCase(game.coordinator)
        val key = game.key("finish")
        val receipt = finish(key).ok()
        val result = (receipt.effect as GameEffect.ShiftFinished).result
        assertEquals(result, game.store.persisted?.lastShiftResult)

        assertEquals(240L, result.wageMinor)
        assertEquals(100L, result.debtPaidMinor)
        assertEquals(5 * GAME_HOUR_MILLIS, result.workedGameMillis)
        assertEquals(2, result.experienceGained)
        assertEquals(9, game.session.player.taskRemainder)
        assertEquals(290L, game.session.player.moneyMinor)
        assertEquals(
            listOf(TransactionCategory.WAGE, TransactionCategory.DEBT_PAYMENT),
            game.store.transactions.map { it.category }
        )
        assertEquals(receipt, finish(key).ok())
        finish(game.key("duplicate")).failsWith(GameError.NoActiveShift)
    }

    @Test
    fun halvesLowMoodBonusAndPaysZeroWithoutCorrectPlacements() = runTest {
        val initial = sampleSession()
        val lowMood = initial.copy(
            player = initial.player.copy(needs = Needs.of(100, 100, 19, 100))
        )
        val shifts = listOf(sampleShift(20, 5) to 220L, sampleShift(errors = 5) to 0L)
        for ((shift, expected) in shifts) {
            val game = Fixture(lowMood.copy(activeActivity = shift)).open()
            val receipt = FinishShiftUseCase(game.coordinator)(game.key("finish")).ok()
            assertEquals(expected, (receipt.effect as GameEffect.ShiftFinished).result.wageMinor)
        }
    }

    @Test
    fun rejectsInvalidKeyAndPreservesUnpaidShiftOnStorageFailure() = runTest {
        val game = Fixture(sampleSession().copy(activeActivity = sampleShift(20, 5))).open()
        val finish = FinishShiftUseCase(game.coordinator)
        finish(game.key("")).failsWith(GameError.InvalidInput("operationKey"))
        game.store.writeError = GameError.StorageFailure
        finish(game.key("finish")).failsWith(GameError.StorageFailure)
        assertEquals(150L, game.session.player.moneyMinor)
        assertEquals(0, game.store.transactions.size)
        assertEquals(sampleShift(20, 5), game.session.activeActivity)
    }
}
