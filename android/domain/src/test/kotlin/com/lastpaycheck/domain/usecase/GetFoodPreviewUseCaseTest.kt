package com.lastpaycheck.domain.usecase

import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.GameClock
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.Inventory
import com.lastpaycheck.domain.model.Needs
import com.lastpaycheck.domain.model.PauseReason
import com.lastpaycheck.domain.model.TimedActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GetFoodPreviewUseCaseTest {
    private val preview = GetFoodPreviewUseCase()
    private val product = sampleCatalog().products.single()

    @Test
    fun zeroStockKeepsRealNutritionAndDurationButBlocksEating() {
        val session = sampleSession()
        val result = (preview(session, product) as DomainResult.Success).value
        assertEquals(0, result.quantity)
        assertEquals(product.durationMinutes, result.durationMinutes)
        assertEquals(GameError.EmptyInventory, result.blockedReason)
        assertEquals(Inventory(), session.inventory)
    }

    @Test
    fun showsTheSameCappedSatietyAsTheEatingRule() {
        val initial = sampleSession()
        val session = initial.copy(
            inventory = Inventory(mapOf(product.id to 2)),
            player = initial.player.copy(needs = Needs.of(72, 50, 50, 100))
        )
        val result = (preview(session, product) as DomainResult.Success).value
        assertEquals(2, result.quantity)
        assertEquals(72.0, result.satietyBefore, 0.0)
        assertEquals(100.0, result.satietyAfter, 0.0)
        assertNull(result.blockedReason)
        assertEquals(2, session.inventory.quantity(product.id))
    }

    @Test
    fun respectsPauseAndTheSingleActivityRestriction() {
        val session = sampleSession().copy(inventory = Inventory(mapOf(product.id to 1)))
        val paused = session.copy(clock = GameClock(pauseReasons = setOf(PauseReason.MANUAL)))
        assertEquals(
            GameError.Paused,
            (preview(paused, product) as DomainResult.Success).value.blockedReason
        )
        val busy = session.copy(activeActivity = TimedActivity.Rest("rest"))
        assertEquals(
            GameError.Busy,
            (preview(busy, product) as DomainResult.Success).value.blockedReason
        )
    }

    @Test
    fun invalidProductsCannotProduceAnActionPreview() {
        assertEquals(
            DomainResult.Failure(GameError.InvalidInput("foodPreview")),
            preview(sampleSession(), product.copy(satietyGain = 101))
        )
    }
}
