package com.lastpaycheck.presentation.startup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lastpaycheck.domain.model.CatalogSnapshot
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.ThemePreference
import com.lastpaycheck.domain.usecase.GetCatalogManifest
import com.lastpaycheck.domain.usecase.ObserveSettingsUseCase
import com.lastpaycheck.domain.usecase.UpdateSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class StartupViewModel @Inject constructor(
    private val getCatalogManifest: GetCatalogManifest,
    observeSettings: ObserveSettingsUseCase,
    private val updateSettings: UpdateSettingsUseCase
) : ViewModel() {
    private val mutableCatalog = MutableStateFlow<DomainResult<CatalogSnapshot>?>(null)
    val catalog = mutableCatalog.asStateFlow()
    val settings = observeSettings().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private var loading: Job? = null
    private var savingTheme: Job? = null

    init {
        reloadCatalog()
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
        if (savingTheme?.isActive == true) return
        savingTheme = viewModelScope.launch {
            val theme = if (darkTheme) ThemePreference.DARK else ThemePreference.LIGHT
            updateSettings(current.copy(theme = theme))
        }
    }
}
