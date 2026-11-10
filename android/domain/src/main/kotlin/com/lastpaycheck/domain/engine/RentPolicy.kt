package com.lastpaycheck.domain.engine

import com.lastpaycheck.domain.model.GameSession

internal class RentPolicy(private val prices: PricePolicy) {
    fun charge(session: GameSession): Pair<GameSession, Long> {
        val rent = prices.price(session.dailyRentMinor, session.scenario, session.clock.day - 1)
        val player = session.player
        val paid = minOf(player.moneyMinor, rent)
        return session.copy(
            lastRentDay = session.clock.day,
            player = player.copy(
                moneyMinor = player.moneyMinor - paid,
                rentDebtMinor = Math.addExact(player.rentDebtMinor, rent - paid)
            )
        ) to paid
    }
}
