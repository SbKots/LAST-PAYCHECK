package com.lastpaycheck.presentation.startup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lastpaycheck.domain.catalog.GetCatalogVersion
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class StartupViewModel @Inject constructor(
    getCatalogVersion: GetCatalogVersion
) : ViewModel() {
    private val mutableCatalogVersion = MutableStateFlow<String?>(null)
    val catalogVersion = mutableCatalogVersion.asStateFlow()

    init {
        viewModelScope.launch {
            mutableCatalogVersion.value = getCatalogVersion()
        }
    }
}
