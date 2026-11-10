package com.lastpaycheck.domain.usecase

import com.lastpaycheck.domain.model.GAME_DAY_MILLIS
import com.lastpaycheck.domain.model.GameClock
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.ProductQuote
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class QuoteProductUseCaseTest {
    @Test
    fun pricesOneYearOfInflationFromThePinnedScenario() = runTest {
        val catalog = sampleCatalog(inflation = "10")
        val clock = GameClock(gameMillis = 365 * GAME_DAY_MILLIS)
        val session = sampleSession(catalog).copy(clock = clock, lastRentDay = 366)
        val game = Fixture(session, FakeCatalog(catalog)).open()
        val quote = QuoteProductUseCase(game.catalog, game.coordinator)

        assertEquals(ProductQuote(game.session.id, "food", 55), quote("food").ok())
    }

    @Test
    fun validatesProductAndPropagatesMissingCatalog() = runTest {
        val game = Fixture()
        val quote = QuoteProductUseCase(game.catalog, game.coordinator)
        quote("").failsWith(GameError.InvalidInput("productId"))
        quote("food").failsWith(GameError.NoSession)
        game.open()
        quote("missing").failsWith(GameError.NotFound("missing"))
        game.catalog.readError = GameError.CatalogUnavailable
        quote("food").failsWith(GameError.CatalogUnavailable)
        assertEquals(0, game.store.writes)
    }
}
