package com.lastpaycheck.domain.usecase

import com.lastpaycheck.domain.engine.GameSessionCoordinator
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.GameSession
import com.lastpaycheck.domain.model.PauseReason

class SetPauseUseCase(private val coordinator: GameSessionCoordinator) {
    suspend operator fun invoke(reason: PauseReason, enabled: Boolean): DomainResult<GameSession> =
        coordinator.setPause(reason, enabled)
}
