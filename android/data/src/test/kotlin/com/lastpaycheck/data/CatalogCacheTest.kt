package com.lastpaycheck.data

import com.lastpaycheck.data.catalog.CachedManifest
import com.lastpaycheck.data.catalog.CatalogCache
import com.lastpaycheck.data.catalog.DefaultCatalogRepository
import com.lastpaycheck.data.remote.CatalogApi
import com.lastpaycheck.data.remote.dto.CatalogManifestDto
import com.lastpaycheck.data.remote.error.RemoteCalls
import com.lastpaycheck.domain.model.CatalogSnapshot
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.RemoteFailure
import java.net.ConnectException
import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class CatalogCacheTest {
    @Test
    fun retainsValidCacheAcrossFailuresAndRefreshesAfterRecovery() = runBlocking {
        val api = StubApi()
        val cache = FakeCache()
        var now = Instant.ofEpochMilli(123)
        val repository =
            DefaultCatalogRepository(api, RemoteCalls(Json) { api.online }, cache) { now }
        val offline = DomainResult.Failure(GameError.Remote(RemoteFailure.OFFLINE))

        assertEquals(offline, repository.loadCatalog())
        assertNull(cache.value)
        api.online = true
        val fresh = repository.loadCatalog().snapshot()
        assertFalse(fresh.fromCache)
        assertEquals(now, fresh.lastRefreshedAt)
        assertEquals(fresh.manifest.version, cache.value?.dto?.catalogVersion)

        api.online = false
        assertEquals(fresh.copy(fromCache = true), repository.loadCatalog().snapshot())

        api.online = true
        api.response = Response.error(503, "{}".toResponseBody())
        assertEquals(fresh.copy(fromCache = true), repository.loadCatalog().snapshot())

        api.response = Response.success(manifestFixture().copy(contentSha256 = "invalid"))
        assertEquals(fresh.copy(fromCache = true), repository.loadCatalog().snapshot())
        assertEquals(1, cache.writes)

        now = Instant.ofEpochMilli(456)
        api.response = Response.success(manifestFixture().copy(catalogVersion = "release-43"))
        val refreshed = repository.loadCatalog().snapshot()
        assertFalse(refreshed.fromCache)
        assertEquals("release-43", refreshed.manifest.version)
        assertEquals(now, refreshed.lastRefreshedAt)
        assertEquals(2, cache.writes)
    }

    @Test
    fun fallsBackWithoutOverwritingCacheWhenRetrofitCannotDecodeJson() = runBlocking {
        okhttp3.mockwebserver.MockWebServer().use { server ->
            val cache = FakeCache()
            val network = com.lastpaycheck.data.remote.SupabaseNetwork(
                server.url("/").toString(),
                null,
                { null },
                { true }
            )
            val repo = network.catalogRepository(cache)
            server.enqueue(
                okhttp3.mockwebserver.MockResponse().setBody(Json.encodeToString(manifestFixture()))
            )
            val fresh = repo.loadCatalog().snapshot()
            server.enqueue(okhttp3.mockwebserver.MockResponse().setBody("{"))
            assertTrue(repo.loadCatalog().snapshot().fromCache)
            assertEquals(fresh.manifest.version, cache.value?.dto?.catalogVersion)
            assertEquals(1, cache.writes)
        }
    }

    private fun DomainResult<CatalogSnapshot>.snapshot() = (this as DomainResult.Success).value

    private class StubApi : CatalogApi {
        var online = false
        var response: Response<CatalogManifestDto> = Response.success(manifestFixture())

        override suspend fun getManifest(): Response<CatalogManifestDto> {
            if (!online) throw ConnectException()
            return response
        }
    }

    private class FakeCache : CatalogCache {
        var value: CachedManifest? = null
        var writes = 0

        override suspend fun read() = value

        override suspend fun write(manifest: CachedManifest) {
            value = manifest
            writes++
        }
    }
}
