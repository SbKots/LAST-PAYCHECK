package com.lastpaycheck.domain.usecase

import com.lastpaycheck.domain.model.AuthForm
import com.lastpaycheck.domain.model.AuthMode
import com.lastpaycheck.domain.model.AuthOutcome
import com.lastpaycheck.domain.model.AuthResult
import com.lastpaycheck.domain.repository.AccountRepository

class SubmitAuthFormUseCase(
    private val repository: AccountRepository,
    private val validator: ValidateAuthForm = ValidateAuthForm()
) {
    suspend operator fun invoke(form: AuthForm, mode: AuthMode): AuthResult<AuthOutcome> {
        val errors = validator(form, mode)
        if (errors.isNotEmpty()) return AuthResult.Invalid(errors)
        val email = form.email.trim()
        return when (mode) {
            AuthMode.SIGN_IN -> repository.signIn(email, form.password)
            AuthMode.SIGN_UP -> repository.signUp(email, form.password)
            AuthMode.RECOVER -> repository.recover(email)
        }
    }
}

class GetAccountUseCase(private val repository: AccountRepository) {
    suspend operator fun invoke() = repository.current()
}

class SignOutUseCase(private val repository: AccountRepository) {
    suspend operator fun invoke() = repository.signOut()
}
