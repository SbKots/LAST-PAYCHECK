package com.lastpaycheck.domain.usecase

import com.lastpaycheck.domain.model.GameError
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RefreshCatalogUseCaseTest {
    @Test
    fun refreshesLatestButKeepsTheSessionOnItsPinnedVersion() = runTest {
        val game = Fixture().open()
        game.catalog.candidate = sampleCatalog(version = "v2", inflation = "10")
        val latest = RefreshCatalogUseCase(game.catalog)().ok()
        assertEquals("v2", latest.version)
        assertEquals("v1", game.session.catalogVersion)
        assertEquals("v1", game.session.scenario.version)
        game.buy(game.key("purchase"), "food", 1, 50).ok()
        assertEquals("v1", game.catalog.requestedVersions.last())
    }

    @Test
    fun invalidOrUnavailableRefreshKeepsTheLastValidVersion() = runTest {
        val catalog = FakeCatalog()
        val refresh = RefreshCatalogUseCase(catalog)
        catalog.candidate = sampleCatalog("v2", "-100")
        refresh().failsWith(GameError.InvalidCatalog)
        assertEquals("v1", catalog.latest)
        catalog.refreshError = GameError.CatalogUnavailable
        refresh().failsWith(GameError.CatalogUnavailable)
        assertEquals("v1", catalog.latest)
    }
}
