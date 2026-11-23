package com.lastpaycheck.domain.usecase

import com.lastpaycheck.domain.model.FinancialTransaction
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.TransactionCategory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FinancialOverviewRegressionTest {
    @Test fun racePrizeIsIncomeAndVehicleExpensesRemainExpenses() = runTest {
        val game = Fixture().open()
        val amounts = listOf(
            TransactionCategory.CAR_PURCHASE to -6000L,
            TransactionCategory.CAR_UPGRADE to -600L,
            TransactionCategory.CAR_REPAIR to -300L,
            TransactionCategory.RACE_ENTRY to -100L,
            TransactionCategory.RACE_PRIZE to 180L,
            TransactionCategory.WAGE to 250L
        )
        game.store.seedTransactions(
            amounts.mapIndexed { index, (category, amount) ->
                FinancialTransaction(
                    "row-$index",
                    "op-$index",
                    game.session.id,
                    0,
                    category,
                    amount
                )
            }
        )
        val overview = GetFinancialOverviewUseCase(game.store)(game.session.id).ok()
        assertEquals(430L, overview.statistics.incomeMinor)
        assertEquals(7000L, overview.statistics.expenseMinor)
        assertEquals(6, overview.transactions.size)
        assertEquals(game.session.player.moneyMinor, overview.session.player.moneyMinor)
    }

    @Test fun emptyJournalAndRetryNeverReplaceTheSession() = runTest {
        val game = Fixture().open()
        val original = game.session
        val overview = GetFinancialOverviewUseCase(game.store)
        game.store.journalError = GameError.StorageFailure
        overview(original.id).failsWith(GameError.StorageFailure)
        game.store.journalError = null
        val restored = overview(original.id).ok()
        assertEquals(original, restored.session)
        assertEquals(0L, restored.statistics.incomeMinor)
        assertEquals(0L, restored.statistics.expenseMinor)
        assertEquals(emptyList<FinancialTransaction>(), restored.transactions)
    }
}
