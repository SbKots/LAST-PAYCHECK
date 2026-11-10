package com.lastpaycheck.domain.usecase

import com.lastpaycheck.domain.model.GameClock
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.PauseReason
import com.lastpaycheck.domain.repository.CheckpointChoice
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContinueGameUseCaseTest {
    @Test
    fun restoresPreviousCheckpointWithItsJournalAndReceiptBoundary() = runTest {
        val game = Fixture().open()
        val first = game.key("first")
        val second = game.key("second")
        val receipt = game.buy(first, "food", 1, 50).ok()
        game.buy(second, "food", 1, 50).ok()
        game.store.corruptLatest = true
        val resume = ContinueGameUseCase(game.coordinator)

        resume().failsWith(GameError.InvalidSnapshot(previousAvailable = true))
        assertEquals(50L, game.session.player.moneyMinor)
        val restored = resume(CheckpointChoice.PREVIOUS).ok()
        assertEquals(100L, restored.player.moneyMinor)
        assertEquals(1, restored.inventory.quantity("food"))
        assertEquals(1, game.store.transactions.size)
        assertEquals(receipt, game.store.findReceipt(first).ok())
        assertNull(game.store.findReceipt(second).ok())
    }

    @Test
    fun retainsManualPauseButClearsLifecyclePausesOnLoad() = runTest {
        val clock = GameClock(pauseReasons = PauseReason.entries.toSet())
        val game = Fixture(sampleSession().copy(clock = clock)).open()

        assertEquals(setOf(PauseReason.MANUAL), game.session.clock.pauseReasons)
    }

    @Test
    fun rejectsMissingMalformedAndUnavailableSnapshots() = runTest {
        val empty = Fixture(initial = null)
        ContinueGameUseCase(empty.coordinator)().failsWith(GameError.NoSession)
        ContinueGameUseCase(empty.coordinator)(CheckpointChoice.PREVIOUS)
            .failsWith(GameError.NoSession)
        val malformed = Fixture(sampleSession().copy(revision = -1))
        val resume = ContinueGameUseCase(malformed.coordinator)
        resume().failsWith(GameError.InvalidSnapshot(previousAvailable = false))
        malformed.store.loadError = GameError.StorageFailure
        resume().failsWith(GameError.StorageFailure)
        assertNull(malformed.coordinator.session.value)
    }
}
