package com.lastpaycheck.domain.usecase

import com.lastpaycheck.domain.model.ControlMode
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.GameSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateSettingsUseCaseTest {
    @Test
    fun savesControlsAndPreferencesIncludingCalibrationEndpoints() = runTest {
        val initial = GameSettings(ControlMode.TOUCH, 0.0, true, true)
        val repository = FakeSettings(initial)
        val update = UpdateSettingsUseCase(repository)
        val changed = GameSettings(ControlMode.TILT, -1.0, false, false)
        assertEquals(changed, update(changed).ok())
        val otherLimit = changed.copy(calibration = 1.0)
        assertEquals(otherLimit, update(otherLimit).ok())
        assertEquals(otherLimit, repository.observe().first().ok())
    }

    @Test
    fun rejectsInvalidCalibrationAndPreservesStateAfterFailure() = runTest {
        val initial = GameSettings(ControlMode.TOUCH, 0.0, true, true)
        val repository = FakeSettings(initial)
        val update = UpdateSettingsUseCase(repository)
        for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.01)) {
            update(initial.copy(calibration = value))
                .failsWith(GameError.InvalidInput("calibration"))
        }
        repository.error = GameError.StorageFailure
        update(initial.copy(soundEnabled = false)).failsWith(GameError.StorageFailure)
        assertEquals(initial, repository.observe().first().ok())
        assertEquals(0, repository.writes)
    }
}
