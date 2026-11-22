package com.lastpaycheck.presentation.account

import androidx.lifecycle.viewModelScope
import com.lastpaycheck.domain.model.Account
import com.lastpaycheck.domain.model.AuthError
import com.lastpaycheck.domain.model.AuthForm
import com.lastpaycheck.domain.model.AuthMode
import com.lastpaycheck.domain.model.AuthOutcome
import com.lastpaycheck.domain.model.AuthResult
import com.lastpaycheck.domain.model.FormField
import com.lastpaycheck.domain.repository.AccountRepository
import com.lastpaycheck.domain.usecase.GetAccountUseCase
import com.lastpaycheck.domain.usecase.SignOutUseCase
import com.lastpaycheck.domain.usecase.SubmitAuthFormUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {
    @Test fun validatesBeforeNetworkAndBlocksDoubleSubmitWhilePreservingInputOnError() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = FakeAccount()
        val model =
            AccountViewModel(
                GetAccountUseCase(repository),
                SubmitAuthFormUseCase(repository),
                SignOutUseCase(repository)
            )
        try {
            runCurrent()
            model.send()
            runCurrent()
            assertTrue(model.state.value.errors.containsKey(FormField.EMAIL))
            assertEquals(0, repository.calls)
            val input = AuthForm(" player@example.com ", "secret")
            model.edit(input)
            model.send()
            model.send()
            assertTrue(model.state.value.loading)
            runCurrent()
            assertEquals(1, repository.calls)
            repository.response.complete(AuthResult.Failure(AuthError.NETWORK))
            runCurrent()
            assertEquals(input, model.state.value.form)
            assertEquals(AuthError.NETWORK, model.state.value.error)
            assertFalse(model.state.value.loading)
            model.retry()
            runCurrent()
            assertEquals(2, repository.calls)
            assertEquals(input, model.state.value.form)
        } finally {
            model.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test fun successfulLoginClearsPasswordLoadsProfileAndLogoutReturnsGuest() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = FakeAccount()
        val model =
            AccountViewModel(
                GetAccountUseCase(repository),
                SubmitAuthFormUseCase(repository),
                SignOutUseCase(repository)
            )
        try {
            runCurrent()
            model.edit(AuthForm("player@example.com", "secret"))
            model.send()
            runCurrent()
            repository.account = Account("id", "player@example.com")
            repository.response.complete(AuthResult.Success(AuthOutcome.SIGNED_IN))
            runCurrent()
            assertEquals("", model.state.value.form.password)
            assertEquals(repository.account, model.state.value.account)
            assertEquals(AuthOutcome.SIGNED_IN, model.state.value.outcome)
            model.logout()
            runCurrent()
            assertNull(model.state.value.account)
            assertEquals(1, repository.signOuts)
            model.mode(AuthMode.SIGN_UP)
            model.edit(AuthForm("a@b.com", "123", "other"))
            model.send()
            runCurrent()
            assertTrue(model.state.value.errors.containsKey(FormField.CONFIRMATION))
            assertEquals(1, repository.calls)
        } finally {
            model.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    private class FakeAccount : AccountRepository {
        var calls = 0
        var signOuts = 0
        var account: Account? = null
        val response = CompletableDeferred<AuthResult<AuthOutcome>>()
        override suspend fun current() = AuthResult.Success(account)
        override suspend fun signIn(email: String, password: String): AuthResult<AuthOutcome> {
            assertEquals(email.trim(), email)
            calls++
            return response.await()
        }
        override suspend fun signUp(email: String, password: String) = signIn(email, password)
        override suspend fun recover(email: String) = signIn(email, "")
        override suspend fun signOut(): AuthResult<Unit> {
            signOuts++
            account = null
            return AuthResult.Success(Unit)
        }
    }
}
