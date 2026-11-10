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

class BuyProductUseCase(
    private val catalog: CatalogRepository,
    private val coordinator: GameSessionCoordinator
) {
    suspend operator fun invoke(
        key: OperationKey,
        productId: String,
        quantity: Int,
        quotedUnitPriceMinor: Long
    ): DomainResult<OperationReceipt> {
        if (productId.isBlank()) return failure(GameError.InvalidInput("productId"))
        if (quantity <= 0) return failure(GameError.InvalidInput("quantity"))
        if (quotedUnitPriceMinor < 0) return failure(GameError.InvalidInput("quotedUnitPriceMinor"))
        return coordinator.submit(key) { session ->
            catalog.validatedCatalog(session.catalogVersion).map {
                GameCommand.Buy(it, productId, quantity, quotedUnitPriceMinor)
            }
        }
    }
}
