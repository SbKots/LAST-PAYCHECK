package com.lastpaycheck.domain.usecase

import com.lastpaycheck.domain.engine.GameCommand
import com.lastpaycheck.domain.engine.GameSessionCoordinator
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.OperationKey
import com.lastpaycheck.domain.model.OperationReceipt
import com.lastpaycheck.domain.model.failure

class MoveBoxUseCase(private val coordinator: GameSessionCoordinator) {
    suspend operator fun invoke(
        key: OperationKey,
        direction: Double
    ): DomainResult<OperationReceipt> {
        if (direction !in -1.0..1.0) return failure(GameError.InvalidInput("direction"))
        return coordinator.submit(key) { DomainResult.Success(GameCommand.MoveBox(direction)) }
    }
}
