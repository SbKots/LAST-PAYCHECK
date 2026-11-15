package com.lastpaycheck.data.local

import com.lastpaycheck.data.catalog.CachedManifest
import com.lastpaycheck.data.catalog.CatalogCache
import com.lastpaycheck.data.remote.dto.CatalogManifestDto
import java.time.Instant
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

internal class RoomCatalogCache(private val dao: CatalogDao) : CatalogCache {
    override suspend fun read(): CachedManifest? {
        val row = dao.read() ?: return null
        return try {
            CachedManifest(
                Json.decodeFromString<CatalogManifestDto>(row.payload),
                row.refreshedAtMillis?.let(Instant::ofEpochMilli)
            )
        } catch (_: SerializationException) {
            null
        }
    }

    override suspend fun write(manifest: CachedManifest) {
        dao.write(
            CatalogRow(
                slot = 0,
                payload = Json.encodeToString(manifest.dto),
                refreshedAtMillis = manifest.lastRefreshedAt?.toEpochMilli()
            )
        )
    }
}
