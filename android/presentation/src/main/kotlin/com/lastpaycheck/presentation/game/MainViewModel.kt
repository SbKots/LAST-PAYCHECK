package com.lastpaycheck.presentation.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lastpaycheck.domain.model.ActivityRequest
import com.lastpaycheck.domain.model.BoxCategory
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.GameEffect
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.GameSession
import com.lastpaycheck.domain.model.NewGamePreview
import com.lastpaycheck.domain.model.OperationKey
import com.lastpaycheck.domain.model.OperationReceipt
import com.lastpaycheck.domain.model.PauseReason
import com.lastpaycheck.domain.model.Product
import com.lastpaycheck.domain.model.SessionStatus
import com.lastpaycheck.domain.model.ValidationIssue
import com.lastpaycheck.domain.model.VehicleAction
import com.lastpaycheck.domain.model.VehicleCatalog
import com.lastpaycheck.domain.model.WorkShift
import com.lastpaycheck.domain.repository.ActiveGameClock
import com.lastpaycheck.domain.repository.CatalogRepository
import com.lastpaycheck.domain.repository.CheckpointChoice
import com.lastpaycheck.domain.repository.GameSessionRepository
import com.lastpaycheck.domain.usecase.ContinueGameUseCase
import com.lastpaycheck.domain.usecase.GetFoodPreviewUseCase
import com.lastpaycheck.domain.usecase.GetPlayerStatusUseCase
import com.lastpaycheck.domain.usecase.PreviewNewGameUseCase
import com.lastpaycheck.domain.usecase.RenameCharacterUseCase
import com.lastpaycheck.domain.usecase.SaveProgressUseCase
import com.lastpaycheck.domain.usecase.SetPauseUseCase
import com.lastpaycheck.domain.usecase.StartActivityUseCase
import com.lastpaycheck.domain.usecase.StartNewGameUseCase
import com.lastpaycheck.domain.usecase.UpdateGameTimeUseCase
import com.lastpaycheck.domain.usecase.ValidateCharacterName
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal sealed interface MainUiState {
    data object Loading : MainUiState
    data object Empty : MainUiState
    data class Content(val session: GameSession) : MainUiState
    data class Error(val reason: GameError, val session: GameSession? = null) : MainUiState
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val sessions: GameSessionRepository,
    private val catalog: CatalogRepository,
    private val clock: ActiveGameClock,
    private val previewGame: PreviewNewGameUseCase,
    private val startGame: StartNewGameUseCase,
    private val continueGame: ContinueGameUseCase,
    private val playerStatus: GetPlayerStatusUseCase,
    private val startActivity: StartActivityUseCase,
    private val updateTime: UpdateGameTimeUseCase,
    private val setPause: SetPauseUseCase,
    private val saveProgress: SaveProgressUseCase,
    private val sections: SectionActions,
    private val renameCharacter: RenameCharacterUseCase,
    private val getFoodPreview: GetFoodPreviewUseCase = GetFoodPreviewUseCase(),
    private val vehicleActions: VehicleActions? = null
) : ViewModel() {
    private val mutableState = MutableStateFlow<MainUiState>(MainUiState.Loading)
    internal val state = mutableState.asStateFlow()
    private val mutablePreview = MutableStateFlow<DomainResult<NewGamePreview>?>(null)
    internal val preview = mutablePreview.asStateFlow()
    private val mutableCatalog = MutableStateFlow<DomainResult<SectionCatalog>?>(null)
    internal val sectionCatalog = mutableCatalog.asStateFlow()
    private val mutableEffect = MutableStateFlow<GameEffect?>(null)
    internal val sectionEffect = mutableEffect.asStateFlow()
    private val mutableBusy = MutableStateFlow(false)
    internal val sectionBusy = mutableBusy.asStateFlow()
    private val mutableVehicles = MutableStateFlow<DomainResult<VehicleCatalog>?>(null)
    internal val vehicleCatalog = mutableVehicles.asStateFlow()
    private val mutableVehicleError = MutableStateFlow<GameError?>(null)
    internal val vehicleError = mutableVehicleError.asStateFlow()

    internal fun loadVehicles() {
        viewModelScope.launch {
            mutableVehicles.value = vehicleActions?.catalog?.invoke()
                ?: DomainResult.Failure(GameError.CatalogUnavailable)
        }
    }

    internal fun vehicleAction(
        action: VehicleAction,
        operationId: String,
        onSuccess: () -> Unit = {}
    ) = perform {
        val current = currentSession() ?: return@perform
        mutableBusy.value = true
        mutableVehicleError.value = null
        try {
            val result = vehicleActions?.execute(OperationKey(current.id, operationId), action)
                ?: DomainResult.Failure(GameError.CatalogUnavailable)
            when (result) {
                is DomainResult.Success -> {
                    publish(playerStatus())
                    onSuccess()
                }
                is DomainResult.Failure -> {
                    mutableVehicleError.value = result.error
                    publish(playerStatus())
                }
            }
        } finally {
            mutableBusy.value = false
        }
    }
    private var operation: Job? = null
    private var ticker: Job? = null
    private var homeVisible = false
    private var activeSessionId: String? = null
    private var retryAction: (() -> Unit)? = null
    private var catalogLoad: Job? = null
    internal fun foodPreview(sessionId: String, product: Product) =
        currentSession()?.takeIf { it.id == sessionId }?.let { getFoodPreview(it, product) }
            ?: DomainResult.Failure(GameError.NoSession)
    private val mutableNameError = MutableStateFlow<ValidationIssue?>(null)
    internal val nameError = mutableNameError.asStateFlow()
    private val mutableNameSaved = MutableStateFlow(false)
    internal val nameSaved = mutableNameSaved.asStateFlow()

    internal fun clearNameFeedback() {
        mutableNameError.value = null
        mutableNameSaved.value = false
    }

    internal fun rename(sessionId: String, name: String) {
        if (mutableBusy.value) return
        mutableNameSaved.value = false
        mutableNameError.value = ValidateCharacterName()(name)
        if (mutableNameError.value != null) return
        mutableBusy.value = true
        val running = operation
        operation = viewModelScope.launch {
            try {
                running?.join()
                when (val result = renameCharacter(sessionId, name)) {
                    is DomainResult.Success -> {
                        publish(result)
                        mutableNameSaved.value = true
                    }
                    is DomainResult.Failure -> showError(result.error)
                }
            } finally {
                mutableBusy.value = false
            }
        }
    }

    internal fun startCourse(sessionId: String, courseId: String) =
        sectionCommand(sessionId) { key, _ -> startActivity(key, ActivityRequest.Course(courseId)) }

    init {
        loadSavedGame()
    }

    internal fun loadSavedGame() = perform {
        mutableState.value = MainUiState.Loading
        retryAction = ::loadSavedGame
        publish(sessions.load(CheckpointChoice.LATEST))
    }

    internal fun prepareNewGame() = perform {
        mutablePreview.value = null
        val available = catalog.get()
        mutablePreview.value = when (available) {
            is DomainResult.Failure -> available
            is DomainResult.Success -> {
                val scenario = available.value.scenarios.firstOrNull()
                if (scenario == null) {
                    DomainResult.Failure(GameError.CatalogUnavailable)
                } else {
                    previewGame(scenario.id, available.value.version)
                }
            }
        }
    }

    internal fun createGame(onOpened: (GameSession) -> Unit) = perform {
        val selection = (mutablePreview.value as? DomainResult.Success)?.value ?: return@perform
        val existingId = (state.value as? MainUiState.Content)?.session?.id
        val sessionId = UUID.randomUUID().toString()
        mutablePreview.value = null
        mutableState.value = MainUiState.Loading
        retryAction = ::loadSavedGame
        val result =
            startGame(sessionId, selection.scenario.id, selection.scenario.version, existingId)
        if (result is DomainResult.Failure) {
            mutablePreview.value = result
            publish(sessions.load(CheckpointChoice.LATEST))
        } else {
            open(result, onOpened)
        }
    }

    internal fun resumeGame(choice: CheckpointChoice, onOpened: (GameSession) -> Unit): Unit =
        perform {
            mutableState.value = MainUiState.Loading
            retryAction = { resumeGame(choice, onOpened) }
            open(continueGame(choice), onOpened)
        }

    internal fun restoreHome(sessionId: String) {
        viewModelScope.launch {
            operation?.join()
            perform {
                if (activeSessionId == sessionId) return@perform
                activeSessionId = null
                updateTicker()
                retryAction = { restoreHome(sessionId) }
                mutableState.value = MainUiState.Loading
                val loaded = sessions.load(CheckpointChoice.LATEST)
                if (loaded is DomainResult.Success && loaded.value.id == sessionId) {
                    open(continueGame(), {})
                } else {
                    val result = loaded as? DomainResult.Failure
                        ?: DomainResult.Failure(GameError.NoSession)
                    publish(result)
                }
            }
        }
    }

    internal fun loadSection(sessionId: String, day: Long) {
        if (activeSessionId != sessionId) return
        catalogLoad?.cancel()
        mutableCatalog.value = null
        catalogLoad = viewModelScope.launch {
            val session = currentSession()?.takeIf { it.id == sessionId } ?: return@launch
            val result = catalog.get(session.catalogVersion)
            if (result is DomainResult.Failure) {
                mutableCatalog.value = result
                return@launch
            }
            val available = (result as DomainResult.Success).value
            val products = available.products.map { product ->
                when (val quote = sections.quote(product.id)) {
                    is DomainResult.Success -> StoreProduct(product, quote.value)
                    is DomainResult.Failure -> {
                        mutableCatalog.value = quote
                        return@launch
                    }
                }
            }
            mutableCatalog.value =
                DomainResult.Success(SectionCatalog(sessionId, day, available, products))
        }
    }

    internal fun startShift(sessionId: String, jobId: String) = sectionCommand(
        sessionId
    ) { key, _ ->
        sections.startShift(key, jobId)
    }

    internal fun placeBox(sessionId: String, boxId: String, zone: BoxCategory) =
        sectionCommand(sessionId) { key, session ->
            if ((session.activeActivity as? WorkShift)?.selectedBox?.id != boxId) {
                DomainResult.Failure(GameError.BoxAlreadyPlaced)
            } else {
                sections.placeBox(key, boxId, zone)
            }
        }

    internal fun finishShift(sessionId: String) = sectionCommand(sessionId) { key, _ ->
        sections.finishShift(key)
    }

    internal fun placeTilt(
        sessionId: String,
        boxId: String,
        zone: BoxCategory,
        operationId: String
    ) = sectionCommand(sessionId, operationId) { key, session ->
        val shift = session.activeActivity as? WorkShift
        if (shift?.selectedBox?.id != boxId) {
            DomainResult.Failure(GameError.BoxAlreadyPlaced)
        } else {
            sections.placeBox(key, boxId, zone)
        }
    }

    internal fun buy(sessionId: String, item: StoreProduct, quantity: Int, confirmationId: String) =
        sectionCommand(sessionId, confirmationId) { key, _ ->
            sections.buy(key, item.product.id, quantity, item.quote.unitPriceMinor)
        }

    internal fun dismissSectionEffect() {
        mutableEffect.value = null
    }

    private fun sectionCommand(
        sessionId: String,
        operationId: String = UUID.randomUUID().toString(),
        command: suspend (OperationKey, GameSession) -> DomainResult<OperationReceipt>
    ) {
        if (mutableBusy.value || activeSessionId != sessionId) return
        mutableBusy.value = true
        mutableEffect.value = null
        val running = operation
        operation = viewModelScope.launch {
            try {
                running?.join()
                val session = currentSession()?.takeIf { it.id == sessionId } ?: return@launch
                val key = OperationKey(sessionId, operationId)
                retryAction = {
                    publish(playerStatus())
                    loadSection(sessionId, session.clock.day)
                }
                when (val result = command(key, session)) {
                    is DomainResult.Success -> {
                        publish(playerStatus())
                        mutableEffect.value = result.value.effect
                    }
                    is DomainResult.Failure -> showError(result.error)
                }
            } finally {
                mutableBusy.value = false
            }
        }
    }

    internal fun start(request: ActivityRequest) = perform {
        val session = currentSession() ?: return@perform
        val key = OperationKey(session.id, UUID.randomUUID().toString())
        applyActivity(key, request)
    }

    private suspend fun applyActivity(key: OperationKey, request: ActivityRequest) {
        retryAction = { perform { applyActivity(key, request) } }
        when (val result = startActivity(key, request)) {
            is DomainResult.Success -> publish(playerStatus())
            is DomainResult.Failure -> showError(result.error)
        }
    }

    internal fun togglePause() = perform {
        val session = currentSession() ?: return@perform
        retryAction = ::togglePause
        publish(setPause(PauseReason.MANUAL, PauseReason.MANUAL !in session.clock.pauseReasons))
    }

    internal fun save() = perform {
        retryAction = ::save
        publish(saveProgress())
    }

    internal fun retry() {
        val state = state.value as? MainUiState.Error ?: return
        if (state.session != null &&
            (state.reason == GameError.StorageFailure || state.reason == GameError.RevisionConflict)
        ) {
            save()
        } else {
            retryAction?.invoke()
        }
    }

    internal fun setHomeVisible(visible: Boolean) {
        if (homeVisible == visible) return
        homeVisible = visible
        updateTicker()
        if (!visible && activeSessionId != null) {
            val running = operation
            viewModelScope.launch {
                running?.join()
                if (!homeVisible && activeSessionId != null) save()
            }
        }
    }

    private fun open(result: DomainResult<GameSession>, onOpened: (GameSession) -> Unit) {
        publish(result)
        if (result is DomainResult.Success) {
            activeSessionId = result.value.id
            updateTicker()
            onOpened(result.value)
        }
    }

    private fun updateTicker() {
        val running = homeVisible && activeSessionId != null
        clock.setRunning(running)
        if (!running) {
            ticker?.cancel()
            ticker = null
        } else if (ticker == null) {
            ticker = viewModelScope.launch {
                while (true) {
                    val current = currentSession()
                    val compactShift = (current?.activeActivity as? WorkShift)?.compact == true
                    delay(if (current?.vehicles?.race != null || compactShift) 100 else 1000)
                    val content = state.value as? MainUiState.Content ?: continue
                    if (content.session.status == SessionStatus.ACTIVE) {
                        perform {
                            retryAction = ::save
                            publish(updateTime())
                        }
                    }
                }
            }
        }
    }

    private fun currentSession(): GameSession? = (playerStatus() as? DomainResult.Success)?.value

    private fun publish(result: DomainResult<GameSession>) {
        mutableState.value = when (result) {
            is DomainResult.Success -> MainUiState.Content(result.value)
            is DomainResult.Failure -> {
                if (result.error == GameError.NoSession) {
                    MainUiState.Empty
                } else {
                    MainUiState.Error(result.error, currentSession())
                }
            }
        }
    }

    private fun showError(error: GameError) {
        mutableState.value = MainUiState.Error(error, currentSession())
    }

    private fun perform(block: suspend () -> Unit) {
        if (operation?.isActive == true) return
        operation = viewModelScope.launch { block() }
    }
}
