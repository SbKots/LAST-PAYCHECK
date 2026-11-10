package com.lastpaycheck.domain.usecase

import com.lastpaycheck.domain.model.BoxCategory
import com.lastpaycheck.domain.model.GameEffect
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.WorkShift
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaceBoxUseCaseTest {
    @Test
    fun scoresCorrectAndIncorrectBoxesOnlyOnce() = runTest {
        val game = Fixture(sampleSession().copy(activeActivity = sampleShift())).open()
        val place = PlaceBoxUseCase(game.coordinator)
        val key = game.key("first")
        val receipt = place(key, "next", BoxCategory.FOOD).ok()
        assertEquals(GameEffect.Placement(true), receipt.effect)
        assertEquals(receipt, place(key, "next", BoxCategory.FOOD).ok())
        place(game.key("duplicate"), "next", BoxCategory.FOOD).failsWith(GameError.BoxAlreadyPlaced)

        val next = (game.session.activeActivity as WorkShift).selectedBox
        assertEquals(
            GameEffect.Placement(false),
            place(game.key("wrong-zone"), next.id, BoxCategory.FRAGILE).ok().effect
        )
        val shift = game.session.activeActivity as WorkShift
        assertEquals(1, shift.correct)
        assertEquals(1, shift.errors)
        assertEquals(2, shift.processedBoxIds.size)
    }

    @Test
    fun rejectsUnknownBoxesAndDoesNotRecordFailedWrite() = runTest {
        val game = Fixture(sampleSession().copy(activeActivity = sampleShift())).open()
        val place = PlaceBoxUseCase(game.coordinator)
        place(game.key("invalid"), "", BoxCategory.FOOD).failsWith(GameError.InvalidInput("boxId"))
        place(game.key("unknown"), "foreign", BoxCategory.FOOD)
            .failsWith(GameError.NotFound("foreign"))
        game.store.writeError = GameError.StorageFailure
        place(game.key("place"), "next", BoxCategory.FOOD).failsWith(GameError.StorageFailure)
        assertEquals(0, (game.session.activeActivity as WorkShift).correct)
    }
}
