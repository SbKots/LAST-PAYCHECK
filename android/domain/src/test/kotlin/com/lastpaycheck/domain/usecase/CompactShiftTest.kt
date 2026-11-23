package com.lastpaycheck.domain.usecase

import com.lastpaycheck.domain.model.BoxCategory
import com.lastpaycheck.domain.model.GameEffect
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.TransactionCategory
import com.lastpaycheck.domain.model.WorkShift
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompactShiftTest {
    @Test fun timeAfterCompactShiftCompletionUsesNormalSpeed() = runTest {
        val game = Fixture().open()
        game.startShift(game.key("shift"), helperJob.id).ok()
        game.tick(90_001).ok()
        assertNull(game.session.activeActivity)
        assertEquals(36_000_144L, game.session.clock.gameMillis)
    }

    @Test fun twentyFourMatchingBoxesCompleteInAMinuteAndPayOnlyOnce() = runTest {
        val game = Fixture().open()
        game.startShift(game.key("shift"), helperJob.id).ok()
        val place = PlaceBoxUseCase(game.coordinator)
        repeat(24) { index ->
            if (index > 0) game.clock.now += 2500
            val box = (game.session.activeActivity as WorkShift).selectedBox
            val receipt = place(game.key("box-$index"), box.id, box.category).ok()
            if (index == 23) {
                assertEquals(500L, (receipt.effect as GameEffect.ShiftFinished).result.wageMinor)
                assertEquals(receipt, place(game.key("box-$index"), box.id, box.category).ok())
            }
        }
        assertEquals(57_500L, game.clock.now)
        assertNull(game.session.activeActivity)
        assertEquals(650L, game.session.player.moneyMinor)
        assertEquals(1, game.store.transactions.count { it.category == TransactionCategory.WAGE })
        FinishShiftUseCase(game.coordinator)(game.key("again")).failsWith(GameError.NoActiveShift)
    }

    @Test fun wrongZoneExplainsTheErrorAndConveyorRejectsPrematurePlacement() = runTest {
        val game = Fixture().open()
        game.startShift(game.key("shift"), helperJob.id).ok()
        val place = PlaceBoxUseCase(game.coordinator)
        val first = (game.session.activeActivity as WorkShift).selectedBox
        val wrong = BoxCategory.entries.first { it != first.category }
        val feedback = place(game.key("wrong"), first.id, wrong).ok().effect as GameEffect.Placement
        assertEquals(false, feedback.correct)
        assertEquals(first.category, feedback.expected)
        assertEquals(wrong, feedback.actual)
        val shift = game.session.activeActivity as WorkShift
        assertEquals(1, shift.errors)
        place(game.key("too-early"), shift.selectedBox.id, shift.selectedBox.category)
            .failsWith(GameError.Busy)
    }

    @Test fun ninetySecondsEndTheShiftWithoutPayingForUnsortedBoxes() = runTest {
        val game = Fixture().open()
        game.startShift(game.key("shift"), helperJob.id).ok()
        game.clock.now = 90_000
        UpdateGameTimeUseCase(game.coordinator)().ok()
        assertNull(game.session.activeActivity)
        assertEquals(0L, game.session.lastShiftResult!!.wageMinor)
        assertEquals(36_000_000L, game.session.lastShiftResult!!.workedGameMillis)
        assertTrue(game.session.isValid())
    }
}
