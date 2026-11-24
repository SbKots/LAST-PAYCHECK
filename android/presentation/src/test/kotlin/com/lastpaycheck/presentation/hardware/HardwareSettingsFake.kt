package com.lastpaycheck.presentation.hardware

import com.lastpaycheck.domain.model.ControlMode
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.GameSettings
import com.lastpaycheck.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow

class HardwareSettingsFake : SettingsRepository {
    val data = MutableStateFlow<DomainResult<GameSettings>>(
        DomainResult.Success(GameSettings(ControlMode.TOUCH, 0.0, true, true))
    )
    var failWrites = false
    override fun observe() = data
    override suspend fun update(settings: GameSettings): DomainResult<GameSettings> {
        if (failWrites) return DomainResult.Failure(GameError.StorageFailure)
        data.value = DomainResult.Success(settings)
        return DomainResult.Success(settings)
    }
}
