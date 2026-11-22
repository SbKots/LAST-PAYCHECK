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
import com.lastpaycheck.domain.model.GameEffect
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
import com.lastpaycheck.domain.model.WorkShift
import com.lastpaycheck.domain.repository.ActiveGameClock
import com.lastpaycheck.domain.repository.CatalogRepository
import com.lastpaycheck.domain.repository.CheckpointChoice
import com.lastpaycheck.domain.repository.GameSessionRepository
import com.lastpaycheck.domain.usecase.BuyProductUseCase
import com.lastpaycheck.domain.usecase.ContinueGameUseCase
import com.lastpaycheck.domain.usecase.FinishShiftUseCase
import com.lastpaycheck.domain.usecase.GetPlayerStatusUseCase
import com.lastpaycheck.domain.usecase.PlaceBoxUseCase
import com.lastpaycheck.domain.usecase.PreviewNewGameUseCase
import com.lastpaycheck.domain.usecase.QuoteProductUseCase
import com.lastpaycheck.domain.usecase.SaveProgressUseCase
import com.lastpaycheck.domain.usecase.SetPauseUseCase
import com.lastpaycheck.domain.usecase.StartActivityUseCase
import com.lastpaycheck.domain.usecase.StartNewGameUseCase
import com.lastpaycheck.domain.usecase.StartShiftUseCase
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
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    @Test
    fun characterNameValidationSavesOnlyValidInputAndSurvivesReload() = verify { model, store ->
        createSession(model)
        val session = checkNotNull(store.latest)
        model.rename(session.id, " ")
        assertEquals(com.lastpaycheck.domain.model.ValidationIssue.REQUIRED, model.nameError.value)
        model.rename(session.id, "Максим")
        model.rename(session.id, "Другое имя")
        runCurrent()
        assertEquals("Максим", store.latest?.player?.name)
        assertTrue(model.nameSaved.value)
        assertNull(model.nameError.value)
        val reloaded = newModel(store)
        try {
            runCurrent()
            assertEquals(
                "Максим",
                (reloaded.state.value as MainUiState.Content).session.player.name
            )
        } finally {
            reloaded.viewModelScope.cancel()
        }
    }

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

    @Test
    fun workLoadsCatalogAndPaysOnlyThroughSavedShiftResult() = verify { model, store ->
        createSession(model)
        val initial = checkNotNull(store.latest)
        assertNull(model.sectionCatalog.value)
        model.loadSection(initial.id, initial.clock.day)
        runCurrent()
        val job = section(model).gameCatalog.jobs.first()
        assertEquals(initial.player.selectedJobId, job.id)
        assertEquals(400L, job.basePayMinor)
        model.startShift(initial.id, job.id)
        model.startShift(initial.id, job.id)
        runCurrent()
        val shift = checkNotNull(store.latest).activeActivity as WorkShift
        assertEquals(job, shift.job)
        model.setHomeVisible(true)
        advanceTimeBy(2000)
        model.placeBox(initial.id, shift.selectedBox.id, shift.selectedBox.category)
        runCurrent()
        model.finishShift(initial.id)
        runCurrent()
        val paid = checkNotNull(store.latest)
        val result = checkNotNull(paid.lastShiftResult)
        assertEquals(1, result.correct)
        assertTrue(result.wageMinor > 0)
        assertEquals(initial.player.moneyMinor + result.wageMinor, paid.player.moneyMinor)
        assertTrue(paid.player.needs.energy < initial.player.needs.energy)
        assertTrue(paid.clock.gameMillis > initial.clock.gameMillis)
        assertNull(paid.activeActivity)
        assertEquals(paid, restoreSession(store, initial.id))
    }

    @Test
    fun workRejectsSecondShiftAndReportsStorageFailure() = verify { model, store ->
        createSession(model)
        val session = checkNotNull(store.latest)
        model.startShift(session.id, session.player.selectedJobId)
        runCurrent()
        model.startShift(session.id, session.player.selectedJobId)
        runCurrent()
        assertEquals(GameError.Busy, (model.state.value as MainUiState.Error).reason)
        model.retry()
        runCurrent()
        store.writeError = true
        model.finishShift(session.id)
        runCurrent()
        assertEquals(GameError.StorageFailure, (model.state.value as MainUiState.Error).reason)
        assertTrue(checkNotNull(store.latest).activeActivity is WorkShift)
    }

    @Test
    fun offlinePurchaseUsesDomainQuoteAndOnlyCommitsOneReceiptPerConfirmation() =
        verify { model, store ->
            createSession(model)
            val initial = checkNotNull(store.latest)
            model.loadSection(initial.id, initial.clock.day)
            runCurrent()
            val item = section(model).products.single()
            assertEquals(50L, item.quote.unitPriceMinor)
            model.buy(initial.id, item, 2, "confirmation")
            model.buy(initial.id, item, 2, "confirmation")
            runCurrent()
            val purchased = checkNotNull(store.latest)
            model.buy(initial.id, item, 2, "confirmation")
            runCurrent()
            assertEquals(purchased, store.latest)
            assertEquals(900L, purchased.player.moneyMinor)
            assertEquals(2, purchased.inventory.quantity(item.product.id))
            assertEquals(initial.player.needs, purchased.player.needs)
            assertEquals(GameEffect.Purchase(item.product.id, 2, 100), model.sectionEffect.value)
            assertEquals(purchased, restoreSession(store, initial.id))
        }

    @Test
    fun unaffordablePurchaseCanReturnToCatalogWithoutChangingTheSave() = verify { model, store ->
        createSession(model)
        val initial = checkNotNull(store.latest)
        model.loadSection(initial.id, initial.clock.day)
        runCurrent()
        model.buy(initial.id, section(model).products.single(), 21, "too-expensive")
        runCurrent()
        assertEquals(
            GameError.InsufficientFunds(1050, 1000),
            (model.state.value as MainUiState.Error).reason
        )
        assertEquals(initial, store.latest)
        model.retry()
        runCurrent()
        assertEquals(MainUiState.Content(initial), model.state.value)
        assertEquals(1, section(model).products.size)
    }

    @Test
    fun catalogFailureIsVisibleAndCanBeRetried() = verify { model, store ->
        createSession(model)
        val session = checkNotNull(store.latest)
        store.catalogError = true
        model.loadSection(session.id, session.clock.day)
        runCurrent()
        assertEquals(DomainResult.Failure(GameError.CatalogUnavailable), model.sectionCatalog.value)
        store.catalogError = false
        model.loadSection(session.id, session.clock.day)
        runCurrent()
        assertEquals(session.id, section(model).sessionId)
    }

    @Test
    fun workAndShopRoutesRoundTripTheSessionIdAndRejectMissingSession() = verify { model, store ->
        val sessionId = "room/save #1"
        assertEquals(
            GameRoutes.Work(sessionId),
            Json.decodeFromString<GameRoutes.Work>(
                Json.encodeToString(GameRoutes.Work.serializer(), GameRoutes.Work(sessionId))
            )
        )
        assertEquals(
            GameRoutes.Shop(sessionId),
            Json.decodeFromString<GameRoutes.Shop>(
                Json.encodeToString(GameRoutes.Shop.serializer(), GameRoutes.Shop(sessionId))
            )
        )
        model.restoreHome(sessionId)
        runCurrent()
        assertEquals(MainUiState.Empty, model.state.value)
        createSession(model)
        assertEquals(MainUiState.Empty, model.state.value.forSession(sessionId))
        model.restoreHome(sessionId)
        runCurrent()
        assertEquals(MainUiState.Empty, model.state.value)
        assertTrue(checkNotNull(store.latest).id != sessionId)
    }

    private fun section(model: MainViewModel) =
        (model.sectionCatalog.value as DomainResult.Success).value

    private fun TestScope.restoreSession(store: SessionStore, sessionId: String): GameSession {
        val restarted = newModel(store)
        return try {
            runCurrent()
            restarted.restoreHome(sessionId)
            runCurrent()
            (restarted.state.value as MainUiState.Content).session
        } finally {
            restarted.viewModelScope.cancel()
        }
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
        val catalog = TestCatalog { store.catalogError }
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
            SaveProgressUseCase(coordinator),
            SectionActions(
                StartShiftUseCase(catalog, coordinator),
                PlaceBoxUseCase(coordinator),
                FinishShiftUseCase(coordinator),
                QuoteProductUseCase(catalog, coordinator),
                BuyProductUseCase(catalog, coordinator)
            ),
            com.lastpaycheck.domain.usecase.RenameCharacterUseCase(coordinator)
        )
    }

    private class SessionStore : GameSessionRepository {
        var latest: GameSession? = null
        var replacements = 0
        var readError = false
        var writeError = false
        var catalogError = false
        private val receipts = mutableMapOf<OperationKey, OperationReceipt>()

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
            DomainResult.Success(receipts[key])

        override suspend fun commit(
            expected: SessionRevision,
            key: OperationKey,
            transition: GameTransition
        ): DomainResult<CommittedOperation> {
            if (writeError) return DomainResult.Failure(GameError.StorageFailure)
            val session = transition.session.copy(revision = expected.revision + 1)
            latest = session
            val receipt = OperationReceipt(key, session.revision, transition.effect)
            receipts[key] = receipt
            return DomainResult.Success(CommittedOperation(session, receipt))
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

    private class TestCatalog(private val unavailable: () -> Boolean) : CatalogRepository {
        override suspend fun getVersion() = "training-test"

        override suspend fun get(version: String?) =
            if (unavailable()) DomainResult.Failure(GameError.CatalogUnavailable) else refresh()

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
