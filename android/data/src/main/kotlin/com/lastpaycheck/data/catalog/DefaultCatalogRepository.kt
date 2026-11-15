package com.lastpaycheck.data.catalog

import android.database.sqlite.SQLiteException
import com.lastpaycheck.data.mapper.toDomain
import com.lastpaycheck.data.remote.CatalogApi
import com.lastpaycheck.data.remote.error.RemoteCalls
import com.lastpaycheck.domain.model.CatalogSnapshot
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.repository.CatalogManifestRepository
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class DefaultCatalogRepository(
    private val api: CatalogApi,
    private val calls: RemoteCalls,
    private val cache: CatalogCache,
    private val now: () -> Instant = Instant::now
) : CatalogManifestRepository {
    private val refresh = Mutex()

    override suspend fun loadCatalog(): DomainResult<CatalogSnapshot> = refresh.withLock {
        val response = calls.execute(api::getManifest)
        if (response is DomainResult.Failure) return@withLock cachedOr(response)

        val dto = (response as DomainResult.Success).value
        when (val mapped = dto.toDomain()) {
            is DomainResult.Failure -> cachedOr(mapped)
            is DomainResult.Success -> {
                val refreshedAt = Instant.ofEpochMilli(now().toEpochMilli())
                try {
                    cache.write(CachedManifest(dto, refreshedAt))
                    DomainResult.Success(CatalogSnapshot(mapped.value, false, refreshedAt))
                } catch (_: SQLiteException) {
                    cachedOr(DomainResult.Failure(GameError.StorageFailure))
                } catch (_: IOException) {
                    cachedOr(DomainResult.Failure(GameError.StorageFailure))
                }
            }
        }
    }

    override suspend fun getVersion(): String = when (val result = getManifest()) {
        is DomainResult.Success -> result.value.version
        is DomainResult.Failure -> throw CatalogReadException(result.error)
    }

    private suspend fun cachedOr(failure: DomainResult.Failure): DomainResult<CatalogSnapshot> {
        val saved = try {
            cache.read()
        } catch (_: SQLiteException) {
            null
        } catch (_: IOException) {
            null
        } ?: return failure

        val mapped = saved.dto.toDomain()
        if (mapped !is DomainResult.Success) return failure
        return DomainResult.Success(CatalogSnapshot(mapped.value, true, saved.lastRefreshedAt))
    }
}

internal class CatalogReadException(val error: GameError) : IOException()
