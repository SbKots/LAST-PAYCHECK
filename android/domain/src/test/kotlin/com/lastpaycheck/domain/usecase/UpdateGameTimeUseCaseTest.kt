package com.lastpaycheck.domain.usecase

import com.lastpaycheck.domain.engine.GameEngine
import com.lastpaycheck.domain.engine.LimitedEventsPolicy
import com.lastpaycheck.domain.model.ActivityRequest
import com.lastpaycheck.domain.model.EventDefinition
import com.lastpaycheck.domain.model.FinishReason
import com.lastpaycheck.domain.model.GAME_DAY_MILLIS
import com.lastpaycheck.domain.model.GAME_HOUR_MILLIS
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.Needs
import com.lastpaycheck.domain.model.SessionStatus
import com.lastpaycheck.domain.model.TransactionCategory
import com.lastpaycheck.domain.repository.RandomDraw
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateGameTimeUseCaseTest {
    @Test
    fun countsActiveTimeAndCheckpointsAtTenSeconds() = runTest {
        val game = Fixture().open()
        game.tick(9_999).ok()
        assertEquals(0, game.store.writes)
        game.tick(1).ok()
        assertEquals(1, game.store.writes)
        assertEquals(1_440_000L, game.store.persisted?.clock?.gameMillis)
        game.tick(15_000).ok()
        assertEquals(GAME_HOUR_MILLIS, game.session.clock.gameMillis)
        game.tick(575_000).ok()
        assertEquals(2L, game.session.clock.day)
        assertEquals(0, game.session.clock.minuteOfDay)
        assertEquals(50L, game.session.player.moneyMinor)
        assertEquals(TransactionCategory.RENT, game.store.transactions.single().category)
        ContinueGameUseCase(game.coordinator)().ok()
        game.tick(1).ok()
        assertEquals(1, game.store.transactions.size)
    }

    @Test
    fun elapsedTimeCompletesShiftAtTwoHundredFiftySeconds() = runTest {
        val game = Fixture().open()
        game.startShift(game.key("work"), helperJob.id).ok()
        game.tick(249_999).ok()
        assertEquals(144L, game.session.activeActivity?.remainingGameMillis)
        game.tick(1).ok()
        assertNull(game.session.activeActivity)
        assertEquals(10 * GAME_HOUR_MILLIS, game.session.workMillis)
        assertEquals(150L, game.session.player.moneyMinor)
        assertEquals(0L, game.store.persisted?.lastShiftResult?.wageMinor)
    }

    @Test
    fun realTimeAfterSleepCompletionUsesNormalSpeed() = runTest {
        val game = Fixture().open()
        game.activity(game.key("sleep"), ActivityRequest.Sleep).ok()
        game.tick(50_001).ok()
        assertEquals(8 * GAME_HOUR_MILLIS + 144, game.session.clock.gameMillis)
        assertNull(game.session.activeActivity)
    }

    @Test
    fun twoEmptyResourcesDoNotDoubleDamageAndTimeStopsAtDeath() = runTest {
        val initial = sampleSession()
        val player = initial.player.copy(needs = Needs.of(0, 0, 100, 5))
        val game = Fixture(initial.copy(player = player)).open()
        val ended = game.tick(50_000).ok()
        assertEquals(GAME_HOUR_MILLIS, ended.clock.gameMillis)
        assertEquals(FinishReason.HEALTH_DEPLETED, ended.finishReason)
        assertEquals(SessionStatus.FINISHED, game.store.persisted?.status)
        game.tick(1).failsWith(GameError.GameFinished)
    }

    @Test
    fun rentAccruesUnpaidBalanceAndIsNotReappliedAfterResume() = runTest {
        val catalog = sampleCatalog(money = 40)
        val game = Fixture(sampleSession(catalog), FakeCatalog(catalog)).open()
        game.tick(600_000).ok()
        assertEquals(0L, game.session.player.moneyMinor)
        assertEquals(60L, game.session.player.rentDebtMinor)
        assertEquals(-40L, game.store.transactions.single().amountMinor)
        ContinueGameUseCase(game.coordinator)().ok()
        game.tick(1).ok()
        assertEquals(60L, game.session.player.rentDebtMinor)
    }

    @Test
    fun optionalEventsRespectThreeDaysAndTenPercentAndSurviveResume() = runTest {
        val random = { state: Long, _: Int -> RandomDraw(0, state + 1) }
        val event = EventDefinition(
            "incident",
            "Broken box",
            moneyLossPercent = 10,
            healthLoss = 2,
            durationGameMillis = GAME_HOUR_MILLIS,
            extraWorkEnergyPerHour = 2
        )
        val catalog = sampleCatalog(money = 1000)
        val initial = sampleSession(catalog).copy(dailyRentMinor = 0)
        val eventPolicy = LimitedEventsPolicy(listOf(event), random)
        val engine = GameEngine(random = random, events = eventPolicy)
        val game = Fixture(initial, FakeCatalog(catalog), engine).open()
        repeat(3) { day ->
            game.tick(600_000).ok()
            if (day < 2) {
                val refreshed = game.session.copy(
                    player = game.session.player.copy(needs = defaultNeeds)
                )
                game.store.checkpoint(game.session.identity, refreshed).ok()
                ContinueGameUseCase(game.coordinator)().ok()
            }
        }
        assertEquals(3 * GAME_DAY_MILLIS, game.session.lastEvent?.occurredAt)
        assertEquals(100L, game.session.lastEvent?.moneyLostMinor)
        ContinueGameUseCase(game.coordinator)().ok()
        val beforeShift = game.session.player.needs.energy
        game.startShift(game.key("event-shift"), helperJob.id).ok()
        game.tick(50_000).ok()
        assertEquals(beforeShift - 10, game.session.player.needs.energy, 0.0)
        assertEquals(1, game.store.transactions.size)
        assertEquals(1, game.session.events.size)
    }

    @Test
    fun rejectsClockReversalAndKeepsConfirmedTimeOnWriteFailure() = runTest {
        val game = Fixture().open()
        game.tick(-1).failsWith(GameError.InvalidInput("activeMillis"))
        game.store.writeError = GameError.StorageFailure
        game.tick(10_001).failsWith(GameError.StorageFailure)
        assertEquals(0L, game.session.clock.gameMillis)
        assertEquals(0L, game.store.persisted?.clock?.gameMillis)
    }
}
