package com.lastpaycheck.data

import com.lastpaycheck.data.mapper.toDomain
import com.lastpaycheck.data.remote.dto.CatalogManifestDto
import com.lastpaycheck.data.remote.dto.SourceMetadataDto
import com.lastpaycheck.domain.model.CatalogManifest
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.RemoteFailure
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogManifestMapperTest {
    @Test
    fun preservesVersionTimeHashAndSources() {
        val dto = manifestFixture()
        val result = dto.toDomain() as DomainResult.Success<CatalogManifest>
        val catalog = result.value
        assertEquals("release-42", catalog.version)
        assertEquals(Instant.parse(dto.publishedAt), catalog.publishedAt)
        assertEquals(dto.contentSha256, catalog.contentSha256)
        assertEquals(dto.resources, catalog.sources.map { it.resource })
        assertEquals("2025", catalog.sources.first().sourceVersion)
    }

    @Test
    fun preservesNullableSourceVersion() {
        val result = manifestFixture().toDomain() as DomainResult.Success<CatalogManifest>
        assertNull(result.value.sources.last().sourceVersion)
    }

    @Test
    fun rejectsInvalidManifestFields() {
        val dto = manifestFixture()
        val invalid = listOf(
            dto.copy(catalogVersion = ""),
            dto.copy(catalogVersion = "release/42"),
            dto.copy(schemaVersion = "2"),
            dto.copy(publishedAt = "yesterday"),
            dto.copy(contentSha256 = "wrong"),
            dto.copy(resources = dto.resources.dropLast(1)),
            dto.copy(sources = dto.sources.dropLast(1) + dto.sources.first()),
            dto.copy(
                sources = listOf(dto.sources.first().copy(provider = "OTHER")) + dto.sources.drop(1)
            ),
            dto.copy(
                sources = listOf(dto.sources.first().copy(fetchedAt = "")) + dto.sources.drop(1)
            ),
            dto.copy(sources = listOf(dto.sources.first().copy(sha256 = "")) + dto.sources.drop(1))
        )
        invalid.forEach {
            assertEquals(
                DomainResult.Failure(GameError.Remote(RemoteFailure.INVALID_RESPONSE)),
                it.toDomain()
            )
        }
    }
}

internal fun manifestFixture(): CatalogManifestDto {
    val resources = listOf(
        "occupations",
        "skills",
        "jobs",
        "inflation",
        "car-brands",
        "car-models",
        "tracks",
        "race-configurations"
    )
    val time = "2026-11-10T12:00:00.123456Z"
    return CatalogManifestDto(
        catalogVersion = "release-42",
        schemaVersion = "1",
        publishedAt = time,
        contentSha256 = "a".repeat(64),
        resources = resources,
        sources = resources.mapIndexed { index, resource ->
            SourceMetadataDto(
                resource,
                "DEMO",
                time,
                if (index == 0) "2025" else null,
                "b".repeat(64)
            )
        }
    )
}
