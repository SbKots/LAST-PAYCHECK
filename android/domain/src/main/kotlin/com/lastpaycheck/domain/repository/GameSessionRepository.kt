package com.lastpaycheck.domain.repository

import com.lastpaycheck.domain.model.CommittedOperation
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.FinancialTransaction
import com.lastpaycheck.domain.model.GameSession
import com.lastpaycheck.domain.model.GameTransition
import com.lastpaycheck.domain.model.OperationKey
import com.lastpaycheck.domain.model.OperationReceipt
import com.lastpaycheck.domain.model.SessionRevision

enum class CheckpointChoice { LATEST, PREVIOUS }

interface GameSessionRepository {
    suspend fun load(choice: CheckpointChoice): DomainResult<GameSession>

    /** Restores the selected checkpoint together with its journal and operation receipts. */
    suspend fun restorePrevious(checkpoint: SessionRevision): DomainResult<GameSession>

    /** Atomically replaces the active session if it still matches the expected revision. */
    suspend fun replace(expected: SessionRevision?, session: GameSession): DomainResult<GameSession>

    suspend fun findReceipt(key: OperationKey): DomainResult<OperationReceipt?>

    /**
     * Atomically commits the snapshot, financial records and receipt. A repeated key returns the
     * same receipt. A revision conflict applies none of the changes.
     */
    suspend fun commit(
        expected: SessionRevision,
        key: OperationKey,
        transition: GameTransition
    ): DomainResult<CommittedOperation>

    /** Stores a checkpoint without replaying transactions or changing receipt history. */
    suspend fun checkpoint(
        expected: SessionRevision,
        session: GameSession
    ): DomainResult<GameSession>

    /** Reads the journal as of the supplied revision or reports a revision conflict. */
    suspend fun readTransactions(
        revision: SessionRevision
    ): DomainResult<List<FinancialTransaction>>
}
