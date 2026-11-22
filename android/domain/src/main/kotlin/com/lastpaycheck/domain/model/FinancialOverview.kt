package com.lastpaycheck.domain.model

data class FinancialOverview(
    val session: GameSession,
    val statistics: GameStatistics,
    val transactions: List<FinancialTransaction>
) {
    val dailyBalances: List<Pair<Long, Long>>
        get() {
            val last = session.clock.day
            return (maxOf(1, last - 6)..last).map { day ->
                val later = transactions.filter { it.gameMillis / GAME_DAY_MILLIS + 1 > day }
                day to later.fold(session.player.moneyMinor) { balance, transaction ->
                    Math.subtractExact(balance, transaction.amountMinor)
                }
            }
        }
}
