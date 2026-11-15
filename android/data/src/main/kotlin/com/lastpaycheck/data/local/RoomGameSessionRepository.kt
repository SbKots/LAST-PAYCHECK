package com.lastpaycheck.data.local

import android.database.sqlite.SQLiteException
import androidx.room.withTransaction
import com.lastpaycheck.domain.model.CommittedOperation
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.FinancialTransaction
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.GameSession
import com.lastpaycheck.domain.model.GameTransition
import com.lastpaycheck.domain.model.OperationKey
import com.lastpaycheck.domain.model.OperationReceipt
import com.lastpaycheck.domain.model.SessionRevision
import com.lastpaycheck.domain.model.TransactionCategory
import com.lastpaycheck.domain.repository.CheckpointChoice
import com.lastpaycheck.domain.repository.GameSessionRepository
import java.security.MessageDigest
import kotlinx.serialization.json.Json

private const val LATEST = 0
private const val PREVIOUS = 1
private const val FORMAT = 1

internal class RoomGameSessionRepository(private val db: LocalDatabase) : GameSessionRepository {
    private val dao = db.sessions()

    override suspend fun load(choice: CheckpointChoice): DomainResult<GameSession> = transaction {
        val slot = if (choice == CheckpointChoice.LATEST) LATEST else PREVIOUS
        val row = dao.snapshot(slot) ?: return@transaction failure(GameError.NoSession)
        val session = row.decode()
        if (session == null) {
            val hasPrevious = slot == LATEST && dao.snapshot(PREVIOUS)?.decode() != null
            failure(GameError.InvalidSnapshot(hasPrevious))
        } else {
            DomainResult.Success(session)
        }
    }

    override suspend fun restorePrevious(checkpoint: SessionRevision) = transaction {
        val row = dao.snapshot(PREVIOUS) ?: return@transaction failure(GameError.NoSession)
        if (row.identity != checkpoint) return@transaction failure(GameError.RevisionConflict)
        val old = row.decode()
            ?: return@transaction failure(GameError.InvalidSnapshot(previousAvailable = false))
        val latest = dao.snapshot(LATEST)
        if (latest?.sessionId != row.sessionId) {
            return@transaction failure(
                GameError.RevisionConflict
            )
        }
        val restored = old.copy(revision = Math.addExact(maxOf(latest.revision, old.revision), 1L))
        dao.trimReceipts(row.sessionId, row.revision)
        dao.trimTransactions(row.sessionId, row.revision)
        dao.put(restored.encode(LATEST))
        dao.deleteSnapshot(PREVIOUS)
        DomainResult.Success(restored)
    }

    override suspend fun replace(expected: SessionRevision?, session: GameSession) = transaction {
        val latest = dao.snapshot(LATEST)
        if (latest?.identity != expected) return@transaction failure(GameError.RevisionConflict)
        if (!session.isValid()) return@transaction failure(GameError.InvalidSnapshot(false))
        if (latest != null) {
            dao.trimReceipts(latest.sessionId, -1)
            dao.trimTransactions(latest.sessionId, -1)
        }
        dao.deleteSnapshot(PREVIOUS)
        dao.put(session.encode(LATEST))
        DomainResult.Success(session)
    }

    override suspend fun findReceipt(key: OperationKey) = transaction {
        DomainResult.Success(dao.receipt(key.sessionId, key.id)?.decode())
    }

    override suspend fun commit(
        expected: SessionRevision,
        key: OperationKey,
        transition: GameTransition
    ) = transaction {
        val old = dao.snapshot(LATEST) ?: return@transaction failure(GameError.NoSession)
        if (key.sessionId != old.sessionId || key.sessionId != expected.sessionId ||
            key.id.isBlank() || transition.session.id != old.sessionId
        ) {
            return@transaction failure(GameError.RevisionConflict)
        }
        val prior = dao.receipt(key.sessionId, key.id)
        val persisted = old.decode()
        if (persisted == null) {
            return@transaction failure(
                GameError.InvalidSnapshot(dao.snapshot(PREVIOUS)?.decode() != null)
            )
        }
        if (prior != null) {
            return@transaction DomainResult.Success(CommittedOperation(persisted, prior.decode()))
        }
        if (old.identity != expected) return@transaction failure(GameError.RevisionConflict)
        val session = transition.session.copy(revision = Math.addExact(old.revision, 1L))
        if (!session.isValid()) return@transaction failure(GameError.InvalidSnapshot(false))
        val mismatchedRecord = transition.transactions.any {
            it.sessionId != key.sessionId || it.operationId != key.id
        }
        if (mismatchedRecord) {
            return@transaction failure(GameError.InvalidInput("transactions"))
        }
        val receipt = OperationReceipt(key, session.revision, transition.effect)
        dao.insertTransactions(
            transition.transactions.mapIndexed { position, record ->
                record.encode(session.revision, position)
            }
        )
        dao.insertReceipt(receipt.encode())
        save(old, session)
        DomainResult.Success(CommittedOperation(session, receipt))
    }

    override suspend fun checkpoint(expected: SessionRevision, session: GameSession) = transaction {
        val old = dao.snapshot(LATEST) ?: return@transaction failure(GameError.NoSession)
        if (old.identity != expected || session.id != old.sessionId) {
            return@transaction failure(GameError.RevisionConflict)
        }
        if (old.decode() == null) {
            return@transaction failure(
                GameError.InvalidSnapshot(dao.snapshot(PREVIOUS)?.decode() != null)
            )
        }
        val next = session.copy(revision = Math.addExact(old.revision, 1L))
        if (!next.isValid()) return@transaction failure(GameError.InvalidSnapshot(false))
        save(old, next)
        DomainResult.Success(next)
    }

    override suspend fun readTransactions(revision: SessionRevision) = transaction {
        val old = dao.snapshot(LATEST)
        if (old?.identity != revision) return@transaction failure(GameError.RevisionConflict)
        DomainResult.Success(dao.transactions(revision.sessionId).map(TransactionRow::decode))
    }

    private suspend fun save(old: SnapshotRow, next: GameSession) {
        dao.put(old.copy(slot = PREVIOUS))
        dao.put(next.encode(LATEST))
    }

    private suspend fun <T> transaction(block: suspend () -> DomainResult<T>): DomainResult<T> =
        try {
            db.withTransaction(block)
        } catch (_: SQLiteException) {
            failure(GameError.StorageFailure)
        } catch (_: IllegalArgumentException) {
            failure(GameError.StorageFailure)
        } catch (_: ArithmeticException) {
            failure(GameError.LimitExceeded)
        }
}

private fun failure(error: GameError) = DomainResult.Failure(error)

private val SnapshotRow.identity get() = SessionRevision(sessionId, revision)

private fun GameSession.encode(slot: Int): SnapshotRow {
    val payload = Json.encodeToString(toStored())
    return SnapshotRow(slot, id, revision, FORMAT, payload, payload.sha256())
}

private fun SnapshotRow.decode(): GameSession? {
    if (formatVersion != FORMAT || sha256 != payload.sha256()) return null
    return try {
        Json.decodeFromString<StoredGameSession>(payload).toDomain()
            .takeIf { it.identity == identity && it.isValid() }
    } catch (_: IllegalArgumentException) {
        null
    }
}

private fun String.sha256() = MessageDigest.getInstance("SHA-256").digest(encodeToByteArray())
    .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }

private fun FinancialTransaction.encode(revision: Long, position: Int) = TransactionRow(
    sessionId,
    id,
    revision,
    position,
    operationId,
    gameMillis,
    category.name,
    amountMinor
)

private fun TransactionRow.decode() = FinancialTransaction(
    id,
    operationId,
    sessionId,
    gameMillis,
    TransactionCategory.valueOf(category),
    amountMinor
)

private fun OperationReceipt.encode() =
    ReceiptRow(key.sessionId, key.id, revision, FORMAT, Json.encodeToString(effect.toStored()))

private fun ReceiptRow.decode(): OperationReceipt {
    require(formatVersion == FORMAT)
    val effect = Json.decodeFromString<StoredEffect>(payload).toDomain()
    return OperationReceipt(OperationKey(sessionId, operationId), revision, effect)
}
