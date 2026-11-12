package com.lastpaycheck.presentation.startup

import com.lastpaycheck.domain.model.CatalogManifest
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.RemoteFailure
import com.lastpaycheck.domain.repository.CatalogManifestRepository
import com.lastpaycheck.domain.usecase.GetCatalogManifest
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StartupViewModelTest {
    @Test
    fun retriesAfterOfflineAndServerErrorsAndPublishesTheNextSuccessfulResult() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = FakeRepository()
            val viewModel = StartupViewModel(GetCatalogManifest(repository))
            val offline = DomainResult.Failure(GameError.Remote(RemoteFailure.OFFLINE))
            val serverError = DomainResult.Failure(GameError.Remote(RemoteFailure.SERVER, 503))
            val manifest = CatalogManifest(
                version = "published-2",
                publishedAt = Instant.EPOCH,
                contentSha256 = "a".repeat(64),
                sources = emptyList()
            )

            assertNull(viewModel.catalog.value)
            viewModel.reloadCatalog()
            runCurrent()
            assertEquals(1, repository.requestCount)
            repository.results.send(offline)
            runCurrent()
            assertEquals(offline, viewModel.catalog.value)

            viewModel.reloadCatalog()
            assertNull(viewModel.catalog.value)
            repository.results.send(serverError)
            runCurrent()
            assertEquals(serverError, viewModel.catalog.value)

            viewModel.reloadCatalog()
            assertNull(viewModel.catalog.value)
            val success = DomainResult.Success(manifest)
            repository.results.send(success)
            runCurrent()
            assertEquals(success, viewModel.catalog.value)
            assertEquals(3, repository.requestCount)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class FakeRepository : CatalogManifestRepository {
        val results = Channel<DomainResult<CatalogManifest>>(Channel.UNLIMITED)
        var requestCount = 0
            private set

        override suspend fun getVersion(): String = error("Expected a manifest request")

        override suspend fun getManifest(): DomainResult<CatalogManifest> {
            requestCount++
            return results.receive()
        }
    }
}
