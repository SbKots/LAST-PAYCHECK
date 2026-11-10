package com.lastpaycheck.domain.usecase

import com.lastpaycheck.domain.engine.GameCommand
import com.lastpaycheck.domain.engine.GameSessionCoordinator
import com.lastpaycheck.domain.model.BoxCategory
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.OperationKey
import com.lastpaycheck.domain.model.OperationReceipt
import com.lastpaycheck.domain.model.failure

class PlaceBoxUseCase(private val coordinator: GameSessionCoordinator) {
    suspend operator fun invoke(
        key: OperationKey,
        boxId: String,
        zone: BoxCategory
    ): DomainResult<OperationReceipt> {
        if (boxId.isBlank()) return failure(GameError.InvalidInput("boxId"))
        return coordinator.submit(key) { DomainResult.Success(GameCommand.PlaceBox(boxId, zone)) }
    }
}
