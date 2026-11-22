package com.lastpaycheck.domain.repository

import com.lastpaycheck.domain.model.Account
import com.lastpaycheck.domain.model.AuthOutcome
import com.lastpaycheck.domain.model.AuthResult

interface AccountRepository {
    suspend fun current(): AuthResult<Account?>
    suspend fun signIn(email: String, password: String): AuthResult<AuthOutcome>
    suspend fun signUp(email: String, password: String): AuthResult<AuthOutcome>
    suspend fun recover(email: String): AuthResult<AuthOutcome>
    suspend fun signOut(): AuthResult<Unit>
}
