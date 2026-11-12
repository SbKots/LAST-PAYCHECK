package com.lastpaycheck.data.mapper

import com.lastpaycheck.data.remote.dto.CatalogManifestDto
import com.lastpaycheck.domain.model.CatalogManifest
import com.lastpaycheck.domain.model.CatalogResourceSource
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.RemoteFailure
import java.time.Instant
import java.time.format.DateTimeParseException

private val versionPattern = Regex("[a-z0-9][a-z0-9._-]{0,31}")
private val hashPattern = Regex("[a-f0-9]{64}")
private val expectedResources = setOf(
    "occupations",
    "skills",
    "jobs",
    "inflation",
    "car-brands",
    "car-models",
    "tracks",
    "race-configurations"
)
private val providers = setOf("DEMO", "ONET", "WORLD_BANK", "CARS_BASE")

internal fun CatalogManifestDto.toDomain(): DomainResult<CatalogManifest> {
    val invalid = DomainResult.Failure(GameError.Remote(RemoteFailure.INVALID_RESPONSE))
    if (schemaVersion != "1" || !versionPattern.matches(catalogVersion) ||
        !hashPattern.matches(contentSha256) ||
        resources.size != expectedResources.size || resources.toSet() != expectedResources ||
        sources.size != expectedResources.size ||
        sources.map { it.resource }.toSet() != expectedResources
    ) {
        return invalid
    }

    return try {
        val mappedSources = sources.map { source ->
            if (source.provider !in providers || !hashPattern.matches(source.sha256) ||
                (source.sourceVersion?.codePointCount(0, source.sourceVersion.length) ?: 0) > 120
            ) {
                return invalid
            }

            CatalogResourceSource(
                resource = source.resource,
                provider = source.provider,
                fetchedAt = Instant.parse(source.fetchedAt),
                sourceVersion = source.sourceVersion,
                sha256 = source.sha256
            )
        }
        DomainResult.Success(
            CatalogManifest(
                catalogVersion,
                Instant.parse(publishedAt),
                contentSha256,
                mappedSources
            )
        )
    } catch (_: DateTimeParseException) {
        invalid
    }
}
