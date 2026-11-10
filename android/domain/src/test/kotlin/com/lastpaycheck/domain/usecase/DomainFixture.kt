package com.lastpaycheck.domain.usecase

import com.lastpaycheck.domain.engine.GameEngine
import com.lastpaycheck.domain.engine.GameSessionCoordinator
import com.lastpaycheck.domain.model.BoxCategory
import com.lastpaycheck.domain.model.CatalogSource
import com.lastpaycheck.domain.model.CommittedOperation
import com.lastpaycheck.domain.model.Course
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.EconomicScenario
import com.lastpaycheck.domain.model.FinancialTransaction
import com.lastpaycheck.domain.model.GAME_HOUR_MILLIS
import com.lastpaycheck.domain.model.GameCatalog
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.GameSession
import com.lastpaycheck.domain.model.GameSettings
import com.lastpaycheck.domain.model.GameTransition
import com.lastpaycheck.domain.model.InitialConditions
import com.lastpaycheck.domain.model.Job
import com.lastpaycheck.domain.model.Needs
import com.lastpaycheck.domain.model.OperationKey
import com.lastpaycheck.domain.model.OperationReceipt
import com.lastpaycheck.domain.model.PlayerState
import com.lastpaycheck.domain.model.Product
import com.lastpaycheck.domain.model.SessionRevision
import com.lastpaycheck.domain.model.WarehouseBox
import com.lastpaycheck.domain.model.WorkShift
import com.lastpaycheck.domain.model.failure
import com.lastpaycheck.domain.repository.CatalogRepository
import com.lastpaycheck.domain.repository.CheckpointChoice
import com.lastpaycheck.domain.repository.GameSessionRepository
import com.lastpaycheck.domain.repository.MonotonicTimeSource
import com.lastpaycheck.domain.repository.RandomDraw
import com.lastpaycheck.domain.repository.SettingsRepository
import java.math.BigDecimal
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals

internal val helperJob = Job("helper", "Helper", 0, 10, 400, 100, 40, 2)
internal val pickerJob = Job("picker", "Picker", 1, 10, 500, 120, 45, 3)
internal val seniorJob = Job("senior", "Senior", 2, 10, 600, 150, 50, 4)
internal val defaultNeeds = Needs.of(100, 100, 100, 100)

internal fun sampleCatalog(version: String = "v1", inflation: String = "0", money: Long = 150) =
    GameCatalog(
        version = version,
        source = CatalogSource.TRAINING,
        initial = InitialConditions(money, defaultNeeds, helperJob.id, 100),
        jobs = listOf(helperJob, pickerJob, seniorJob),
        products = listOf(Product("food", 50, 30, 10)),
        courses = listOf(Course("logistics", 300, 4, 100)),
        scenarios = listOf(
            EconomicScenario(
                "scenario",
                version,
                "RUS",
                2020,
                BigDecimal(inflation),
                CatalogSource.TRAINING
            )
        )
    )

internal fun sampleSession(
    catalog: GameCatalog = sampleCatalog(),
    player: PlayerState = PlayerState(catalog.initial.moneyMinor, defaultNeeds, helperJob.id)
) = GameSession(
    "game",
    catalog.version,
    player,
    catalog.scenarios.first(),
    catalog.initial.dailyRentMinor
)

internal fun sampleShift(correct: Int = 0, errors: Int = 0, workedHours: Int = 0) = WorkShift(
    id = "shift",
    job = helperJob,
    selectedBox = WarehouseBox("next", BoxCategory.FOOD),
    remainingGameMillis = (10 - workedHours) * GAME_HOUR_MILLIS,
    correct = correct,
    errors = errors,
    processedBoxIds = (0 until correct + errors).map { "box:$it" }.toSet()
)

internal class FakeClock(var now: Long = 0) : MonotonicTimeSource {
    override fun nowMillis() = now
}

internal class FakeCatalog(initial: GameCatalog = sampleCatalog()) : CatalogRepository {
    private val versions = mutableMapOf(initial.version to initial)
    var latest = initial.version
        private set
    var candidate = initial
    var readError: GameError? = null
    var refreshError: GameError? = null
    val requestedVersions = mutableListOf<String?>()

    override suspend fun getVersion() = latest

    override suspend fun get(version: String?): DomainResult<GameCatalog> {
        requestedVersions += version
        readError?.let { return failure(it) }
        return versions[version ?: latest]?.let { DomainResult.Success(it) }
            ?: failure(GameError.CatalogUnavailable)
    }

    override suspend fun refresh(): DomainResult<GameCatalog> {
        refreshError?.let { return failure(it) }
        if (!candidate.isValid()) return failure(GameError.InvalidCatalog)
        if (versions[candidate.version]?.let { it != candidate } == true) {
            return failure(GameError.InvalidCatalog)
        }
        versions[candidate.version] = candidate
        latest = candidate.version
        return DomainResult.Success(candidate)
    }
}

internal class FakeSessions(initial: GameSession? = null) : GameSessionRepository {
    private data class Stored(
        val session: GameSession,
        val transactions: List<FinancialTransaction> = emptyList(),
        val receipts: Map<OperationKey, OperationReceipt> = emptyMap()
    )

    private var latest = initial?.let { Stored(it) }
    private var previous: Stored? = null
    var loadError: GameError? = null
    var writeError: GameError? = null
    var journalError: GameError? = null
    var receiptError: GameError? = null
    var corruptLatest = false
    var writes = 0
        private set
    val persisted get() = latest?.session
    val transactions get() = latest?.transactions.orEmpty()

    override suspend fun load(choice: CheckpointChoice): DomainResult<GameSession> {
        loadError?.let { return failure(it) }
        if (choice == CheckpointChoice.LATEST && corruptLatest) {
            return failure(GameError.InvalidSnapshot(previous != null))
        }
        val selected = if (choice == CheckpointChoice.LATEST) latest else previous
        return selected?.session?.let { DomainResult.Success(it) }
            ?: failure(GameError.NoSession)
    }

    override suspend fun restorePrevious(checkpoint: SessionRevision): DomainResult<GameSession> {
        writeError?.let { return failure(it) }
        val selected = previous ?: return failure(GameError.NoSession)
        if (selected.session.identity != checkpoint) return failure(GameError.RevisionConflict)
        val revision = maxOf(latest?.session?.revision ?: 0, selected.session.revision) + 1
        val restored = selected.copy(session = selected.session.copy(revision = revision))
        latest = restored
        previous = null
        corruptLatest = false
        writes++
        return DomainResult.Success(restored.session)
    }

    override suspend fun replace(
        expected: SessionRevision?,
        session: GameSession
    ): DomainResult<GameSession> {
        writeError?.let { return failure(it) }
        if (latest?.session?.identity != expected) return failure(GameError.RevisionConflict)
        previous = null
        latest = Stored(session)
        corruptLatest = false
        writes++
        return DomainResult.Success(session)
    }

    override suspend fun findReceipt(key: OperationKey): DomainResult<OperationReceipt?> {
        receiptError?.let { return failure(it) }
        return DomainResult.Success(latest?.receipts?.get(key))
    }

    override suspend fun commit(
        expected: SessionRevision,
        key: OperationKey,
        transition: GameTransition
    ): DomainResult<CommittedOperation> {
        val old = latest ?: return failure(GameError.NoSession)
        old.receipts[key]?.let { return DomainResult.Success(CommittedOperation(old.session, it)) }
        writeError?.let { return failure(it) }
        if (old.session.identity != expected) return failure(GameError.RevisionConflict)
        val session = transition.session.copy(revision = old.session.revision + 1)
        val receipt = OperationReceipt(key, session.revision, transition.effect)
        store(
            Stored(
                session,
                old.transactions + transition.transactions,
                old.receipts + (key to receipt)
            )
        )
        return DomainResult.Success(CommittedOperation(session, receipt))
    }

    override suspend fun checkpoint(
        expected: SessionRevision,
        session: GameSession
    ): DomainResult<GameSession> {
        writeError?.let { return failure(it) }
        val old = latest ?: return failure(GameError.NoSession)
        if (old.session.identity != expected) return failure(GameError.RevisionConflict)
        val next = session.copy(revision = old.session.revision + 1)
        store(old.copy(session = next))
        return DomainResult.Success(next)
    }

    override suspend fun readTransactions(
        revision: SessionRevision
    ): DomainResult<List<FinancialTransaction>> {
        journalError?.let { return failure(it) }
        if (latest?.session?.identity != revision) return failure(GameError.RevisionConflict)
        return DomainResult.Success(transactions)
    }

    fun seedTransactions(records: List<FinancialTransaction>) {
        val stored = checkNotNull(latest)
        latest = stored.copy(transactions = records)
    }

    private fun store(snapshot: Stored) {
        previous = latest
        latest = snapshot
        writes++
    }
}

internal class FakeSettings(initial: GameSettings) : SettingsRepository {
    private val state = MutableStateFlow<DomainResult<GameSettings>>(DomainResult.Success(initial))
    var error: GameError? = null
    var writes = 0
        private set
    override fun observe() = state

    override suspend fun update(settings: GameSettings): DomainResult<GameSettings> {
        error?.let { return failure(it) }
        val result = DomainResult.Success(settings)
        state.value = result
        writes++
        return result
    }

    fun failRead() {
        state.value = failure(GameError.StorageFailure)
    }
}

internal class Fixture(
    initial: GameSession? = sampleSession(),
    val catalog: FakeCatalog = FakeCatalog(),
    engine: GameEngine = GameEngine(random = { state, _ -> RandomDraw(0, state + 1) })
) {
    val store = FakeSessions(initial)
    val clock = FakeClock()
    val coordinator = GameSessionCoordinator(engine, store, clock)
    val session get() = checkNotNull(coordinator.session.value)
    val buy = BuyProductUseCase(catalog, coordinator)
    val startShift = StartShiftUseCase(catalog, coordinator)
    val activity = StartActivityUseCase(catalog, coordinator)
    val advance = UpdateGameTimeUseCase(coordinator)

    fun key(id: String) = OperationKey(session.id, id)

    suspend fun open(): Fixture {
        ContinueGameUseCase(coordinator)().ok()
        return this
    }

    suspend fun tick(millis: Long): DomainResult<GameSession> {
        clock.now += millis
        return advance()
    }
}

internal fun <T> DomainResult<T>.ok(): T {
    check(this is DomainResult.Success) { "Expected success, got $this" }
    return value
}

internal fun DomainResult<*>.failsWith(error: GameError) =
    assertEquals(DomainResult.Failure(error), this)
