package com.lastpaycheck.domain.engine

import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.GameSession
import com.lastpaycheck.domain.model.GameTransition
import com.lastpaycheck.domain.model.OperationKey
import com.lastpaycheck.domain.model.OperationReceipt
import com.lastpaycheck.domain.model.PauseReason
import com.lastpaycheck.domain.model.SessionStatus
import com.lastpaycheck.domain.model.failure
import com.lastpaycheck.domain.model.flatMap
import com.lastpaycheck.domain.model.map
import com.lastpaycheck.domain.repository.CheckpointChoice
import com.lastpaycheck.domain.repository.GameSessionRepository
import com.lastpaycheck.domain.repository.MonotonicTimeSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val CHECKPOINT_INTERVAL_MILLIS = 10_000L
private const val CLOCK_OPERATION_PREFIX = "@clock:"

/** One instance per game. Durable effects are published only after a repository commit. */
class GameSessionCoordinator(
    private val engine: GameEngine,
    private val repository: GameSessionRepository,
    private val timeSource: MonotonicTimeSource
) {
    private val lock = Mutex()
    private val current = MutableStateFlow<GameSession?>(null)
    val session = current.asStateFlow()

    private var confirmed: GameSession? = null
    private var timeMark = 0L
    private var activeSinceCheckpoint = 0L

    internal suspend fun start(
        newSession: GameSession,
        replaceSessionId: String?
    ): DomainResult<GameSession> = lock.withLock {
        val previous = when (val result = repository.load(CheckpointChoice.LATEST)) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> {
                if (result.error != GameError.NoSession) return@withLock result
                null
            }
        }
        if (previous != null && replaceSessionId == null) {
            return@withLock failure(GameError.ConfirmationRequired(previous.id))
        }
        if (previous?.id != replaceSessionId || previous?.id == newSession.id) {
            return@withLock failure(GameError.RevisionConflict)
        }
        repository.replace(previous?.identity, newSession).flatMap { loaded ->
            if (loaded.id == newSession.id) open(loaded) else invalidSnapshot()
        }
    }

    internal suspend fun resume(choice: CheckpointChoice): DomainResult<GameSession> =
        lock.withLock {
            val loaded = repository.load(choice)
            loaded.flatMap { snapshot ->
                if (!snapshot.isValid()) return@flatMap invalidSnapshot()
                if (choice == CheckpointChoice.PREVIOUS) {
                    repository.restorePrevious(snapshot.identity).flatMap(::open)
                } else {
                    open(snapshot)
                }
            }
        }

    internal suspend fun submit(
        key: OperationKey,
        prepare: suspend (GameSession) -> DomainResult<GameCommand>
    ): DomainResult<OperationReceipt> = lock.withLock {
        val reserved = key.id.startsWith(CLOCK_OPERATION_PREFIX)
        if (key.sessionId.isBlank() || key.id.isBlank() || reserved) {
            return@withLock failure(GameError.InvalidInput("operationKey"))
        }
        val session = current.value ?: return@withLock failure(GameError.NoSession)
        if (key.sessionId != session.id) return@withLock failure(GameError.RevisionConflict)
        when (val replay = repository.findReceipt(key)) {
            is DomainResult.Success -> {
                val receipt = replay.value
                if (receipt != null) return@withLock DomainResult.Success(receipt)
            }
            is DomainResult.Failure -> return@withLock handleFailure(replay)
        }
        advanceLocked().flatMap { ready ->
            if (ready.status == SessionStatus.FINISHED) {
                return@flatMap failure(GameError.GameFinished)
            }
            if (ready.clock.isPaused) return@flatMap failure(GameError.Paused)
            prepare(ready).flatMap { command ->
                engine.handle(ready, key, command).flatMap { transition ->
                    commit(key, transition)
                }
            }
        }
    }

    internal suspend fun advance(): DomainResult<GameSession> = lock.withLock {
        advanceLocked()
    }

    internal suspend fun setPause(
        reason: PauseReason,
        enabled: Boolean
    ): DomainResult<GameSession> = lock.withLock {
        if (reason == PauseReason.STORAGE_FAILURE) {
            return@withLock failure(GameError.InvalidInput("reason"))
        }
        val advanced = advanceLocked()
        if (advanced is DomainResult.Failure && enabled) {
            val snapshot = current.value
            if (snapshot?.status == SessionStatus.ACTIVE) {
                val clock = snapshot.clock
                current.value = snapshot.copy(
                    clock = clock.copy(pauseReasons = clock.pauseReasons + reason)
                )
            }
        }
        advanced.flatMap { snapshot ->
            if (snapshot.status == SessionStatus.FINISHED) {
                return@flatMap failure(GameError.GameFinished)
            }
            val oldReasons = snapshot.clock.pauseReasons
            val reasons = if (enabled) oldReasons + reason else oldReasons - reason
            if (reasons == oldReasons) return@flatMap DomainResult.Success(snapshot)
            val paused = snapshot.copy(clock = snapshot.clock.copy(pauseReasons = reasons))
            // Lifecycle pauses remain effective even when the checkpoint cannot be written.
            checkpoint(paused, reasons)
        }
    }

    internal suspend fun save(): DomainResult<GameSession> = lock.withLock {
        val snapshot = current.value ?: return@withLock failure(GameError.NoSession)
        val ready = if (snapshot.status == SessionStatus.FINISHED) {
            DomainResult.Success(snapshot)
        } else {
            advanceLocked()
        }
        ready.flatMap {
            val reasons = it.clock.pauseReasons - PauseReason.STORAGE_FAILURE
            checkpoint(it.copy(clock = it.clock.copy(pauseReasons = reasons)))
        }
    }

    private suspend fun advanceLocked(): DomainResult<GameSession> {
        val snapshot = current.value ?: return failure(GameError.NoSession)
        if (snapshot.status == SessionStatus.FINISHED) return failure(GameError.GameFinished)
        val now = timeSource.nowMillis()
        if (now < timeMark) return failure(GameError.InvalidInput("activeMillis"))
        if (snapshot.clock.isPaused) {
            timeMark = now
            return DomainResult.Success(snapshot)
        }
        val elapsed = try {
            Math.subtractExact(now, timeMark)
        } catch (_: ArithmeticException) {
            return failure(GameError.LimitExceeded)
        }
        if (elapsed == 0L) return DomainResult.Success(snapshot)
        val key = OperationKey(snapshot.id, "$CLOCK_OPERATION_PREFIX${snapshot.revision}")
        return engine.advance(snapshot, key, elapsed).flatMap { transition ->
            timeMark = now
            val next = transition.session
            val durableEffect = transition.transactions.isNotEmpty() ||
                snapshot.activeActivity?.id != next.activeActivity?.id ||
                snapshot.lastRentDay != next.lastRentDay || snapshot.status != next.status
            when {
                durableEffect -> commit(key, transition).map { checkNotNull(current.value) }
                elapsed >= CHECKPOINT_INTERVAL_MILLIS - activeSinceCheckpoint -> checkpoint(next)
                else -> {
                    activeSinceCheckpoint += elapsed
                    current.value = next
                    DomainResult.Success(next)
                }
            }
        }
    }

    private suspend fun commit(
        key: OperationKey,
        transition: GameTransition
    ): DomainResult<OperationReceipt> {
        val expected = checkNotNull(confirmed).identity
        return when (val result = repository.commit(expected, key, transition)) {
            is DomainResult.Success -> {
                val committed = result.value
                if (!validWrite(expected.sessionId, expected.revision, committed.session) ||
                    committed.receipt.key != key ||
                    committed.receipt.revision > committed.session.revision
                ) {
                    handleFailure(failure(GameError.StorageFailure))
                } else {
                    publish(committed.session)
                    DomainResult.Success(committed.receipt)
                }
            }
            is DomainResult.Failure -> handleFailure(result)
        }
    }

    private suspend fun checkpoint(
        snapshot: GameSession,
        pauseOnFailure: Set<PauseReason> = snapshot.clock.pauseReasons
    ): DomainResult<GameSession> {
        val expected = checkNotNull(confirmed).identity
        val wasPaused = current.value?.clock?.isPaused == true
        return when (val result = repository.checkpoint(expected, snapshot)) {
            is DomainResult.Success -> {
                if (!validWrite(expected.sessionId, expected.revision, result.value)) {
                    return handleFailure(failure(GameError.StorageFailure), pauseOnFailure)
                }
                publish(result.value)
                if (wasPaused || result.value.clock.isPaused) timeMark = timeSource.nowMillis()
                result
            }
            is DomainResult.Failure -> handleFailure(result, pauseOnFailure)
        }
    }

    private fun validWrite(sessionId: String, revision: Long, snapshot: GameSession) =
        snapshot.id == sessionId && snapshot.revision > revision && snapshot.isValid()

    private suspend fun handleFailure(
        result: DomainResult.Failure,
        pauseReasons: Set<PauseReason> = current.value?.clock?.pauseReasons.orEmpty()
    ): DomainResult.Failure {
        if (result.error == GameError.RevisionConflict) {
            val loaded = repository.load(CheckpointChoice.LATEST)
            if (loaded is DomainResult.Success && open(loaded.value) is DomainResult.Success) {
                val snapshot = checkNotNull(current.value)
                val lifecycle = pauseReasons.intersect(
                    setOf(PauseReason.BACKGROUND, PauseReason.FOCUS_LOST)
                )
                val reasons = snapshot.clock.pauseReasons + lifecycle
                current.value = snapshot.copy(
                    clock = snapshot.clock.copy(pauseReasons = reasons)
                )
                return result
            }
        }
        val storageFailure = result.error == GameError.StorageFailure
        if (storageFailure || result.error == GameError.RevisionConflict) {
            val snapshot = confirmed
            if (snapshot != null) {
                current.value = snapshot.copy(
                    clock = snapshot.clock.copy(
                        pauseReasons = pauseReasons + PauseReason.STORAGE_FAILURE
                    )
                )
            }
            timeMark = timeSource.nowMillis()
            activeSinceCheckpoint = 0
        }
        return result
    }

    private fun open(snapshot: GameSession): DomainResult<GameSession> {
        if (!snapshot.isValid()) return invalidSnapshot()
        val opened = snapshot.copy(
            clock = snapshot.clock.copy(
                pauseReasons = snapshot.clock.pauseReasons.intersect(setOf(PauseReason.MANUAL))
            )
        )
        confirmed = snapshot
        activeSinceCheckpoint = 0
        current.value = opened
        timeMark = timeSource.nowMillis()
        return DomainResult.Success(opened)
    }

    private fun publish(snapshot: GameSession) {
        confirmed = snapshot
        current.value = snapshot
        activeSinceCheckpoint = 0
    }

    private fun invalidSnapshot() = failure(GameError.InvalidSnapshot(previousAvailable = false))
}
