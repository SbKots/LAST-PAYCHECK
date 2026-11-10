package com.lastpaycheck.domain.repository

import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.GameSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun observe(): Flow<DomainResult<GameSettings>>
    suspend fun update(settings: GameSettings): DomainResult<GameSettings>
}
