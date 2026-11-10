package com.lastpaycheck.domain.engine

import com.lastpaycheck.domain.model.EconomicScenario
import java.math.BigDecimal
import java.math.RoundingMode

internal class PricePolicy {
    fun price(baseMinor: Long, scenario: EconomicScenario, elapsedDays: Long): Long {
        if (baseMinor == 0L || elapsedDays == 0L) return baseMinor
        val annual = scenario.annualInflationPercent.movePointLeft(2).add(BigDecimal.ONE)
        if (annual.compareTo(BigDecimal.ONE) == 0) return baseMinor
        val factor = if (elapsedDays == 365L) {
            annual
        } else {
            val multiplier = StrictMath.pow(annual.toDouble(), elapsedDays / 365.0)
            if (!multiplier.isFinite()) throw ArithmeticException("Inflation exceeds money range")
            BigDecimal.valueOf(multiplier)
        }
        return BigDecimal.valueOf(baseMinor).multiply(factor)
            .setScale(0, RoundingMode.HALF_UP).longValueExact()
    }
}
