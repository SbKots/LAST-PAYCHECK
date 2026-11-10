package com.lastpaycheck.domain.usecase

import com.lastpaycheck.domain.model.GameError
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StartNewGameUseCaseTest {
    @Test
    fun startsFromConfirmedCatalogAndRequiresConsentToReplace() = runTest {
        val game = Fixture(initial = null)
        val start = StartNewGameUseCase(game.catalog, game.coordinator)

        val first = start("first", "scenario", "v1").ok()
        assertEquals(1L, first.clock.day)
        assertEquals(0, first.clock.minuteOfDay)
        assertEquals(150L, first.player.moneyMinor)
        assertEquals(helperJob.id, first.player.selectedJobId)
        start("second", "scenario", "v1").failsWith(GameError.ConfirmationRequired("first"))
        start("second", "scenario", "v1", "wrong").failsWith(GameError.RevisionConflict)
        assertEquals(first, game.store.persisted)

        assertEquals("second", start("second", "scenario", "v1", "first").ok().id)
        assertEquals("second", game.store.persisted?.id)
        assertEquals(emptyList<Any>(), game.store.transactions)
    }

    @Test
    fun invalidInputAndRepositoryFailureDoNotCreateSession() = runTest {
        val game = Fixture(initial = null)
        val start = StartNewGameUseCase(game.catalog, game.coordinator)
        start("", "scenario", "v1").failsWith(GameError.InvalidInput("sessionId"))
        start("new", "missing", "v1").failsWith(GameError.NotFound("missing"))
        game.store.writeError = GameError.StorageFailure
        start("new", "scenario", "v1").failsWith(GameError.StorageFailure)
        assertNull(game.coordinator.session.value)
        assertEquals(0, game.store.writes)
    }
}
