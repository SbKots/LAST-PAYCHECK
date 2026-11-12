package com.lastpaycheck.domain.model

import java.time.Instant

data class CatalogManifest(
    val version: String,
    val publishedAt: Instant,
    val contentSha256: String,
    val sources: List<CatalogResourceSource>
)

data class CatalogResourceSource(
    val resource: String,
    val provider: String,
    val fetchedAt: Instant,
    val sourceVersion: String?,
    val sha256: String
)

enum class RemoteFailure {
    BAD_REQUEST,
    AUTH_REQUIRED,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
    STALE_REVISION,
    RATE_LIMITED,
    SERVER,
    HTTP,
    TIMEOUT,
    OFFLINE,
    DNS,
    TLS,
    CONNECTION,
    INVALID_RESPONSE,
    MISSING_CONFIGURATION
}
