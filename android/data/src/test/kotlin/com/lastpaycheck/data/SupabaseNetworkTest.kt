package com.lastpaycheck.data

import com.lastpaycheck.data.remote.SupabaseNetwork
import com.lastpaycheck.data.remote.dto.SaveArgsDto
import com.lastpaycheck.data.remote.dto.SaveRequestDto
import com.lastpaycheck.data.remote.error.RemoteCalls
import com.lastpaycheck.data.remote.interceptor.TokenProvider
import com.lastpaycheck.domain.model.CatalogManifest
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.RemoteFailure
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SupabaseNetworkTest {
    @get:Rule
    val server = MockWebServer()

    @Test
    fun readsCatalogFromServerWithoutCredentials() = runBlocking {
        val network = network()
        server.enqueue(jsonResponse(Json.encodeToString(manifestFixture())))
        server.enqueue(
            jsonResponse(Json.encodeToString(manifestFixture().copy(catalogVersion = "release-43")))
        )

        val result = network.catalogRepository.getManifest()
            as DomainResult.Success<CatalogManifest>
        assertEquals("release-42", result.value.version)
        assertEquals("release-43", network.catalogRepository.getVersion())

        val request = checkNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        assertEquals("/functions/v1/catalog", request.path)
        assertEquals("GET", request.method)
        assertEquals("application/json", request.getHeader("Accept"))
        assertNull(request.getHeader("Authorization"))
        assertNull(request.getHeader("apikey"))
        assertNull(request.getHeader("Accept-Profile"))
    }

    @Test
    fun mapsHttpStatusesAndBothErrorEnvelopes() = runBlocking {
        val catalog = network().catalogRepository
        val expected = mapOf(
            400 to RemoteFailure.BAD_REQUEST,
            401 to RemoteFailure.AUTH_REQUIRED,
            403 to RemoteFailure.FORBIDDEN,
            404 to RemoteFailure.NOT_FOUND,
            409 to RemoteFailure.CONFLICT,
            412 to RemoteFailure.STALE_REVISION,
            418 to RemoteFailure.HTTP,
            422 to RemoteFailure.BAD_REQUEST,
            429 to RemoteFailure.RATE_LIMITED,
            500 to RemoteFailure.SERVER,
            503 to RemoteFailure.SERVER
        )
        for ((status, reason) in expected) {
            val body = if (status == 400) {
                """{"error":{"code":"BADREQUEST","message":"invalid"}}"""
            } else {
                """{"code":"PGRST123","message":"error","details":null,"hint":null}"""
            }
            server.enqueue(jsonResponse(body).setResponseCode(status))
            val code = if (status == 400) "BADREQUEST" else "PGRST123"
            assertEquals(failure(reason, status, code), catalog.getManifest())
        }
    }

    @Test
    fun rejectsMalformedMissingEmptyAndUnexpectedPayloads() = runBlocking {
        val catalog = network().catalogRepository
        for (body in listOf("{", "", "null", "[]", "{}", "<html>error</html>")) {
            server.enqueue(jsonResponse(body))
            assertEquals(failure(RemoteFailure.INVALID_RESPONSE), catalog.getManifest())
        }
        server.enqueue(MockResponse().setResponseCode(204))
        assertEquals(failure(RemoteFailure.INVALID_RESPONSE), catalog.getManifest())
    }

    @Test
    fun setsProtectedHeadersAndNeverLogsSecretsOrPayloads() = runBlocking {
        val log = mutableListOf<String>()
        val network = network(log = { log.add(it) })
        server.enqueue(
            jsonResponse("""[{"user_id":"owner","snapshot":{},"revision":1,"updated_at":"time"}]""")
        )

        val result = network.calls.execute(network.saveApi::readOwnSave)
        assertTrue(result is DomainResult.Success)
        val request = checkNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        assertEquals("/rest/v1/user_save?select=*", request.path)
        assertEquals("Bearer $TEST_TOKEN", request.getHeader("Authorization"))
        assertEquals(TEST_KEY, request.getHeader("apikey"))
        assertEquals("lastpay_api", request.getHeader("Accept-Profile"))
        assertNull(request.getHeader("Content-Profile"))
        assertEquals(listOf("GET HTTP 200"), log)
        assertFalse(log.any { TEST_TOKEN in it || TEST_KEY in it || "snapshot" in it })
    }

    @Test
    fun rejectsProtectedCallsWithoutUserTokenOrPublicKeyBeforeNetwork() = runBlocking {
        val unauthenticated = network(userToken = null)
        val unconfigured = network(publishableKey = null)
        assertEquals(
            failure(RemoteFailure.AUTH_REQUIRED),
            unauthenticated.calls.execute(unauthenticated.saveApi::readOwnSave)
        )
        assertEquals(
            failure(RemoteFailure.MISSING_CONFIGURATION),
            unconfigured.calls.execute(unconfigured.saveApi::readOwnSave)
        )
        assertEquals(0, server.requestCount)
    }

    @Test
    fun doesNotReplayMutationEvenWhenServerRequestsImmediateRetry() = runBlocking {
        val network = network()
        server.enqueue(jsonResponse("{}").setResponseCode(503).addHeader("Retry-After", "0"))
        server.enqueue(
            jsonResponse("""{"user_id":"owner","snapshot":{},"revision":1,"updated_at":"time"}""")
        )
        val request = SaveRequestDto(SaveArgsDto(JsonObject(emptyMap()), 0))

        assertEquals(
            failure(RemoteFailure.SERVER, 503),
            network.calls.execute {
                network.saveApi.saveGame(request)
            }
        )
        val sent = checkNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        assertEquals(1, server.requestCount)
        assertEquals("POST", sent.method)
        assertEquals("/rest/v1/rpc/save_game", sent.path)
        assertEquals("lastpay_api", sent.getHeader("Content-Profile"))
        assertEquals("lastpay_api", sent.getHeader("Accept-Profile"))
        assertEquals("""{"args":{"snapshot":{},"expectedRevision":0}}""", sent.body.readUtf8())
    }

    @Test
    fun distinguishesTimeoutOfflineDnsTlsAndConnectionFailures() = runBlocking {
        val online = RemoteCalls(Json) { true }
        val offline = RemoteCalls(Json) { false }
        assertEquals(
            failure(RemoteFailure.TIMEOUT),
            online.execute<Unit> {
                throw SocketTimeoutException()
            }
        )
        assertEquals(
            failure(RemoteFailure.DNS),
            online.execute<Unit> { throw UnknownHostException() }
        )
        assertEquals(
            failure(RemoteFailure.TLS),
            online.execute<Unit> {
                throw SSLHandshakeException("test")
            }
        )
        assertEquals(
            failure(RemoteFailure.CONNECTION),
            online.execute<Unit> { throw ConnectException() }
        )
        assertEquals(
            failure(RemoteFailure.OFFLINE),
            offline.execute<Unit> { throw UnknownHostException() }
        )
    }

    @Test
    fun propagatesCancellation() = runBlocking {
        val calls = RemoteCalls(Json) { true }
        val result = runCatching { calls.execute<Unit> { throw CancellationException() } }
        assertTrue(result.exceptionOrNull() is CancellationException)
    }

    private fun network(
        userToken: String? = TEST_TOKEN,
        publishableKey: String? = TEST_KEY,
        log: ((String) -> Unit)? = null
    ) = SupabaseNetwork(
        server.url("/").toString(),
        publishableKey,
        TokenProvider {
            userToken
        },
        { true },
        log
    )

    private fun jsonResponse(body: String) = MockResponse().setHeader(
        "Content-Type",
        "application/json"
    ).setBody(body)

    private fun failure(reason: RemoteFailure, status: Int? = null, code: String? = null) =
        DomainResult.Failure(GameError.Remote(reason, status, code))

    private companion object {
        const val TEST_TOKEN = "testHeader.testPayload.testSignature"
        const val TEST_KEY = "sb_publishable_test"
    }
}
