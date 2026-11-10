package com.lastpaycheck.domain.usecase

import com.lastpaycheck.domain.model.GameError
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SelectJobUseCaseTest {
    @Test
    fun assignsAvailableJobWithoutChangingBalance() = runTest {
        val initial = sampleSession()
        val game = Fixture(initial.copy(player = initial.player.copy(experience = 100))).open()
        val select = SelectJobUseCase(game.catalog, game.coordinator)
        select(game.key("select"), pickerJob.id).ok()
        assertEquals(pickerJob.id, game.session.player.selectedJobId)
        assertEquals(150L, game.session.player.moneyMinor)
        assertEquals(0, game.store.transactions.size)
    }

    @Test
    fun rejectsBlankMissingLockedAndInShiftSelections() = runTest {
        val game = Fixture().open()
        val select = SelectJobUseCase(game.catalog, game.coordinator)
        select(game.key("empty"), "").failsWith(GameError.InvalidInput("jobId"))
        select(game.key("missing"), "unknown").failsWith(GameError.NotFound("unknown"))
        select(game.key("locked"), pickerJob.id).failsWith(GameError.UnavailableJob)
        game.startShift(game.key("shift"), helperJob.id).ok()
        select(game.key("busy"), helperJob.id).failsWith(GameError.Busy)
    }

    @Test
    fun failedRepositoryWriteDoesNotChangeJob() = runTest {
        val initial = sampleSession()
        val game = Fixture(initial.copy(player = initial.player.copy(experience = 100))).open()
        game.store.writeError = GameError.StorageFailure
        SelectJobUseCase(game.catalog, game.coordinator)(game.key("select"), pickerJob.id)
            .failsWith(GameError.StorageFailure)
        assertEquals(helperJob.id, game.session.player.selectedJobId)
    }
}
