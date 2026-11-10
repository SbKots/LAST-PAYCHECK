package com.lastpaycheck.domain.usecase

import com.lastpaycheck.domain.model.CatalogSource
import com.lastpaycheck.domain.model.GameError
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewNewGameUseCaseTest {
    @Test
    fun exposesCatalogStartConditionsWithoutWritingGameState() = runTest {
        val catalog = FakeCatalog(sampleCatalog(inflation = "5"))
        val preview = PreviewNewGameUseCase(catalog)
        val offered = preview("scenario").ok()
        assertEquals(150L, offered.initial.moneyMinor)
        assertEquals(CatalogSource.TRAINING, offered.scenario.source)
        assertEquals("v1", offered.scenario.version)
    }

    @Test
    fun rejectsUnknownScenarioInvalidVersionAndCatalogFailure() = runTest {
        val catalog = FakeCatalog()
        val preview = PreviewNewGameUseCase(catalog)
        preview("").failsWith(GameError.InvalidInput("scenarioId"))
        preview("scenario", "").failsWith(GameError.InvalidInput("catalogVersion"))
        preview("unknown").failsWith(GameError.NotFound("unknown"))
        catalog.readError = GameError.CatalogUnavailable
        preview("scenario").failsWith(GameError.CatalogUnavailable)
    }
}
