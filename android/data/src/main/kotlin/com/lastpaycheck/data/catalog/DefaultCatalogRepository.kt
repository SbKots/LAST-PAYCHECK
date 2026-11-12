package com.lastpaycheck.data.catalog

import com.lastpaycheck.data.mapper.toDomain
import com.lastpaycheck.data.remote.CatalogApi
import com.lastpaycheck.data.remote.error.RemoteCalls
import com.lastpaycheck.domain.model.CatalogManifest
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.repository.CatalogManifestRepository
import java.io.IOException

internal class DefaultCatalogRepository(
    private val api: CatalogApi,
    private val calls: RemoteCalls
) : CatalogManifestRepository {
    override suspend fun getManifest(): DomainResult<CatalogManifest> =
        when (val response = calls.execute(api::getManifest)) {
            is DomainResult.Success -> response.value.toDomain()
            is DomainResult.Failure -> response
        }

    override suspend fun getVersion(): String = when (val result = getManifest()) {
        is DomainResult.Success -> result.value.version
        is DomainResult.Failure -> throw CatalogReadException(result.error)
    }
}

internal class CatalogReadException(val error: GameError) : IOException()
