package com.lastpaycheck.domain.usecase

import com.lastpaycheck.domain.engine.GameSessionCoordinator
import com.lastpaycheck.domain.engine.PricePolicy
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.ProductQuote
import com.lastpaycheck.domain.model.checkedArithmetic
import com.lastpaycheck.domain.model.failure
import com.lastpaycheck.domain.model.flatMap
import com.lastpaycheck.domain.repository.CatalogRepository

class QuoteProductUseCase(
    private val catalog: CatalogRepository,
    private val coordinator: GameSessionCoordinator
) {
    private val prices = PricePolicy()

    suspend operator fun invoke(productId: String): DomainResult<ProductQuote> = checkedArithmetic {
        if (productId.isBlank()) return failure(GameError.InvalidInput("productId"))
        val session = coordinator.session.value ?: return failure(GameError.NoSession)
        catalog.validatedCatalog(session.catalogVersion).flatMap { current ->
            val product = current.products.find { it.id == productId }
                ?: return@flatMap failure(GameError.NotFound(productId))
            val unitPrice = prices.price(
                product.basePriceMinor,
                session.scenario,
                session.clock.day - 1
            )
            DomainResult.Success(ProductQuote(session.id, productId, unitPrice))
        }
    }
}
