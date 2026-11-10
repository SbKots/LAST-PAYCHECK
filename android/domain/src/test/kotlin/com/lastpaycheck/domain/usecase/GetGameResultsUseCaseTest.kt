package com.lastpaycheck.domain.usecase

import com.lastpaycheck.domain.model.FinishReason
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.Needs
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetGameResultsUseCaseTest {
    @Test
    fun requiresFinishedSessionAndReportsTheCauseOfDeath() = runTest {
        val initial = sampleSession()
        val player = initial.player.copy(needs = Needs.of(0, 0, 100, 1))
        val game = Fixture(initial.copy(player = player)).open()
        val results = GetGameResultsUseCase(game.store)
        results().failsWith(GameError.GameInProgress)
        game.tick(5_000).ok()
        assertEquals(FinishReason.HEALTH_DEPLETED, results().ok().finishReason)
        game.store.journalError = GameError.StorageFailure
        results().failsWith(GameError.StorageFailure)
    }
}
