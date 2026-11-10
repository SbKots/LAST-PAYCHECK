package com.lastpaycheck.domain.usecase

import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.FinancialTransaction
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.GameSession
import com.lastpaycheck.domain.model.GameStatistics
import com.lastpaycheck.domain.model.SessionStatus
import com.lastpaycheck.domain.model.TransactionCategory
import com.lastpaycheck.domain.model.checkedArithmetic
import com.lastpaycheck.domain.model.failure
import com.lastpaycheck.domain.model.flatMap
import com.lastpaycheck.domain.repository.CheckpointChoice
import com.lastpaycheck.domain.repository.GameSessionRepository

class GetGameStatisticsUseCase(private val repository: GameSessionRepository) {
    suspend operator fun invoke(): DomainResult<GameStatistics> = read(finishedOnly = false)

    internal suspend fun read(finishedOnly: Boolean): DomainResult<GameStatistics> =
        repository.load(CheckpointChoice.LATEST).flatMap { session ->
            if (!session.isValid()) return@flatMap invalidSnapshot()
            if (finishedOnly && session.status != SessionStatus.FINISHED) {
                return@flatMap failure(GameError.GameInProgress)
            }
            repository.readTransactions(session.identity).flatMap { records ->
                summarize(session, records)
            }
        }

    private fun summarize(
        session: GameSession,
        records: List<FinancialTransaction>
    ): DomainResult<GameStatistics> = checkedArithmetic {
        var income = 0L
        var expense = 0L
        val ids = mutableSetOf<String>()
        val categories = mutableMapOf<TransactionCategory, Long>()
        for (record in records) {
            val isWage = record.category == TransactionCategory.WAGE
            val validTime = record.gameMillis in 0..session.clock.gameMillis
            if (record.sessionId != session.id || !validTime ||
                record.id.isBlank() || !ids.add(record.id) || record.operationId.isBlank() ||
                (isWage && record.amountMinor <= 0) || (!isWage && record.amountMinor >= 0)
            ) {
                return invalidSnapshot()
            }
            if (isWage) {
                income = Math.addExact(income, record.amountMinor)
            } else {
                expense = Math.subtractExact(expense, record.amountMinor)
            }
            categories[record.category] = Math.addExact(
                categories.getOrDefault(record.category, 0),
                record.amountMinor
            )
        }
        DomainResult.Success(
            GameStatistics(
                session.id,
                income,
                expense,
                categories.toMap(),
                session.clock.day,
                session.workMillis,
                session.player,
                session.finishReason
            )
        )
    }

    private fun invalidSnapshot() = failure(GameError.InvalidSnapshot(previousAvailable = false))
}
