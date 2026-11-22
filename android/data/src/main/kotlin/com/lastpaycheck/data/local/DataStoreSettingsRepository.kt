package com.lastpaycheck.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lastpaycheck.domain.model.AppLanguage
import com.lastpaycheck.domain.model.ControlMode
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.GameSettings
import com.lastpaycheck.domain.model.ThemePreference
import com.lastpaycheck.domain.repository.SettingsRepository
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

internal class DataStoreSettingsRepository(
    private val store: DataStore<Preferences>
) : SettingsRepository {
    override fun observe(): Flow<DomainResult<GameSettings>> =
        store.data.map(::decode).catch { error ->
            if (error !is IOException) throw error
            emit(DomainResult.Failure(GameError.StorageFailure))
        }

    override suspend fun update(settings: GameSettings): DomainResult<GameSettings> = try {
        if (settings.calibration !in -1.0..1.0) {
            DomainResult.Failure(GameError.InvalidInput("calibration"))
        } else {
            store.edit {
                it[controls] = settings.controls.name
                it[calibration] = settings.calibration
                it[sound] = settings.soundEnabled
                it[vibration] = settings.vibrationEnabled
                it[theme] = settings.theme.name
                it[language] = settings.language.name
            }
            DomainResult.Success(settings)
        }
    } catch (_: IOException) {
        DomainResult.Failure(GameError.StorageFailure)
    }

    private fun decode(prefs: Preferences): DomainResult<GameSettings> = try {
        val offset = prefs[calibration] ?: 0.0
        require(offset in -1.0..1.0)
        DomainResult.Success(
            GameSettings(
                controls = prefs[controls]?.let(ControlMode::valueOf) ?: ControlMode.TOUCH,
                calibration = offset,
                soundEnabled = prefs[sound] ?: true,
                vibrationEnabled = prefs[vibration] ?: true,
                theme = prefs[theme]?.let(ThemePreference::valueOf) ?: ThemePreference.SYSTEM,
                language = prefs[language]?.let(AppLanguage::valueOf) ?: AppLanguage.SYSTEM
            )
        )
    } catch (_: IllegalArgumentException) {
        DomainResult.Failure(GameError.StorageFailure)
    }

    private companion object {
        val controls = stringPreferencesKey("controls")
        val calibration = doublePreferencesKey("calibration")
        val sound = booleanPreferencesKey("sound_enabled")
        val vibration = booleanPreferencesKey("vibration_enabled")
        val theme = stringPreferencesKey("theme")
        val language = stringPreferencesKey("language")
    }
}
