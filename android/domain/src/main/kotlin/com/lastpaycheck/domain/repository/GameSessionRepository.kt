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

    suspend fun restorePrevious(checkpoint: SessionRevision): DomainResult<GameSession>

    suspend fun replace(expected: SessionRevision?, session: GameSession): DomainResult<GameSession>

    suspend fun findReceipt(key: OperationKey): DomainResult<OperationReceipt?>

    suspend fun commit(
        expected: SessionRevision,
        key: OperationKey,
        transition: GameTransition
    ): DomainResult<CommittedOperation>

    suspend fun checkpoint(
        expected: SessionRevision,
        session: GameSession
    ): DomainResult<GameSession>

    suspend fun readTransactions(
        revision: SessionRevision
    ): DomainResult<List<FinancialTransaction>>
}
