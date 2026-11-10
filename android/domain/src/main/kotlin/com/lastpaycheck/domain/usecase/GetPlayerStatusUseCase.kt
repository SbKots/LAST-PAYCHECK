package com.lastpaycheck.domain.usecase

import com.lastpaycheck.domain.engine.GameSessionCoordinator
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.GameSession
import com.lastpaycheck.domain.model.failure

class GetPlayerStatusUseCase(private val coordinator: GameSessionCoordinator) {
    operator fun invoke(): DomainResult<GameSession> {
        val session = coordinator.session.value ?: return failure(GameError.NoSession)
        return DomainResult.Success(session)
    }
}
