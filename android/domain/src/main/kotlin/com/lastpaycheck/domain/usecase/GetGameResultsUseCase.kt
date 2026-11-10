package com.lastpaycheck.domain.usecase

import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.GameStatistics
import com.lastpaycheck.domain.repository.GameSessionRepository

class GetGameResultsUseCase(repository: GameSessionRepository) {
    private val statistics = GetGameStatisticsUseCase(repository)

    suspend operator fun invoke(): DomainResult<GameStatistics> =
        statistics.read(finishedOnly = true)
}
