package com.lastpaycheck.domain.usecase

import com.lastpaycheck.domain.model.AccelerationSample
import com.lastpaycheck.domain.model.BoxCategory
import com.lastpaycheck.domain.model.GameEffect
import com.lastpaycheck.domain.model.TransactionCategory
import com.lastpaycheck.domain.model.WorkShift
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TiltShiftIntegrationTest {
    @Test fun aWholeTiltShiftUsesNormalPlacementsAndPaysExactlyOneWage() = runTest {
        val game = Fixture().open()
        game.startShift(game.key("shift"), helperJob.id).ok()
        val gesture = TiltSortingGesture()
        gesture.configure(TiltCalibration(0.0, 0))
        var time = 0L
        fun send(x: Double) = gesture.sample(
            AccelerationSample(x, 0.0, 9.80665, time.also { time += 100_000_000 }),
            true
        )
        repeat(24) { index ->
            game.clock.now += 2500
            val box = (game.session.activeActivity as WorkShift).selectedBox
            repeat(15) { send(0.0) }
            val zone = when (box.category) {
                BoxCategory.FRAGILE -> checkNotNull(gesture.confirmCenter(true))
                else -> List(10) { send(if (box.category == BoxCategory.FOOD) 4.0 else -4.0) }
                    .mapNotNull { it.zone }.single()
            }
            val key = game.key("gesture-$index")
            val receipt = PlaceBoxUseCase(game.coordinator)(key, box.id, zone).ok()
            assertEquals(receipt, PlaceBoxUseCase(game.coordinator)(key, box.id, zone).ok())
            if (index == 23) {
                assertEquals(
                    500L,
                    (receipt.effect as GameEffect.ShiftFinished).result.wageMinor
                )
            }
        }
        assertNull(game.session.activeActivity)
        assertEquals(1, game.store.transactions.count { it.category == TransactionCategory.WAGE })
        assertEquals(650L, game.session.player.moneyMinor)
    }
}
