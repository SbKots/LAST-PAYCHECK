package com.lastpaycheck.domain.usecase

import com.lastpaycheck.domain.model.AuthForm
import com.lastpaycheck.domain.model.AuthMode
import com.lastpaycheck.domain.model.FormField
import com.lastpaycheck.domain.model.ValidationIssue
import java.text.Normalizer

class ValidateCharacterName {
    fun normalize(input: String): String = Normalizer.normalize(input.trim(), Normalizer.Form.NFC)

    operator fun invoke(input: String): ValidationIssue? {
        val name = normalize(input)
        return when {
            name.isBlank() -> ValidationIssue.REQUIRED
            name.codePointCount(0, name.length) > 40 -> ValidationIssue.TOO_LONG
            !name.matches(Regex("[\\p{L}\\p{M}][\\p{L}\\p{M} '\u2019-]*")) -> ValidationIssue.FORMAT
            else -> null
        }
    }
}

class ValidateAuthForm {
    operator fun invoke(form: AuthForm, mode: AuthMode): Map<FormField, ValidationIssue> =
        buildMap {
            val email = form.email.trim()
            when {
                email.isEmpty() -> put(FormField.EMAIL, ValidationIssue.REQUIRED)
                email.length > 254 -> put(FormField.EMAIL, ValidationIssue.TOO_LONG)
                !validEmail(email) ->
                    put(FormField.EMAIL, ValidationIssue.FORMAT)
            }
            if (mode != AuthMode.RECOVER) {
                when {
                    form.password.isBlank() -> put(FormField.PASSWORD, ValidationIssue.REQUIRED)
                    mode == AuthMode.SIGN_UP && form.password.length < 6 ->
                        put(FormField.PASSWORD, ValidationIssue.PASSWORD_SHORT)
                }
            }
            if (mode == AuthMode.SIGN_UP && form.password != form.confirmation) {
                put(FormField.CONFIRMATION, ValidationIssue.MISMATCH)
            }
        }

    private fun validEmail(email: String): Boolean {
        val parts = email.split('@')
        if (parts.size != 2) return false
        val local = parts[0]
        val labels = parts[1].split('.')
        val invalidLocal = local.length !in 1..64 || local.startsWith('.') ||
            local.endsWith('.') || ".." in local || local.any {
                it.isWhitespace() || it in "<>()[]:;,\\\""
            }
        return !invalidLocal && labels.size >= 2 && labels.all {
            it.length in 1..63 && it.matches(
                Regex("[\\p{L}\\p{N}](?:[\\p{L}\\p{N}-]*[\\p{L}\\p{N}])?")
            )
        }
    }
}
