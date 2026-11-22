package com.lastpaycheck.domain.usecase

import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.FinancialOverview
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.flatMap
import com.lastpaycheck.domain.model.map
import com.lastpaycheck.domain.repository.CheckpointChoice
import com.lastpaycheck.domain.repository.GameSessionRepository

class GetFinancialOverviewUseCase(private val repository: GameSessionRepository) {
    suspend operator fun invoke(sessionId: String): DomainResult<FinancialOverview> =
        repository.load(CheckpointChoice.LATEST).flatMap { session ->
            if (session.id != sessionId) return@flatMap DomainResult.Failure(GameError.NoSession)
            GetGameStatisticsUseCase(repository)().flatMap { statistics ->
                repository.readTransactions(session.identity).map { transactions ->
                    FinancialOverview(
                        session,
                        statistics,
                        transactions.sortedByDescending { it.gameMillis }
                    )
                }
            }
        }
}
