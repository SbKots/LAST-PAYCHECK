package com.lastpaycheck.domain.usecase

import com.lastpaycheck.domain.engine.GameSessionCoordinator
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.GameSession

class RenameCharacterUseCase(
    private val coordinator: GameSessionCoordinator,
    private val validator: ValidateCharacterName = ValidateCharacterName()
) {
    suspend operator fun invoke(sessionId: String, name: String): DomainResult<GameSession> {
        if (validator(name) != null) return DomainResult.Failure(GameError.InvalidInput("name"))
        return coordinator.rename(sessionId, validator.normalize(name))
    }
}
