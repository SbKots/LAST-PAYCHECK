package com.lastpaycheck.domain.model

data class NewGamePreview(val initial: InitialConditions, val scenario: EconomicScenario)

data class ProductQuote(val sessionId: String, val productId: String, val unitPriceMinor: Long)
