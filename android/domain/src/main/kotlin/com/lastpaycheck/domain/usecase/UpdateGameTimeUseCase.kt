package com.lastpaycheck.domain.usecase

import com.lastpaycheck.domain.engine.GameSessionCoordinator
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.GameSession

class UpdateGameTimeUseCase(private val coordinator: GameSessionCoordinator) {
    suspend operator fun invoke(): DomainResult<GameSession> = coordinator.advance()
}
