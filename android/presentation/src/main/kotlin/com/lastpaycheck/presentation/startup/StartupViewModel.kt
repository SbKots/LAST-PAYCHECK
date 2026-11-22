package com.lastpaycheck.presentation.startup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lastpaycheck.domain.model.CatalogSnapshot
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.GameSettings
import com.lastpaycheck.domain.model.ThemePreference
import com.lastpaycheck.domain.usecase.GetCatalogManifest
import com.lastpaycheck.domain.usecase.ObserveSettingsUseCase
import com.lastpaycheck.domain.usecase.UpdateSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class StartupViewModel @Inject constructor(
    private val getCatalogManifest: GetCatalogManifest,
    private val observeSettings: ObserveSettingsUseCase,
    private val updateSettings: UpdateSettingsUseCase
) : ViewModel() {
    private val mutableCatalog = MutableStateFlow<DomainResult<CatalogSnapshot>?>(null)
    val catalog = mutableCatalog.asStateFlow()
    private val mutableSettings = MutableStateFlow<DomainResult<GameSettings>?>(null)
    val settings = mutableSettings.asStateFlow()
    private var settingsRequest: Job? = null
    private var loading: Job? = null
    private var savingTheme: Job? = null
    private val mutableSettingsError = MutableStateFlow<GameError?>(null)
    val settingsError = mutableSettingsError.asStateFlow()
    private val mutableSaving = MutableStateFlow(false)
    val saving = mutableSaving.asStateFlow()

    init {
        reloadCatalog()
        reloadSettings()
    }

    fun reloadSettings() {
        settingsRequest?.cancel()
        settingsRequest = viewModelScope.launch {
            observeSettings().collect { mutableSettings.value = it }
        }
    }

    fun reloadCatalog() {
        if (loading?.isActive == true) return
        mutableCatalog.value = null
        loading = viewModelScope.launch {
            mutableCatalog.value = getCatalogManifest()
        }
    }

    fun setTheme(darkTheme: Boolean) {
        val current = (settings.value as? DomainResult.Success)?.value ?: return
        val theme = if (darkTheme) ThemePreference.DARK else ThemePreference.LIGHT
        setSettings(current.copy(theme = theme))
    }

    fun setSettings(value: GameSettings) {
        if (mutableSaving.value) return
        mutableSaving.value = true
        mutableSettingsError.value = null
        savingTheme = viewModelScope.launch {
            try {
                val result = updateSettings(value)
                mutableSettingsError.value = (result as? DomainResult.Failure)?.error
            } finally {
                mutableSaving.value = false
            }
        }
    }
}
