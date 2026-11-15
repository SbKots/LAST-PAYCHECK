package com.lastpaycheck.presentation.game

import androidx.lifecycle.viewModelScope
import com.lastpaycheck.domain.engine.GameEngine
import com.lastpaycheck.domain.engine.GameSessionCoordinator
import com.lastpaycheck.domain.model.ActivityRequest
import com.lastpaycheck.domain.model.CatalogSource
import com.lastpaycheck.domain.model.CommittedOperation
import com.lastpaycheck.domain.model.Course
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.EconomicScenario
import com.lastpaycheck.domain.model.FinancialTransaction
import com.lastpaycheck.domain.model.GameCatalog
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.GameSession
import com.lastpaycheck.domain.model.GameTransition
import com.lastpaycheck.domain.model.InitialConditions
import com.lastpaycheck.domain.model.Job
import com.lastpaycheck.domain.model.Needs
import com.lastpaycheck.domain.model.OperationKey
import com.lastpaycheck.domain.model.OperationReceipt
import com.lastpaycheck.domain.model.Product
import com.lastpaycheck.domain.model.SessionRevision
import com.lastpaycheck.domain.model.TimedActivity
import com.lastpaycheck.domain.repository.ActiveGameClock
import com.lastpaycheck.domain.repository.CatalogRepository
import com.lastpaycheck.domain.repository.CheckpointChoice
import com.lastpaycheck.domain.repository.GameSessionRepository
import com.lastpaycheck.domain.usecase.ContinueGameUseCase
import com.lastpaycheck.domain.usecase.GetPlayerStatusUseCase
import com.lastpaycheck.domain.usecase.PreviewNewGameUseCase
import com.lastpaycheck.domain.usecase.SaveProgressUseCase
import com.lastpaycheck.domain.usecase.SetPauseUseCase
import com.lastpaycheck.domain.usecase.StartActivityUseCase
import com.lastpaycheck.domain.usecase.StartNewGameUseCase
import com.lastpaycheck.domain.usecase.UpdateGameTimeUseCase
import java.math.BigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    @Test
    fun loadingBecomesEmptyWhenNoSaveExists() = verify { model, _ ->
        assertEquals(MainUiState.Loading, model.state.value)
        runCurrent()
        assertEquals(MainUiState.Empty, model.state.value)
    }

    @Test
    fun readFailureCanBeRetried() = verify { model, store ->
        store.readError = true
        runCurrent()
        assertEquals(MainUiState.Error(GameError.StorageFailure), model.state.value)
        store.readError = false
        model.retry()
        runCurrent()
        assertEquals(MainUiState.Empty, model.state.value)
    }

    @Test
    fun createsOnlyOnConfirmationAndIgnoresDuplicateRequests() = verify { model, store ->
        runCurrent()
        model.prepareNewGame()
        runCurrent()
        assertNull(store.latest)
        val opened = mutableListOf<GameSession>()
        model.createGame { opened += it }
        model.createGame { opened += it }
        runCurrent()
        model.createGame { opened += it }
        runCurrent()
        assertEquals(1, store.replacements)
        assertEquals(store.latest, opened.single())
        assertEquals(MainUiState.Content(opened.single()), model.state.value)
    }

    @Test
    fun loadsAndContinuesTheSavedSessionWithoutReplacingIt() = verify { model, store ->
        createSession(model)
        val saved = checkNotNull(store.latest)
        val restarted = newModel(store)
        try {
            assertEquals(MainUiState.Loading, restarted.state.value)
            runCurrent()
            assertEquals(MainUiState.Content(saved), restarted.state.value)
            var opened: GameSession? = null
            restarted.resumeGame(CheckpointChoice.LATEST) { opened = it }
            runCurrent()
            assertEquals(saved, opened)
            assertEquals(1, store.replacements)
        } finally {
            restarted.viewModelScope.cancel()
        }
    }

    @Test
    fun actionUpdatesContentAndCommitsTheSnapshot() = verify { model, store ->
        createSession(model)
        model.start(ActivityRequest.Rest)
        runCurrent()
        val session = (model.state.value as MainUiState.Content).session
        assertTrue(session.activeActivity is TimedActivity.Rest)
        assertEquals(session, store.latest)
    }

    @Test
    fun checkpointFailureIsShownAndRetrySaves() = verify { model, store ->
        createSession(model)
        store.writeError = true
        model.save()
        runCurrent()
        assertEquals(GameError.StorageFailure, (model.state.value as MainUiState.Error).reason)
        store.writeError = false
        model.retry()
        runCurrent()
        assertEquals(MainUiState.Content(checkNotNull(store.latest)), model.state.value)
    }

    @Test
    fun timeOnlyAdvancesWhileHomeIsVisible() = verify { model, store ->
        createSession(model)
        model.setHomeVisible(true)
        advanceTimeBy(1500)
        runCurrent()
        assertTrue((model.state.value as MainUiState.Content).session.clock.gameMillis > 0)
        model.setHomeVisible(false)
        runCurrent()
        val savedMillis = checkNotNull(store.latest).clock.gameMillis
        advanceTimeBy(5000)
        runCurrent()
        assertEquals(savedMillis, checkNotNull(store.latest).clock.gameMillis)
        model.setHomeVisible(true)
        advanceTimeBy(1050)
        runCurrent()
        val resumed = (model.state.value as MainUiState.Content).session.clock.gameMillis
        assertTrue(resumed > savedMillis)
        assertTrue(resumed - savedMillis < 200_000)
    }

    private fun verify(block: suspend TestScope.(MainViewModel, SessionStore) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = SessionStore()
        val model = newModel(store)
        try {
            block(model, store)
        } finally {
            model.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    private fun TestScope.createSession(model: MainViewModel) {
        runCurrent()
        model.prepareNewGame()
        runCurrent()
        model.createGame {}
        runCurrent()
    }

    private fun TestScope.newModel(store: SessionStore): MainViewModel {
        val catalog = TestCatalog()
        val clock = ActiveGameClock { testScheduler.currentTime }
        val coordinator = GameSessionCoordinator(GameEngine(), store, clock)
        return MainViewModel(
            store,
            catalog,
            clock,
            PreviewNewGameUseCase(catalog),
            StartNewGameUseCase(catalog, coordinator),
            ContinueGameUseCase(coordinator),
            GetPlayerStatusUseCase(coordinator),
            StartActivityUseCase(catalog, coordinator),
            UpdateGameTimeUseCase(coordinator),
            SetPauseUseCase(coordinator),
            SaveProgressUseCase(coordinator)
        )
    }

    private class SessionStore : GameSessionRepository {
        var latest: GameSession? = null
        var replacements = 0
        var readError = false
        var writeError = false

        override suspend fun load(choice: CheckpointChoice): DomainResult<GameSession> = when {
            readError -> DomainResult.Failure(GameError.StorageFailure)
            else -> latest?.let { DomainResult.Success(it) }
                ?: DomainResult.Failure(GameError.NoSession)
        }

        override suspend fun restorePrevious(
            checkpoint: SessionRevision
        ): DomainResult<GameSession> = DomainResult.Failure(GameError.NoSession)

        override suspend fun replace(
            expected: SessionRevision?,
            session: GameSession
        ): DomainResult<GameSession> {
            replacements++
            latest = session
            return DomainResult.Success(session)
        }

        override suspend fun findReceipt(key: OperationKey): DomainResult<OperationReceipt?> =
            DomainResult.Success(null)

        override suspend fun commit(
            expected: SessionRevision,
            key: OperationKey,
            transition: GameTransition
        ): DomainResult<CommittedOperation> {
            val session = transition.session.copy(revision = expected.revision + 1)
            latest = session
            return DomainResult.Success(
                CommittedOperation(
                    session,
                    OperationReceipt(key, session.revision, transition.effect)
                )
            )
        }

        override suspend fun checkpoint(
            expected: SessionRevision,
            session: GameSession
        ): DomainResult<GameSession> {
            if (writeError) return DomainResult.Failure(GameError.StorageFailure)
            val saved = session.copy(revision = expected.revision + 1)
            latest = saved
            return DomainResult.Success(saved)
        }

        override suspend fun readTransactions(
            revision: SessionRevision
        ): DomainResult<List<FinancialTransaction>> = DomainResult.Success(emptyList())
    }

    private class TestCatalog : CatalogRepository {
        override suspend fun getVersion() = "training-test"

        override suspend fun get(version: String?) = refresh()

        override suspend fun refresh() = DomainResult.Success(
            GameCatalog(
                version = "training-test",
                source = CatalogSource.TRAINING,
                initial = InitialConditions(1000, Needs.of(100, 100, 100, 100), "job-1", 100),
                jobs = (1..3).map { Job("job-$it", "Warehouse", 0, 1, 400, 100, 40, 2) },
                products = listOf(Product("food", 50, 30, 10)),
                courses = listOf(Course("course", 100, 1, 100)),
                scenarios = listOf(
                    EconomicScenario(
                        "training",
                        "training-test",
                        "RUS",
                        2026,
                        BigDecimal.ZERO,
                        CatalogSource.TRAINING
                    )
                )
            )
        )
    }
}
