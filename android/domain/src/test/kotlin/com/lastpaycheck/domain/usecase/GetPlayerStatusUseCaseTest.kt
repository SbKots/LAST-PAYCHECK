package com.lastpaycheck.domain.usecase

import com.lastpaycheck.domain.model.GameError
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetPlayerStatusUseCaseTest {
    @Test
    fun readsLivePlayerStateAndReportsMissingSession() = runTest {
        val game = Fixture()
        val status = GetPlayerStatusUseCase(game.coordinator)
        status().failsWith(GameError.NoSession)
        game.open()
        game.tick(100).ok()
        assertEquals(14_400L, status().ok().clock.gameMillis)
        assertEquals(0L, game.store.persisted?.clock?.gameMillis)
        assertEquals(150L, status().ok().player.moneyMinor)
    }
}
