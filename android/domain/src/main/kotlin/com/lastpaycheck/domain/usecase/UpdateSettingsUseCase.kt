package com.lastpaycheck.domain.usecase

import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.GameSettings
import com.lastpaycheck.domain.model.failure
import com.lastpaycheck.domain.repository.SettingsRepository

class UpdateSettingsUseCase(private val settings: SettingsRepository) {
    suspend operator fun invoke(value: GameSettings): DomainResult<GameSettings> {
        if (value.calibration !in -1.0..1.0) {
            return failure(GameError.InvalidInput("calibration"))
        }
        return settings.update(value)
    }
}
