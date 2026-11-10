package com.lastpaycheck.domain.usecase

import com.lastpaycheck.domain.model.GameEffect
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.PauseReason
import com.lastpaycheck.domain.model.Product
import com.lastpaycheck.domain.model.TransactionCategory
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class BuyProductUseCaseTest {
    @Test
    fun purchaseIsAtomicAndConcurrentReplayKeepsTheFirstReceipt() = runTest {
        val game = Fixture().open()
        val key = game.key("purchase")
        val first = async { game.buy(key, "food", 2, 50).ok() }
        val second = async { game.buy(key, "food", 2, 50).ok() }
        val receipt = first.await()

        assertEquals(receipt, second.await())
        assertEquals(GameEffect.Purchase("food", 2, 100), receipt.effect)
        assertEquals(50L, game.session.player.moneyMinor)
        assertEquals(2, game.session.inventory.quantity("food"))
        assertEquals(-100L, game.store.transactions.single().amountMinor)
        assertEquals(TransactionCategory.PURCHASE, game.store.transactions.single().category)
        assertEquals(1, game.store.writes)
        game.catalog.readError = GameError.CatalogUnavailable
        assertEquals(receipt, game.buy(key, "food", 2, 50).ok())
    }

    @Test
    fun rejectsBadQuantityMissingProductStalePriceAndInsufficientBalance() = runTest {
        val game = Fixture().open()
        val key = game.key("purchase")
        game.buy(key, "food", 0, 50).failsWith(GameError.InvalidInput("quantity"))
        game.buy(key, "food", 1, -1).failsWith(GameError.InvalidInput("quotedUnitPriceMinor"))
        game.buy(key, "missing", 1, 50).failsWith(GameError.NotFound("missing"))
        game.buy(key, "food", 1, 49).failsWith(GameError.PriceChanged(50))
        game.buy(key, "food", 4, 50).failsWith(GameError.InsufficientFunds(200, 150))
        assertEquals(0, game.store.writes)
        assertEquals(150L, game.session.player.moneyMinor)
    }

    @Test
    fun failedWriteRollsBackAndRetryRequiresSuccessfulCheckpoint() = runTest {
        val game = Fixture().open()
        val key = game.key("purchase")
        game.store.writeError = GameError.StorageFailure
        game.buy(key, "food", 2, 50).failsWith(GameError.StorageFailure)
        assertEquals(setOf(PauseReason.STORAGE_FAILURE), game.session.clock.pauseReasons)
        assertEquals(0, game.session.inventory.quantity("food"))
        assertEquals(150L, game.session.player.moneyMinor)
        assertEquals(0, game.store.transactions.size)
        game.store.writeError = null
        game.buy(key, "food", 2, 50).failsWith(GameError.Paused)
        SaveProgressUseCase(game.coordinator)().ok()
        game.buy(key, "food", 2, 50).ok()
        assertEquals(1, game.store.transactions.size)
    }

    @Test
    fun revisionConflictReloadsWithoutAutomaticallySpending() = runTest {
        val game = Fixture().open()
        val current = game.session
        val changed = current.copy(player = current.player.copy(moneyMinor = 75))
        game.store.checkpoint(current.identity, changed).ok()

        game.buy(game.key("purchase"), "food", 1, 50).failsWith(GameError.RevisionConflict)
        assertEquals(75L, game.session.player.moneyMinor)
        assertEquals(0, game.store.transactions.size)
    }

    @Test
    fun totalPriceOverflowReturnsATypedErrorWithoutChangingBalance() = runTest {
        val catalog = sampleCatalog().copy(
            products = listOf(Product("food", Long.MAX_VALUE, 30, 10))
        )
        val game = Fixture(sampleSession(catalog), FakeCatalog(catalog)).open()

        game.buy(game.key("purchase"), "food", 2, Long.MAX_VALUE).failsWith(GameError.LimitExceeded)
        assertEquals(150L, game.session.player.moneyMinor)
        assertEquals(0, game.store.writes)
    }
}
