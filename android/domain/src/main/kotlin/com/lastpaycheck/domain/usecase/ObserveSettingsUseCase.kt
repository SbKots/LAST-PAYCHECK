package com.lastpaycheck.domain.usecase

import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.GameSettings
import com.lastpaycheck.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow

class ObserveSettingsUseCase(private val settings: SettingsRepository) {
    operator fun invoke(): Flow<DomainResult<GameSettings>> = settings.observe()
}
