package com.lastpaycheck.data.auth

import android.content.Context
import com.lastpaycheck.domain.model.Account
import com.lastpaycheck.domain.model.AuthError
import com.lastpaycheck.domain.model.AuthOutcome
import com.lastpaycheck.domain.model.AuthResult
import com.lastpaycheck.domain.repository.AccountRepository
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class SupabaseAccountRepository internal constructor(
    baseUrl: String,
    private val publicKey: String,
    private val store: AuthStore,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(25, TimeUnit.SECONDS).followRedirects(false).build(),
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000 }
) : AccountRepository {
    constructor(context: Context, baseUrl: String, publicKey: String) : this(
        baseUrl,
        publicKey,
        ProtectedAuthStore(context.applicationContext)
    )

    private val base = baseUrl.toHttpUrl()
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun current(): AuthResult<Account?> = guarded {
        val saved = store.read()?.let { json.decodeFromString<StoredAuthSession>(it) }
            ?: return@guarded AuthResult.Success(null)
        val session = if (saved.expiresAt <= nowSeconds() + 60) {
            when (
                val refreshed = request(
                    "token?grant_type=refresh_token",
                    buildJsonObject {
                        put("refresh_token", saved.refreshToken)
                    }
                )
            ) {
                is HttpResult.Ok -> saveSession(refreshed.body)
                is HttpResult.Error -> return@guarded AuthResult.Failure(refreshed.reason)
            }
        } else {
            saved
        }
        when (val result = request("user", token = session.accessToken, get = true)) {
            is HttpResult.Ok -> {
                val user = json.decodeFromString<RemoteUser>(result.body.toString())
                AuthResult.Success(Account(user.id, user.email))
            }
            is HttpResult.Error -> AuthResult.Failure(result.reason)
        }
    }

    override suspend fun signIn(email: String, password: String) = authenticate(
        "token?grant_type=password",
        email,
        password
    )

    override suspend fun signUp(email: String, password: String) = authenticate(
        "signup",
        email,
        password
    )

    private suspend fun authenticate(path: String, email: String, password: String) = guarded {
        when (
            val result = request(
                path,
                buildJsonObject {
                    put("email", email)
                    put("password", password)
                }
            )
        ) {
            is HttpResult.Ok -> {
                if (!result.body["access_token"]?.jsonPrimitive?.contentOrNull.isNullOrBlank()) {
                    saveSession(result.body)
                    AuthResult.Success(AuthOutcome.SIGNED_IN)
                } else if (path == "signup") {
                    AuthResult.Success(AuthOutcome.CONFIRM_EMAIL)
                } else {
                    AuthResult.Failure(AuthError.SERVER)
                }
            }
            is HttpResult.Error -> AuthResult.Failure(result.reason)
        }
    }

    override suspend fun recover(email: String) = guarded {
        when (val result = request("recover", buildJsonObject { put("email", email) })) {
            is HttpResult.Ok -> AuthResult.Success(AuthOutcome.RECOVERY_SENT)
            is HttpResult.Error -> AuthResult.Failure(result.reason)
        }
    }

    override suspend fun signOut(): AuthResult<Unit> = guarded {
        val saved = runCatching {
            store.read()?.let { json.decodeFromString<StoredAuthSession>(it) }
        }.getOrNull()
        store.clear()
        if (saved != null) {
            try {
                request("logout?scope=local", token = saved.accessToken)
            } catch (_: IOException) {
                Unit
            }
        }
        AuthResult.Success(Unit)
    }

    private fun saveSession(body: JsonObject): StoredAuthSession {
        val remote = json.decodeFromString<RemoteSession>(body.toString())
        require(remote.access_token.isNotBlank() && remote.refresh_token.isNotBlank())
        val session = StoredAuthSession(
            remote.access_token,
            remote.refresh_token,
            nowSeconds() + remote.expires_in
        )
        store.write(json.encodeToString(session))
        return session
    }

    private fun request(
        path: String,
        body: JsonObject = buildJsonObject {},
        token: String? = null,
        get: Boolean = false
    ): HttpResult {
        if (publicKey.isBlank()) return HttpResult.Error(AuthError.CONFIGURATION)
        val url = checkNotNull(base.resolve("auth/v1/$path"))
        val request = Request.Builder().url(url).header("apikey", publicKey).apply {
            token?.let { header("Authorization", "Bearer $it") }
            if (get) {
                get()
            } else {
                post(
                    body.toString().toRequestBody("application/json".toMediaType())
                )
            }
        }.build()
        return client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val objectBody = runCatching { json.parseToJsonElement(raw).jsonObject }
                .getOrDefault(buildJsonObject {})
            if (response.isSuccessful) return@use HttpResult.Ok(objectBody)
            val code = objectBody["error_code"]?.jsonPrimitive?.content
            val reason = when {
                response.code == 429 -> AuthError.RATE_LIMIT
                code == "email_not_confirmed" -> AuthError.UNCONFIRMED
                response.code in 400..403 || response.code == 422 -> AuthError.CREDENTIALS
                else -> AuthError.SERVER
            }
            HttpResult.Error(reason)
        }
    }

    private suspend fun <T> guarded(block: () -> AuthResult<T>): AuthResult<T> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    block()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: IOException) {
                    AuthResult.Failure(AuthError.NETWORK)
                } catch (_: Exception) {
                    AuthResult.Failure(AuthError.STORAGE)
                }
            }
        }
}

private sealed interface HttpResult {
    data class Ok(val body: JsonObject) : HttpResult
    data class Error(val reason: AuthError) : HttpResult
}

@Serializable
private data class StoredAuthSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long
)

@Serializable
private data class RemoteUser(val id: String, val email: String = "")

@Serializable
private data class RemoteSession(
    val access_token: String,
    val refresh_token: String,
    val expires_in: Long
)
