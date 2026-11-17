package com.lastpaycheck.presentation.game

import com.lastpaycheck.domain.model.GameCatalog
import com.lastpaycheck.domain.model.Product
import com.lastpaycheck.domain.model.ProductQuote
import com.lastpaycheck.domain.usecase.BuyProductUseCase
import com.lastpaycheck.domain.usecase.FinishShiftUseCase
import com.lastpaycheck.domain.usecase.PlaceBoxUseCase
import com.lastpaycheck.domain.usecase.QuoteProductUseCase
import com.lastpaycheck.domain.usecase.StartShiftUseCase

class SectionActions(
    val startShift: StartShiftUseCase,
    val placeBox: PlaceBoxUseCase,
    val finishShift: FinishShiftUseCase,
    val quote: QuoteProductUseCase,
    val buy: BuyProductUseCase
)

internal data class StoreProduct(val product: Product, val quote: ProductQuote) {
    fun totalMinor(quantity: Int): Long? =
        if (quantity > 0 && quote.unitPriceMinor <= Long.MAX_VALUE / quantity) {
            quote.unitPriceMinor * quantity
        } else {
            null
        }

    fun canAfford(quantity: Int, balanceMinor: Long): Boolean =
        totalMinor(quantity)?.let { it <= balanceMinor } ?: false
}

internal data class SectionCatalog(
    val sessionId: String,
    val day: Long,
    val gameCatalog: GameCatalog,
    val products: List<StoreProduct>
)
