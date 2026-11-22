package com.lastpaycheck.presentation.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lastpaycheck.domain.model.Account
import com.lastpaycheck.domain.model.AuthError
import com.lastpaycheck.domain.model.AuthForm
import com.lastpaycheck.domain.model.AuthMode
import com.lastpaycheck.domain.model.AuthOutcome
import com.lastpaycheck.domain.model.AuthResult
import com.lastpaycheck.domain.model.FormField
import com.lastpaycheck.domain.model.ValidationIssue
import com.lastpaycheck.domain.usecase.GetAccountUseCase
import com.lastpaycheck.domain.usecase.SignOutUseCase
import com.lastpaycheck.domain.usecase.SubmitAuthFormUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AccountUiState(
    val form: AuthForm = AuthForm(""),
    val mode: AuthMode = AuthMode.SIGN_IN,
    val account: Account? = null,
    val loading: Boolean = false,
    val errors: Map<FormField, ValidationIssue> = emptyMap(),
    val error: AuthError? = null,
    val outcome: AuthOutcome? = null
)

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val getAccount: GetAccountUseCase,
    private val submit: SubmitAuthFormUseCase,
    private val signOut: SignOutUseCase
) : ViewModel() {
    private val mutableState = MutableStateFlow(AccountUiState())
    val state = mutableState.asStateFlow()
    private var retryAction: () -> Unit = ::reload

    init {
        reload()
    }

    fun edit(form: AuthForm) {
        if (!state.value.loading) {
            mutableState.value = state.value.copy(form = form, errors = emptyMap(), outcome = null)
        }
    }

    fun mode(mode: AuthMode) {
        if (!state.value.loading) {
            mutableState.value = state.value.copy(
                mode = mode, errors = emptyMap(), error = null, outcome = null
            )
        }
    }

    fun send() = runRequest {
        retryAction = ::send
        when (val result = submit(state.value.form, state.value.mode)) {
            is AuthResult.Invalid -> mutableState.value = state.value.copy(errors = result.errors)
            is AuthResult.Failure -> mutableState.value = state.value.copy(error = result.reason)
            is AuthResult.Success -> {
                mutableState.value = state.value.copy(
                    form = state.value.form.copy(password = "", confirmation = ""),
                    outcome = result.value
                )
                if (result.value == AuthOutcome.SIGNED_IN) {
                    retryAction = ::reload
                    loadAccount()
                }
            }
        }
    }

    fun reload() = runRequest {
        retryAction = ::reload
        loadAccount()
    }

    fun retry() = retryAction()

    fun logout() = runRequest {
        retryAction = ::logout
        when (val result = signOut()) {
            is AuthResult.Success -> mutableState.value = AccountUiState(loading = true)
            is AuthResult.Failure -> mutableState.value = state.value.copy(error = result.reason)
            is AuthResult.Invalid -> Unit
        }
    }

    private suspend fun loadAccount() {
        when (val result = getAccount()) {
            is AuthResult.Success -> mutableState.value = state.value.copy(account = result.value)
            is AuthResult.Failure -> mutableState.value = state.value.copy(error = result.reason)
            is AuthResult.Invalid -> Unit
        }
    }

    private fun runRequest(block: suspend () -> Unit) {
        if (state.value.loading) return
        mutableState.value = state.value.copy(
            loading = true, error = null, errors = emptyMap(), outcome = null
        )
        viewModelScope.launch {
            try {
                block()
            } finally {
                mutableState.value = state.value.copy(loading = false)
            }
        }
    }
}
