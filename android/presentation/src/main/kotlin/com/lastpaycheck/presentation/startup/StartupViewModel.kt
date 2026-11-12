package com.lastpaycheck.presentation.startup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lastpaycheck.domain.model.CatalogManifest
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.usecase.GetCatalogManifest
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class StartupViewModel @Inject constructor(
    private val getCatalogManifest: GetCatalogManifest
) : ViewModel() {
    private val mutableCatalog = MutableStateFlow<DomainResult<CatalogManifest>?>(null)
    val catalog = mutableCatalog.asStateFlow()
    private var loading: Job? = null

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
}
