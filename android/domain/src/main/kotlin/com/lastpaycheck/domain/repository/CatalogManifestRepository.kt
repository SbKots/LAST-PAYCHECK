package com.lastpaycheck.domain.repository

import com.lastpaycheck.domain.catalog.CatalogRepository
import com.lastpaycheck.domain.model.CatalogManifest
import com.lastpaycheck.domain.model.DomainResult

interface CatalogManifestRepository : CatalogRepository {
    suspend fun getManifest(): DomainResult<CatalogManifest>
}
