package com.lastpaycheck.presentation.account

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lastpaycheck.domain.model.AuthError
import com.lastpaycheck.domain.model.AuthMode
import com.lastpaycheck.domain.model.AuthOutcome
import com.lastpaycheck.domain.model.FormField
import com.lastpaycheck.presentation.R
import com.lastpaycheck.presentation.game.GameBackButton
import com.lastpaycheck.presentation.game.SectionCard
import com.lastpaycheck.presentation.game.SectionPage
import com.lastpaycheck.presentation.game.validationMessage
import com.lastpaycheck.presentation.ui.components.LpBody
import com.lastpaycheck.presentation.ui.components.LpButton
import com.lastpaycheck.presentation.ui.components.LpButtonStyle
import com.lastpaycheck.presentation.ui.components.LpCaption
import com.lastpaycheck.presentation.ui.components.LpTextField
import com.lastpaycheck.presentation.ui.theme.LastPaycheckTheme

@Composable
internal fun AccountScreen(viewModel: AccountViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SectionPage(stringResource(R.string.progress_account), "", null, onBack, {
        GameBackButton(onBack)
    }) {
        LpCaption(stringResource(R.string.progress_account_hint))
        val account = state.account
        if (account != null) {
            SectionCard(account.email) {
                LpButton(
                    stringResource(R.string.progress_sign_out),
                    viewModel::logout,
                    Modifier.fillMaxWidth(),
                    enabled = !state.loading
                )
            }
        } else {
            SectionCard(stringResource(modeLabel(state.mode))) {
                LpTextField(
                    state.form.email,
                    { viewModel.edit(state.form.copy(email = it)) },
                    stringResource(R.string.progress_email),
                    Modifier.fillMaxWidth(),
                    errorMessage = state.errors[FormField.EMAIL]?.let {
                        validationMessage(
                            it,
                            email = true
                        )
                    },
                    enabled = !state.loading,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )
                if (state.mode != AuthMode.RECOVER) {
                    LpTextField(
                        state.form.password,
                        { viewModel.edit(state.form.copy(password = it)) },
                        stringResource(R.string.progress_password),
                        Modifier.fillMaxWidth(),
                        errorMessage = state.errors[FormField.PASSWORD]?.let {
                            validationMessage(it)
                        },
                        enabled = !state.loading,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
                if (state.mode == AuthMode.SIGN_UP) {
                    LpTextField(
                        state.form.confirmation,
                        { viewModel.edit(state.form.copy(confirmation = it)) },
                        stringResource(R.string.progress_confirmation),
                        Modifier.fillMaxWidth(),
                        errorMessage = state.errors[FormField.CONFIRMATION]?.let {
                            validationMessage(it)
                        },
                        enabled = !state.loading,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
                LpButton(
                    stringResource(
                        if (state.loading) R.string.progress_loading else modeLabel(state.mode)
                    ),
                    viewModel::send,
                    Modifier.fillMaxWidth(),
                    enabled = !state.loading
                )
            }
            AuthMode.entries.filter { it != state.mode }.forEach { mode ->
                LpButton(
                    stringResource(modeLabel(mode)),
                    { viewModel.mode(mode) },
                    Modifier.fillMaxWidth(),
                    LpButtonStyle.Secondary,
                    enabled = !state.loading
                )
            }
        }
        state.error?.let { error ->
            LpBody(stringResource(authErrorLabel(error)), color = LastPaycheckTheme.colors.error)
            LpButton(
                stringResource(R.string.catalog_retry),
                viewModel::retry,
                Modifier.fillMaxWidth(),
                enabled = !state.loading
            )
            LpButton(
                stringResource(R.string.progress_forget_session),
                viewModel::logout,
                Modifier.fillMaxWidth(),
                LpButtonStyle.Secondary,
                enabled = !state.loading
            )
        }
        state.outcome?.let { outcome ->
            LpBody(
                stringResource(
                    when (outcome) {
                        AuthOutcome.SIGNED_IN -> R.string.progress_signed_in
                        AuthOutcome.CONFIRM_EMAIL -> R.string.progress_email_sent
                        AuthOutcome.RECOVERY_SENT -> R.string.progress_recovery_sent
                    }
                )
            )
        }
    }
}

private fun modeLabel(mode: AuthMode): Int = when (mode) {
    AuthMode.SIGN_IN -> R.string.progress_sign_in
    AuthMode.SIGN_UP -> R.string.progress_sign_up
    AuthMode.RECOVER -> R.string.progress_recover
}

private fun authErrorLabel(error: AuthError): Int = when (error) {
    AuthError.NETWORK -> R.string.progress_auth_network
    AuthError.CREDENTIALS -> R.string.progress_auth_credentials
    AuthError.UNCONFIRMED -> R.string.progress_auth_unconfirmed
    AuthError.RATE_LIMIT -> R.string.progress_auth_rate
    AuthError.CONFIGURATION -> R.string.progress_auth_configuration
    AuthError.STORAGE -> R.string.progress_auth_storage
    AuthError.SERVER -> R.string.progress_auth_server
}
