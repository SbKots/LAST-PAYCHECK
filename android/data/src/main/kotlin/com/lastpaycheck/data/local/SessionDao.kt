package com.lastpaycheck.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

@Entity(tableName = "session_snapshot")
internal data class SnapshotRow(
    @PrimaryKey val slot: Int,
    val sessionId: String,
    val revision: Long,
    val formatVersion: Int,
    val payload: String,
    val sha256: String
)

@Entity(
    tableName = "financial_transaction",
    primaryKeys = ["sessionId", "id"],
    indices = [Index(value = ["sessionId", "revision", "position"], unique = true)]
)
internal data class TransactionRow(
    val sessionId: String,
    val id: String,
    val revision: Long,
    val position: Int,
    val operationId: String,
    val gameMillis: Long,
    val category: String,
    val amountMinor: Long
)

@Entity(
    tableName = "operation_receipt",
    primaryKeys = ["sessionId", "operationId"],
    indices = [Index(value = ["sessionId", "revision"])]
)
internal data class ReceiptRow(
    val sessionId: String,
    val operationId: String,
    val revision: Long,
    val formatVersion: Int,
    val payload: String
)

@Dao
internal interface SessionDao {
    @Query("SELECT * FROM session_snapshot WHERE slot = :slot")
    suspend fun snapshot(slot: Int): SnapshotRow?

    @Upsert
    suspend fun put(row: SnapshotRow)

    @Query("DELETE FROM session_snapshot WHERE slot = :slot")
    suspend fun deleteSnapshot(slot: Int)

    @Query("SELECT * FROM operation_receipt WHERE sessionId = :id AND operationId = :key")
    suspend fun receipt(id: String, key: String): ReceiptRow?

    @Insert
    suspend fun insertReceipt(row: ReceiptRow)

    @Insert
    suspend fun insertTransactions(rows: List<TransactionRow>)

    @Query("SELECT * FROM financial_transaction WHERE sessionId = :id ORDER BY revision, position")
    suspend fun transactions(id: String): List<TransactionRow>

    @Query("DELETE FROM operation_receipt WHERE sessionId = :id AND revision > :revision")
    suspend fun trimReceipts(id: String, revision: Long)

    @Query("DELETE FROM financial_transaction WHERE sessionId = :id AND revision > :revision")
    suspend fun trimTransactions(id: String, revision: Long)
}
