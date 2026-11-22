package com.lastpaycheck.domain.model

data class Account(val id: String, val email: String)

enum class AuthMode { SIGN_IN, SIGN_UP, RECOVER }

enum class FormField { NAME, EMAIL, PASSWORD, CONFIRMATION }

enum class ValidationIssue { REQUIRED, TOO_LONG, FORMAT, PASSWORD_SHORT, MISMATCH }

data class AuthForm(val email: String, val password: String = "", val confirmation: String = "")

enum class AuthError {
    NETWORK,
    CREDENTIALS,
    UNCONFIRMED,
    RATE_LIMIT,
    CONFIGURATION,
    STORAGE,
    SERVER
}

sealed interface AuthResult<out T> {
    data class Success<T>(val value: T) : AuthResult<T>
    data class Failure(val reason: AuthError) : AuthResult<Nothing>
    data class Invalid(val errors: Map<FormField, ValidationIssue>) : AuthResult<Nothing>
}

enum class AuthOutcome { SIGNED_IN, CONFIRM_EMAIL, RECOVERY_SENT }
