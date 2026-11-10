package com.lastpaycheck.domain.usecase

import com.lastpaycheck.domain.model.GAME_HOUR_MILLIS
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.PauseReason
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SetPauseUseCaseTest {
    @Test
    fun independentPauseReasonsDropInactiveIntervals() = runTest {
        val game = Fixture().open()
        val pause = SetPauseUseCase(game.coordinator)
        pause(PauseReason.MANUAL, true).ok()
        pause(PauseReason.BACKGROUND, true).ok()
        game.tick(250_000).ok()
        pause(PauseReason.BACKGROUND, false).ok()
        assertEquals(setOf(PauseReason.MANUAL), game.session.clock.pauseReasons)
        game.tick(250_000).ok()
        assertEquals(0L, game.session.clock.gameMillis)
        pause(PauseReason.MANUAL, false).ok()
        game.tick(25_000).ok()
        assertEquals(GAME_HOUR_MILLIS, game.session.clock.gameMillis)
    }

    @Test
    fun failedPauseStillStopsClockAndInternalReasonCannotBeSetByCaller() = runTest {
        val game = Fixture().open()
        val pause = SetPauseUseCase(game.coordinator)
        pause(PauseReason.STORAGE_FAILURE, true).failsWith(GameError.InvalidInput("reason"))
        game.store.writeError = GameError.StorageFailure
        pause(PauseReason.BACKGROUND, true).failsWith(GameError.StorageFailure)
        assertEquals(
            setOf(PauseReason.BACKGROUND, PauseReason.STORAGE_FAILURE),
            game.session.clock.pauseReasons
        )
        game.tick(250_000).ok()
        assertEquals(0L, game.session.clock.gameMillis)
    }

    @Test
    fun keepsBackgroundPauseWhenAutomaticCheckpointFailsBeforePausing() = runTest {
        val game = Fixture().open()
        val pause = SetPauseUseCase(game.coordinator)
        game.clock.now = 10_000
        game.store.writeError = GameError.StorageFailure
        pause(PauseReason.BACKGROUND, true).failsWith(GameError.StorageFailure)

        game.store.writeError = null
        SaveProgressUseCase(game.coordinator)().ok()
        assertEquals(setOf(PauseReason.BACKGROUND), game.session.clock.pauseReasons)
        assertEquals(0L, game.tick(1_000).ok().clock.gameMillis)
    }
}
