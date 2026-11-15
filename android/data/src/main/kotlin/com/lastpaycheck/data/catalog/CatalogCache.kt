package com.lastpaycheck.data.catalog

import com.lastpaycheck.data.remote.dto.CatalogManifestDto
import java.time.Instant

internal data class CachedManifest(val dto: CatalogManifestDto, val lastRefreshedAt: Instant?)

internal interface CatalogCache {
    suspend fun read(): CachedManifest?
    suspend fun write(manifest: CachedManifest)
}
