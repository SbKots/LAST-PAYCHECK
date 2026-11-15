package com.lastpaycheck.presentation.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lastpaycheck.domain.model.ActivityRequest
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.GameSession
import com.lastpaycheck.domain.model.NewGamePreview
import com.lastpaycheck.domain.model.OperationKey
import com.lastpaycheck.domain.model.PauseReason
import com.lastpaycheck.domain.model.SessionStatus
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
    private val saveProgress: SaveProgressUseCase
) : ViewModel() {
    private val mutableState = MutableStateFlow<MainUiState>(MainUiState.Loading)
    internal val state = mutableState.asStateFlow()
    private val mutablePreview = MutableStateFlow<DomainResult<NewGamePreview>?>(null)
    internal val preview = mutablePreview.asStateFlow()
    private var operation: Job? = null
    private var ticker: Job? = null
    private var homeVisible = false
    private var activeSessionId: String? = null
    private var retryAction: (() -> Unit)? = null

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
                    delay(1000)
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
