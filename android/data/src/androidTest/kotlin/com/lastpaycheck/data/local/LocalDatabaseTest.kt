package com.lastpaycheck.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.lastpaycheck.data.catalog.CachedManifest
import com.lastpaycheck.data.catalog.DemoVehicleCatalogRepository
import com.lastpaycheck.data.remote.dto.CatalogManifestDto
import com.lastpaycheck.data.remote.dto.SourceMetadataDto
import com.lastpaycheck.domain.engine.GameEngine
import com.lastpaycheck.domain.engine.GameSessionCoordinator
import com.lastpaycheck.domain.model.CatalogSource
import com.lastpaycheck.domain.model.Course
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.EconomicScenario
import com.lastpaycheck.domain.model.FinancialTransaction
import com.lastpaycheck.domain.model.GameCatalog
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.GameTransition
import com.lastpaycheck.domain.model.InitialConditions
import com.lastpaycheck.domain.model.Job
import com.lastpaycheck.domain.model.Needs
import com.lastpaycheck.domain.model.OperationKey
import com.lastpaycheck.domain.model.Product
import com.lastpaycheck.domain.model.RaceOutcome
import com.lastpaycheck.domain.model.TransactionCategory
import com.lastpaycheck.domain.model.VehicleUpgrade
import com.lastpaycheck.domain.repository.CatalogRepository
import com.lastpaycheck.domain.repository.CheckpointChoice
import com.lastpaycheck.domain.usecase.AbandonRaceUseCase
import com.lastpaycheck.domain.usecase.BuyProductUseCase
import com.lastpaycheck.domain.usecase.BuyVehicleUseCase
import com.lastpaycheck.domain.usecase.ContinueGameUseCase
import com.lastpaycheck.domain.usecase.GetVehicleCatalogUseCase
import com.lastpaycheck.domain.usecase.RenameCharacterUseCase
import com.lastpaycheck.domain.usecase.SaveProgressUseCase
import com.lastpaycheck.domain.usecase.StartNewGameUseCase
import com.lastpaycheck.domain.usecase.StartRaceUseCase
import com.lastpaycheck.domain.usecase.UpgradeVehicleUseCase
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class LocalDatabaseTest {
    @Test
    fun vehicleOwnershipUpgradesAndRacePersistAcrossDatabaseReopen() = runBlocking {
        withDatabase { db ->
            val repository = RoomGameSessionRepository(db)
            val coordinator = GameSessionCoordinator(GameEngine(), repository) { 0L }
            val original = StartNewGameUseCase(FixtureCatalog(), coordinator)(
                "vehicle-reopen",
                "scenario",
                "training-1"
            ).value()
            val payload = checkNotNull(db.sessions().snapshot(0)).payload
            org.junit.Assert.assertFalse(payload.contains("\"vehicles\""))
            repository.checkpoint(
                original.identity,
                original.copy(player = original.player.copy(moneyMinor = 100_000))
            ).value()
            ContinueGameUseCase(coordinator)().value()
            val catalog = GetVehicleCatalogUseCase(
                DemoVehicleCatalogRepository()
            )
            BuyVehicleUseCase(catalog, coordinator)(
                OperationKey(original.id, "buy-car"),
                "city-hatch"
            ).value()
            UpgradeVehicleUseCase(coordinator)(
                OperationKey(original.id, "engine"),
                "city-hatch",
                VehicleUpgrade.ENGINE
            ).value()
            StartRaceUseCase(catalog, coordinator)(
                OperationKey(original.id, "race"),
                "night-standard"
            ).value()
        }
        withDatabase { db ->
            val repository = RoomGameSessionRepository(db)
            val coordinator = GameSessionCoordinator(GameEngine(), repository) { 0L }
            val loaded = ContinueGameUseCase(coordinator)().value()
            assertEquals("city-hatch", loaded.vehicles.activeVehicleId)
            assertEquals(1, loaded.vehicles.activeVehicle!!.level(VehicleUpgrade.ENGINE))
            assertEquals("race", loaded.vehicles.race!!.id)
            AbandonRaceUseCase(coordinator)(
                OperationKey(loaded.id, "abandon"),
                "race"
            ).value()
            val saved = repository.load(CheckpointChoice.LATEST).value()
            assertEquals(1L, saved.vehicles.completed)
            assertEquals(
                RaceOutcome.ABANDONED,
                saved.vehicles.history.single().outcome
            )
            assertEquals(93_400L, saved.player.moneyMinor)
            assertEquals(2, repository.readTransactions(saved.identity).value().size)
        }
    }

    @Test
    fun oldSnapshotWithoutNameLoadsAndRenamedCharacterSurvivesDatabaseReopen() = runBlocking {
        withDatabase { db ->
            val repository = RoomGameSessionRepository(db)
            val coordinator = GameSessionCoordinator(GameEngine(), repository) { 0L }
            val session = StartNewGameUseCase(FixtureCatalog(), coordinator)(
                "legacy-player",
                "scenario",
                "training-1"
            ).value()
            val row = checkNotNull(db.sessions().snapshot(0))
            org.junit.Assert.assertFalse(row.payload.contains("\"name\""))
            assertEquals("", repository.load(CheckpointChoice.LATEST).value().player.name)
            RenameCharacterUseCase(
                coordinator
            )(session.id, "Максим").value()
        }
        withDatabase { db ->
            val loaded = RoomGameSessionRepository(db).load(CheckpointChoice.LATEST).value()
            assertEquals("Максим", loaded.player.name)
            assertEquals("legacy-player", loaded.id)
            assertEquals(1000L, loaded.player.moneyMinor)
        }
    }

    private val name = "persistence-${UUID.randomUUID()}.db"
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @get:Rule
    val migration = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        LocalDatabase::class.java
    )

    @After
    fun deleteTestDatabase() {
        context.deleteDatabase(name)
    }

    @Test
    fun domainUseCasesPersistAndResumeTheEntireGameAndJournal() = runBlocking {
        val catalog = FixtureCatalog()
        var time = 0L
        val purchase = OperationKey("player", "purchase-1")

        val saved = withDatabase { db ->
            val repo = RoomGameSessionRepository(db)
            val coordinator = GameSessionCoordinator(GameEngine(), repo) { time }
            StartNewGameUseCase(catalog, coordinator)("player", "scenario", "training-1").value()
            time = 1000
            BuyProductUseCase(catalog, coordinator)(purchase, "food", 2, 50).value()
            val saved = SaveProgressUseCase(coordinator)().value()
            assertEquals(900L, saved.player.moneyMinor)
            assertEquals(2, saved.inventory.quantity("food"))
            assertNotEquals(0L, saved.clock.gameMillis)
            assertNotEquals(Needs.of(100, 100, 100, 100), saved.player.needs)
            saved
        }

        withDatabase { db ->
            val repo = RoomGameSessionRepository(db)
            val coordinator = GameSessionCoordinator(GameEngine(), repo) { time }
            val restored = ContinueGameUseCase(coordinator)().value()
            assertEquals(saved, restored)
            assertEquals(1, repo.readTransactions(restored.identity).value().size)
            val receipt = repo.findReceipt(purchase).value()
            assertEquals(
                receipt,
                BuyProductUseCase(catalog, coordinator)(purchase, "food", 2, 50).value()
            )
            assertEquals(1, repo.readTransactions(restored.identity).value().size)
        }
    }

    @Test
    fun restoresPreviousSnapshotWithMatchingTransactionsAndReceipts() = runBlocking {
        withDatabase { db ->
            val repo = RoomGameSessionRepository(db)
            val catalog = FixtureCatalog()
            val coordinator = GameSessionCoordinator(GameEngine(), repo) { 0 }
            val initial = StartNewGameUseCase(
                catalog,
                coordinator
            )("player", "scenario", "training-1").value()
            val key = OperationKey(initial.id, "purchase")
            BuyProductUseCase(catalog, coordinator)(key, "food", 1, 50).value()

            val latest = checkNotNull(db.sessions().snapshot(0))
            db.sessions().put(latest.copy(payload = "{}"))
            assertEquals(
                DomainResult.Failure(GameError.InvalidSnapshot(true)),
                repo.load(CheckpointChoice.LATEST)
            )
            val restored = ContinueGameUseCase(coordinator)(CheckpointChoice.PREVIOUS).value()
            assertEquals(initial.copy(revision = latest.revision + 1), restored)
            assertEquals(
                emptyList<FinancialTransaction>(),
                repo.readTransactions(restored.identity).value()
            )
            assertNull(repo.findReceipt(key).value())
        }
    }

    @Test
    fun failedJournalInsertDoesNotPartiallyUpdateSession() = runBlocking {
        withDatabase { db ->
            val repo = RoomGameSessionRepository(db)
            val coordinator = GameSessionCoordinator(GameEngine(), repo) { 0 }
            val initial = StartNewGameUseCase(FixtureCatalog(), coordinator)(
                "player",
                "scenario",
                "training-1"
            ).value()
            val key = OperationKey(initial.id, "duplicate-transaction")
            val next = initial.copy(player = initial.player.copy(moneyMinor = 900))
            val record =
                FinancialTransaction(
                    "same",
                    key.id,
                    initial.id,
                    0,
                    TransactionCategory.PURCHASE,
                    -100
                )
            val result = repo.commit(
                initial.identity,
                key,
                GameTransition(next, listOf(record, record))
            )
            assertEquals(DomainResult.Failure(GameError.StorageFailure), result)
            assertEquals(initial, repo.load(CheckpointChoice.LATEST).value())
            assertEquals(
                emptyList<FinancialTransaction>(),
                repo.readTransactions(initial.identity).value()
            )
            assertNull(repo.findReceipt(key).value())
        }
    }

    @Test
    fun roomCacheSurvivesDatabaseReopen() = runBlocking {
        val cached = CachedManifest(manifest(), Instant.ofEpochMilli(2468))
        withDatabase { db -> RoomCatalogCache(db.catalog()).write(cached) }
        withDatabase { db ->
            assertEquals(cached, RoomCatalogCache(db.catalog()).read())
        }
    }

    @Test
    fun migrationValidatesSchemaAndPreservesLegacyManifestAndSnapshot() = runBlocking {
        val dto = manifest()
        migration.createDatabase(name, 1).use { db ->
            db.execSQL(
                "INSERT INTO catalog_manifest (slot, payload) VALUES (0, ?)",
                arrayOf(Json.encodeToString(dto))
            )
            db.execSQL(
                "INSERT INTO session_snapshot VALUES (0, 'player', 4, 1, 'payload', 'checksum')"
            )
        }
        migration.runMigrationsAndValidate(name, 2, true, LocalDatabase.migration1To2).close()
        withDatabase { db ->
            assertEquals(CachedManifest(dto, null), RoomCatalogCache(db.catalog()).read())
            assertEquals(
                SnapshotRow(0, "player", 4, 1, "payload", "checksum"),
                db.sessions().snapshot(0)
            )
        }
    }

    private suspend fun <T> withDatabase(block: suspend (LocalDatabase) -> T): T {
        val database = LocalDatabase.open(context, name)
        try {
            return block(database)
        } finally {
            database.close()
        }
    }

    private fun manifest(): CatalogManifestDto {
        val resources = listOf(
            "occupations",
            "skills",
            "jobs",
            "inflation",
            "car-brands",
            "car-models",
            "tracks",
            "race-configurations"
        )
        val hash = "a".repeat(64)
        val sources = resources.map {
            SourceMetadataDto(
                it,
                "DEMO",
                Instant.EPOCH.toString(),
                null,
                hash
            )
        }
        return CatalogManifestDto(
            "demo-v1",
            "1",
            Instant.EPOCH.toString(),
            hash,
            resources,
            sources
        )
    }

    private fun <T> DomainResult<T>.value(): T = (this as DomainResult.Success).value

    private class FixtureCatalog : CatalogRepository {
        private val version = "training-1"
        private val data = GameCatalog(
            version = version,
            source = CatalogSource.TRAINING,
            initial = InitialConditions(1000, Needs.of(100, 100, 100, 100), "job-1", 50),
            jobs = (1..3).map { Job("job-$it", "Warehouse", 0, 1, 200, 100, 10, 1) },
            products = listOf(Product("food", 50, 20, 10)),
            courses = listOf(Course("course", 100, 1, 10)),
            scenarios = listOf(
                EconomicScenario(
                    "scenario",
                    version,
                    "RUS",
                    2026,
                    BigDecimal.ZERO,
                    CatalogSource.TRAINING
                )
            )
        )

        override suspend fun getVersion() = version

        override suspend fun get(version: String?) = DomainResult.Success(data)

        override suspend fun refresh() = DomainResult.Success(data)
    }
}
