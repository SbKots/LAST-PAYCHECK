package com.lastpaycheck.domain.usecase

import com.lastpaycheck.domain.engine.GameCommand
import com.lastpaycheck.domain.engine.GameSessionCoordinator
import com.lastpaycheck.domain.model.ActivityRequest
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.OperationKey
import com.lastpaycheck.domain.model.OperationReceipt
import com.lastpaycheck.domain.model.failure
import com.lastpaycheck.domain.model.map
import com.lastpaycheck.domain.repository.CatalogRepository

class StartActivityUseCase(
    private val catalog: CatalogRepository,
    private val coordinator: GameSessionCoordinator
) {
    suspend operator fun invoke(
        key: OperationKey,
        request: ActivityRequest
    ): DomainResult<OperationReceipt> {
        val id = when (request) {
            is ActivityRequest.Food -> request.productId
            is ActivityRequest.Course -> request.courseId
            else -> null
        }
        if (id?.isBlank() == true) return failure(GameError.InvalidInput("activityItemId"))
        return coordinator.submit(key) { session ->
            if (id == null) {
                DomainResult.Success(GameCommand.StartActivity(null, request))
            } else {
                catalog.validatedCatalog(session.catalogVersion).map {
                    GameCommand.StartActivity(it, request)
                }
            }
        }
    }
}
