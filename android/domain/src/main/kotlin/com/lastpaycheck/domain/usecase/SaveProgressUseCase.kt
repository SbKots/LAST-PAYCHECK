package com.lastpaycheck.domain.usecase

import com.lastpaycheck.domain.engine.GameSessionCoordinator
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.GameSession

class SaveProgressUseCase(private val coordinator: GameSessionCoordinator) {
    suspend operator fun invoke(): DomainResult<GameSession> = coordinator.save()
}
