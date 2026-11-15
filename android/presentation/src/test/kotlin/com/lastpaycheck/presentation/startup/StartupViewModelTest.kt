package com.lastpaycheck.presentation.startup

import androidx.lifecycle.viewModelScope
import com.lastpaycheck.domain.model.CatalogManifest
import com.lastpaycheck.domain.model.CatalogSnapshot
import com.lastpaycheck.domain.model.ControlMode
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.GameSettings
import com.lastpaycheck.domain.model.RemoteFailure
import com.lastpaycheck.domain.repository.CatalogManifestRepository
import com.lastpaycheck.domain.repository.SettingsRepository
import com.lastpaycheck.domain.usecase.GetCatalogManifest
import com.lastpaycheck.domain.usecase.ObserveSettingsUseCase
import com.lastpaycheck.domain.usecase.UpdateSettingsUseCase
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
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
        val repository = FakeRepository()
        val settings = FakeSettings()
        val viewModel = StartupViewModel(
            GetCatalogManifest(repository),
            ObserveSettingsUseCase(settings),
            UpdateSettingsUseCase(settings)
        )
        try {
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
            val success = DomainResult.Success(CatalogSnapshot(manifest, false, Instant.EPOCH))
            repository.results.send(success)
            runCurrent()
            assertEquals(success, viewModel.catalog.value)
            assertEquals(3, repository.requestCount)
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    private class FakeRepository : CatalogManifestRepository {
        val results = Channel<DomainResult<CatalogSnapshot>>(Channel.UNLIMITED)
        var requestCount = 0
            private set

        override suspend fun getVersion(): String = error("Expected a manifest request")

        override suspend fun loadCatalog(): DomainResult<CatalogSnapshot> {
            requestCount++
            return results.receive()
        }
    }

    private class FakeSettings : SettingsRepository {
        private val state = MutableStateFlow<DomainResult<GameSettings>>(
            DomainResult.Success(GameSettings(ControlMode.TOUCH, 0.0, true, true))
        )

        override fun observe() = state

        override suspend fun update(settings: GameSettings): DomainResult<GameSettings> =
            DomainResult.Success(settings).also { state.value = it }
    }
}
