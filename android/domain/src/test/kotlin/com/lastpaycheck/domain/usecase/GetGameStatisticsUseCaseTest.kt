package com.lastpaycheck.domain.usecase

import com.lastpaycheck.domain.model.FinancialTransaction
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.TransactionCategory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetGameStatisticsUseCaseTest {
    @Test
    fun summarizesCommittedWagesExpensesAndPlayer() = runTest {
        val game = Fixture().open()
        game.buy(game.key("purchase"), "food", 1, 50).ok()
        val snapshot = game.session
        game.store.seedTransactions(
            game.store.transactions + FinancialTransaction(
                "wage", "shift", snapshot.id, 0, TransactionCategory.WAGE, 240
            )
        )
        val stats = GetGameStatisticsUseCase(game.store)().ok()
        assertEquals(240L, stats.incomeMinor)
        assertEquals(50L, stats.expenseMinor)
        assertEquals(-50L, stats.byCategoryMinor[TransactionCategory.PURCHASE])
        assertEquals(snapshot.player, stats.finalPlayer)
        assertEquals(1L, stats.days)
    }

    @Test
    fun rejectsDuplicateRecordsAndPropagatesJournalFailure() = runTest {
        val game = Fixture().open()
        val statistics = GetGameStatisticsUseCase(game.store)
        game.store.journalError = GameError.StorageFailure
        statistics().failsWith(GameError.StorageFailure)
        game.store.journalError = null
        val record = FinancialTransaction(
            "same",
            "op",
            game.session.id,
            0,
            TransactionCategory.RENT,
            -1
        )
        game.store.seedTransactions(listOf(record, record))
        statistics().failsWith(GameError.InvalidSnapshot(previousAvailable = false))
    }
}
