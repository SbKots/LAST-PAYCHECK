package com.lastpaycheck.domain.usecase

import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.FinancialOverview
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.flatMap
import com.lastpaycheck.domain.repository.CheckpointChoice
import com.lastpaycheck.domain.repository.GameSessionRepository

class GetFinancialOverviewUseCase(private val repository: GameSessionRepository) {
    suspend operator fun invoke(sessionId: String): DomainResult<FinancialOverview> {
        repeat(3) {
            val result = read(sessionId)
            if (result != DomainResult.Failure(GameError.RevisionConflict)) return result
        }
        return DomainResult.Failure(GameError.RevisionConflict)
    }

    private suspend fun read(sessionId: String): DomainResult<FinancialOverview> =
        repository.load(CheckpointChoice.LATEST).flatMap { session ->
            if (session.id != sessionId) return@flatMap DomainResult.Failure(GameError.NoSession)
            repository.readTransactions(session.identity).flatMap { transactions ->
                GetGameStatisticsUseCase(repository).summarize(session, transactions).flatMap {
                    DomainResult.Success(
                        FinancialOverview(
                            session,
                            it,
                            transactions.sortedByDescending { it.gameMillis }
                        )
                    )
                }
            }
        }
}
