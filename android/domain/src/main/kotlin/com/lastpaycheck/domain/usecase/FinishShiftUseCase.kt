package com.lastpaycheck.domain.usecase

import com.lastpaycheck.domain.engine.GameCommand
import com.lastpaycheck.domain.engine.GameSessionCoordinator
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.OperationKey
import com.lastpaycheck.domain.model.OperationReceipt

class FinishShiftUseCase(private val coordinator: GameSessionCoordinator) {
    suspend operator fun invoke(key: OperationKey): DomainResult<OperationReceipt> {
        return coordinator.submit(key) { DomainResult.Success(GameCommand.FinishShift) }
    }
}
