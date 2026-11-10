package com.lastpaycheck.domain.usecase

import com.lastpaycheck.domain.model.ActivityRequest
import com.lastpaycheck.domain.model.GAME_HOUR_MILLIS
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.Needs
import com.lastpaycheck.domain.model.WorkShift
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class StartShiftUseCaseTest {
    @Test
    fun startsWithPinnedTermsAndInitialBox() = runTest {
        val game = Fixture().open()
        game.startShift(game.key("work"), helperJob.id).ok()
        val shift = game.session.activeActivity as WorkShift
        assertEquals(helperJob, shift.job)
        assertEquals(10 * GAME_HOUR_MILLIS, shift.remainingGameMillis)
        assertEquals("work:0", shift.selectedBox.id)
        assertEquals(listOf("v1"), game.catalog.requestedVersions)
        assertEquals(0, game.store.transactions.size)
    }

    @Test
    fun enforcesJobLevelEnergyAndActivityExclusion() = runTest {
        val game = Fixture().open()
        game.startShift(game.key("empty"), "").failsWith(GameError.InvalidInput("jobId"))
        game.startShift(game.key("locked"), seniorJob.id).failsWith(GameError.UnavailableJob)
        game.activity(game.key("rest"), ActivityRequest.Rest).ok()
        game.startShift(game.key("busy"), helperJob.id).failsWith(GameError.Busy)

        val initial = sampleSession()
        val tiredPlayer = initial.player.copy(needs = Needs.of(80, 0, 80, 80))
        val tired = Fixture(initial.copy(player = tiredPlayer)).open()
        tired.startShift(tired.key("work"), helperJob.id).failsWith(GameError.InsufficientEnergy)
    }

    @Test
    fun propagatesCatalogAndStorageFailure() = runTest {
        val game = Fixture().open()
        game.catalog.readError = GameError.CatalogUnavailable
        game.startShift(game.key("work"), helperJob.id).failsWith(GameError.CatalogUnavailable)
        game.catalog.readError = null
        game.store.writeError = GameError.StorageFailure
        game.startShift(game.key("work"), helperJob.id).failsWith(GameError.StorageFailure)
        assertEquals(null, game.session.activeActivity)
    }
}
