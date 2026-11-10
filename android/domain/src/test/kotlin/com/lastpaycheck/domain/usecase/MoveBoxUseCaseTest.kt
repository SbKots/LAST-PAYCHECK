package com.lastpaycheck.domain.usecase

import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.WorkShift
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MoveBoxUseCaseTest {
    @Test
    fun movementClampsPositionAndDoesNotScore() = runTest {
        val game = Fixture(sampleSession().copy(activeActivity = sampleShift())).open()
        val move = MoveBoxUseCase(game.coordinator)

        move(game.key("first"), 0.8).ok()
        move(game.key("second"), 0.8).ok()
        val shift = game.session.activeActivity as WorkShift
        assertEquals(1.0, shift.boxPosition, 0.0)
        assertEquals(0, shift.correct)
        assertEquals("next", shift.selectedBox.id)
    }

    @Test
    fun rejectsInvalidDirectionsMissingShiftAndRepositoryFailure() = runTest {
        val game = Fixture().open()
        val move = MoveBoxUseCase(game.coordinator)
        move(game.key("nan"), Double.NaN).failsWith(GameError.InvalidInput("direction"))
        move(game.key("range"), 1.01).failsWith(GameError.InvalidInput("direction"))
        move(game.key("no-shift"), 1.0).failsWith(GameError.NoActiveShift)
        game.startShift(game.key("work"), helperJob.id).ok()
        game.store.writeError = GameError.StorageFailure
        move(game.key("move"), -1.0).failsWith(GameError.StorageFailure)
        assertEquals(0.0, (game.session.activeActivity as WorkShift).boxPosition, 0.0)
    }
}
