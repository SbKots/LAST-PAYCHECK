package com.lastpaycheck.domain.usecase

import com.lastpaycheck.domain.engine.GameSessionCoordinator
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.GameSession
import com.lastpaycheck.domain.repository.CheckpointChoice

class ContinueGameUseCase(private val coordinator: GameSessionCoordinator) {
    suspend operator fun invoke(
        choice: CheckpointChoice = CheckpointChoice.LATEST
    ): DomainResult<GameSession> = coordinator.resume(choice)
}
