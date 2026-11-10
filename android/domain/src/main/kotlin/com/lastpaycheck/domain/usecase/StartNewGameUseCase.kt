package com.lastpaycheck.domain.usecase

import com.lastpaycheck.domain.engine.GameSessionCoordinator
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.GameSession
import com.lastpaycheck.domain.model.PlayerState
import com.lastpaycheck.domain.model.failure
import com.lastpaycheck.domain.model.flatMap
import com.lastpaycheck.domain.repository.CatalogRepository

class StartNewGameUseCase(
    catalog: CatalogRepository,
    private val coordinator: GameSessionCoordinator
) {
    private val preview = PreviewNewGameUseCase(catalog)

    suspend operator fun invoke(
        sessionId: String,
        scenarioId: String,
        catalogVersion: String,
        replaceSessionId: String? = null,
        randomSeed: Long = sessionId.hashCode().toLong()
    ): DomainResult<GameSession> {
        if (sessionId.isBlank()) return failure(GameError.InvalidInput("sessionId"))
        if (replaceSessionId?.isBlank() == true || replaceSessionId == sessionId) {
            return failure(GameError.InvalidInput("replaceSessionId"))
        }
        return preview(scenarioId, catalogVersion).flatMap { (initial, scenario) ->
            val session = GameSession(
                id = sessionId,
                catalogVersion = scenario.version,
                player = PlayerState(initial.moneyMinor, initial.needs, initial.jobId),
                scenario = scenario,
                dailyRentMinor = initial.dailyRentMinor,
                randomState = randomSeed
            )
            coordinator.start(session, replaceSessionId)
        }
    }
}
