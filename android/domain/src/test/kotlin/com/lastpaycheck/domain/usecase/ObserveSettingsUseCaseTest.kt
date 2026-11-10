package com.lastpaycheck.domain.usecase

import com.lastpaycheck.domain.model.ControlMode
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.GameSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveSettingsUseCaseTest {
    @Test
    fun observesCurrentSettingsAndTransmitsRepositoryErrors() = runTest {
        val initial = GameSettings(ControlMode.TOUCH, 0.0, true, true)
        val repository = FakeSettings(initial)
        val observed = ObserveSettingsUseCase(repository)()
        assertEquals(initial, observed.first().ok())
        val changed = initial.copy(soundEnabled = false)
        repository.update(changed).ok()
        assertEquals(changed, observed.first().ok())
        repository.failRead()
        observed.first().failsWith(GameError.StorageFailure)
    }
}
