package com.lastpaycheck.domain.usecase

import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.PauseReason
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SaveProgressUseCaseTest {
    @Test
    fun checkpointsLiveProgressWithoutDuplicatingTheLedger() = runTest {
        val game = Fixture().open()
        game.buy(game.key("buy"), "food", 1, 50).ok()
        game.tick(100).ok()
        assertEquals(0L, game.store.persisted?.clock?.gameMillis)

        val saved = SaveProgressUseCase(game.coordinator)().ok()
        assertEquals(14_400L, saved.clock.gameMillis)
        assertEquals(saved, game.store.persisted)
        assertEquals(1, game.store.transactions.size)
    }

    @Test
    fun handlesMissingSessionAndFailedCheckpoint() = runTest {
        val empty = Fixture(initial = null)
        SaveProgressUseCase(empty.coordinator)().failsWith(GameError.NoSession)
        val game = Fixture().open()
        game.tick(100).ok()
        game.store.writeError = GameError.StorageFailure

        SaveProgressUseCase(game.coordinator)().failsWith(GameError.StorageFailure)
        assertEquals(0L, game.session.clock.gameMillis)
        assertEquals(setOf(PauseReason.STORAGE_FAILURE), game.session.clock.pauseReasons)
    }
}
