package com.lastpaycheck.domain.usecase

import com.lastpaycheck.domain.engine.GameCommand
import com.lastpaycheck.domain.engine.GameSessionCoordinator
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.OperationKey
import com.lastpaycheck.domain.model.OperationReceipt
import com.lastpaycheck.domain.model.failure
import com.lastpaycheck.domain.model.map
import com.lastpaycheck.domain.repository.CatalogRepository

class SelectJobUseCase(
    private val catalog: CatalogRepository,
    private val coordinator: GameSessionCoordinator
) {
    suspend operator fun invoke(key: OperationKey, jobId: String): DomainResult<OperationReceipt> {
        if (jobId.isBlank()) return failure(GameError.InvalidInput("jobId"))
        return coordinator.submit(key) { session ->
            catalog.validatedCatalog(session.catalogVersion).map {
                GameCommand.SelectJob(it, jobId)
            }
        }
    }
}
