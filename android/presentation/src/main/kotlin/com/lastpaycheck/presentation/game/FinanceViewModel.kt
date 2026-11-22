package com.lastpaycheck.presentation.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.FinancialOverview
import com.lastpaycheck.domain.usecase.GetFinancialOverviewUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class FinanceViewModel @Inject constructor(
    private val overview: GetFinancialOverviewUseCase
) : ViewModel() {
    private val mutableState = MutableStateFlow<DomainResult<FinancialOverview>?>(null)
    internal val state = mutableState.asStateFlow()
    private var request: Job? = null

    internal fun load(sessionId: String) {
        request?.cancel()
        mutableState.value = null
        request = viewModelScope.launch { mutableState.value = overview(sessionId) }
    }
}
