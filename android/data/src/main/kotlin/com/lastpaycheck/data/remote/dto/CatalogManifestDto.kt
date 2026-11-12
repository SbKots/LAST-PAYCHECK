package com.lastpaycheck.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
internal data class CatalogManifestDto(
    val catalogVersion: String,
    val schemaVersion: String,
    val publishedAt: String,
    val contentSha256: String,
    val resources: List<String>,
    val sources: List<SourceMetadataDto>
)

@Serializable
internal data class SourceMetadataDto(
    val resource: String,
    val provider: String,
    val fetchedAt: String,
    val sourceVersion: String?,
    val sha256: String
)
