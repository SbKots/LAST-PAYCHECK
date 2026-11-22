package com.lastpaycheck.data

import com.lastpaycheck.data.auth.AuthStore
import com.lastpaycheck.data.auth.SupabaseAccountRepository
import com.lastpaycheck.domain.model.AuthError
import com.lastpaycheck.domain.model.AuthOutcome
import com.lastpaycheck.domain.model.AuthResult
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseAccountRepositoryTest {
    @Test
    fun damagedAccountSessionCanBeClearedWithoutTouchingGameStorage() = runBlocking {
        MockWebServer().use { server ->
            val store = MemoryStore().apply { value = "damaged-session" }
            val repo = SupabaseAccountRepository(server.url("/").toString(), "public", store)
            assertEquals(AuthResult.Failure(AuthError.STORAGE), repo.current())
            assertEquals(AuthResult.Success(Unit), repo.signOut())
            assertNull(store.value)
            assertEquals(0, server.requestCount)
        }
    }

    @Test fun realAuthContractStoresTokensWithoutPasswordAndRestoresProfile() = runBlocking {
        MockWebServer().use { server ->
            val store = MemoryStore()
            val repo =
                SupabaseAccountRepository(
                    server.url("/").toString(),
                    "public",
                    store,
                    nowSeconds = { 0 }
                )
            server.enqueue(MockResponse().setBody(SESSION))
            assertEquals(
                AuthResult.Success(AuthOutcome.SIGNED_IN),
                repo.signIn("a@b.com", "my-password")
            )
            val signIn = server.takeRequest()
            assertEquals("/auth/v1/token?grant_type=password", signIn.path)
            assertEquals("public", signIn.getHeader("apikey"))
            assertTrue(signIn.body.readUtf8().contains("my-password"))
            assertFalse(checkNotNull(store.value).contains("my-password"))
            server.enqueue(MockResponse().setBody("""{"id":"user-1","email":"a@b.com"}"""))
            val reopened =
                SupabaseAccountRepository(
                    server.url("/").toString(),
                    "public",
                    store,
                    nowSeconds = { 0 }
                )
            val profile = reopened.current() as AuthResult.Success
            assertEquals("a@b.com", profile.value?.email)
            assertEquals("Bearer access-1", server.takeRequest().getHeader("Authorization"))
            server.enqueue(MockResponse().setResponseCode(204))
            assertEquals(AuthResult.Success(Unit), reopened.signOut())
            assertNull(store.value)
            assertEquals("/auth/v1/logout?scope=local", server.takeRequest().path)
        }
    }

    @Test fun refreshesExpiredSessionBeforeFetchingUser() = runBlocking {
        MockWebServer().use { server ->
            val store = MemoryStore()
            var now = 0L
            val repo =
                SupabaseAccountRepository(
                    server.url("/").toString(),
                    "public",
                    store,
                    nowSeconds = { now }
                )
            server.enqueue(MockResponse().setBody(SESSION))
            repo.signIn("a@b.com", "password")
            server.takeRequest()
            now = 4000
            server.enqueue(MockResponse().setBody(SESSION.replace("access-1", "access-2")))
            server.enqueue(MockResponse().setBody("""{"id":"user-1","email":"a@b.com"}"""))
            assertTrue(repo.current() is AuthResult.Success)
            val refresh = server.takeRequest()
            assertEquals("/auth/v1/token?grant_type=refresh_token", refresh.path)
            assertTrue(refresh.body.readUtf8().contains("refresh-1"))
            assertEquals("Bearer access-2", server.takeRequest().getHeader("Authorization"))
        }
    }

    @Test fun confirmationRecoveryAndServerFailuresAreTyped() = runBlocking {
        MockWebServer().use { server ->
            val store = MemoryStore()
            val repo = SupabaseAccountRepository(server.url("/").toString(), "public", store)
            server.enqueue(MockResponse().setBody("""{"id":"pending"}"""))
            assertEquals(
                AuthResult.Success(AuthOutcome.CONFIRM_EMAIL),
                repo.signUp("a@b.com", "password")
            )
            assertNull(store.value)
            server.enqueue(MockResponse().setBody("{}"))
            assertEquals(AuthResult.Success(AuthOutcome.RECOVERY_SENT), repo.recover("a@b.com"))
            server.enqueue(
                MockResponse().setResponseCode(
                    400
                ).setBody("""{"error_code":"email_not_confirmed"}""")
            )
            assertEquals(
                AuthResult.Failure(AuthError.UNCONFIRMED),
                repo.signIn("a@b.com", "password")
            )
            server.enqueue(MockResponse().setResponseCode(400).setBody("{}"))
            assertEquals(AuthResult.Failure(AuthError.CREDENTIALS), repo.signIn("a@b.com", "wrong"))
            server.enqueue(MockResponse().setResponseCode(429).setBody("{}"))
            assertEquals(AuthResult.Failure(AuthError.RATE_LIMIT), repo.recover("a@b.com"))
            server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
            assertEquals(AuthResult.Failure(AuthError.SERVER), repo.recover("a@b.com"))
        }
    }

    private class MemoryStore : AuthStore {
        var value: String? = null
        override fun read() = value
        override fun write(value: String) {
            this.value = value
        }
        override fun clear() {
            value = null
        }
    }

    private companion object {
        const val SESSION = """{"access_token":"access-1","refresh_token":"refresh-1",""" +
            """"expires_in":3600}"""
    }
}
