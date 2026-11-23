package com.lastpaycheck.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.lastpaycheck.data.catalog.TrainingGameCatalogRepository
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.GameSession
import com.lastpaycheck.domain.model.PlayerState
import com.lastpaycheck.domain.repository.CheckpointChoice
import com.lastpaycheck.domain.usecase.GetFinancialOverviewUseCase
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Test

class FinanceSnapshotTest {
    @Test fun legacyJsonAndCorruptionRecoveryNeverSilentlyResetTheGame() = runBlocking {
        val db = database()
        try {
            val catalog = (TrainingGameCatalogRepository().get() as DomainResult.Success).value
            val session = GameSession(
                "legacy-19",
                catalog.version,
                PlayerState(12602, catalog.initial.needs, catalog.jobs.first().id),
                catalog.scenarios.first(),
                catalog.initial.dailyRentMinor
            )
            val payload = Json.encodeToString(session.toStored())
            assertTrue(!payload.contains("\"vehicles\""))
            val original = row(session.id, session.revision, payload)
            db.sessions().put(original)
            val repository = RoomGameSessionRepository(db)
            val overview = GetFinancialOverviewUseCase(repository)
            assertEquals(session, (overview(session.id) as DomainResult.Success).value.session)
            db.sessions().put(original.copy(payload = payload + " "))
            assertEquals(
                DomainResult.Failure(GameError.InvalidSnapshot(false)),
                overview(session.id)
            )
            assertEquals(payload + " ", db.sessions().snapshot(0)!!.payload)
            db.sessions().put(original)
            assertEquals(session, (overview(session.id) as DomainResult.Success).value.session)
        } finally {
            db.close()
        }
    }

    @Test fun suppliedPhoneSnapshotAndCarJournalRemainUnchangedAfterReading() = runBlocking {
        val path = InstrumentationRegistry.getArguments().getString("snapshotFixture")
        assumeNotNull(path)
        val fixture = Json.parseToJsonElement(File(checkNotNull(path)).readText()).jsonObject
        val source = fixture.getValue("snapshot").jsonObject
        val payload = source.getValue("payload").jsonPrimitive.content
        val original = row(
            source.getValue("sessionId").jsonPrimitive.content,
            source.getValue("revision").jsonPrimitive.long,
            payload
        )
        assertEquals(source.getValue("sha256").jsonPrimitive.content, original.sha256)
        val records = fixture.getValue("transactions").jsonArray.map { element ->
            val r = element.jsonObject
            TransactionRow(
                r.getValue("sessionId").jsonPrimitive.content,
                r.getValue("id").jsonPrimitive.content,
                r.getValue("revision").jsonPrimitive.long,
                r.getValue("position").jsonPrimitive.long.toInt(),
                r.getValue("operationId").jsonPrimitive.content,
                r.getValue("gameMillis").jsonPrimitive.long,
                r.getValue("category").jsonPrimitive.content,
                r.getValue("amountMinor").jsonPrimitive.long
            )
        }
        val db = database()
        try {
            db.sessions().put(original)
            db.sessions().insertTransactions(records)
            val repo = RoomGameSessionRepository(db)
            val before = (repo.load(CheckpointChoice.LATEST) as DomainResult.Success).value
            val overview = (
                GetFinancialOverviewUseCase(
                    repo
                )(before.id) as DomainResult.Success
                ).value
            assertEquals(before, overview.session)
            assertEquals(
                records.filter { it.amountMinor > 0 }.sumOf { it.amountMinor },
                overview.statistics.incomeMinor
            )
            assertEquals(
                -records.filter { it.amountMinor < 0 }.sumOf { it.amountMinor },
                overview.statistics.expenseMinor
            )
            assertEquals(original, db.sessions().snapshot(0))
            val legacyPayload = JsonObject(
                Json.parseToJsonElement(payload).jsonObject - "vehicles"
            ).toString()
            db.sessions().put(row(before.id, before.revision, legacyPayload))
            val legacy = (repo.load(CheckpointChoice.LATEST) as DomainResult.Success).value
            assertEquals(before.player, legacy.player)
            assertTrue(legacy.vehicles.garage.isEmpty())
        } finally {
            db.close()
        }
    }

    private fun database() = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        LocalDatabase::class.java
    ).build()

    private fun row(id: String, revision: Long, payload: String): SnapshotRow {
        val hash = MessageDigest.getInstance("SHA-256").digest(payload.encodeToByteArray())
            .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
        return SnapshotRow(0, id, revision, 1, payload, hash)
    }
}
