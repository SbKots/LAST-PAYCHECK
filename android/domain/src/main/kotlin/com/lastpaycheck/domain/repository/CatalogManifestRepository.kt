package com.lastpaycheck.domain.repository

import com.lastpaycheck.domain.catalog.CatalogRepository
import com.lastpaycheck.domain.model.CatalogManifest
import com.lastpaycheck.domain.model.CatalogSnapshot
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.map

interface CatalogManifestRepository : CatalogRepository {
    suspend fun loadCatalog(): DomainResult<CatalogSnapshot>

    suspend fun getManifest(): DomainResult<CatalogManifest> =
        loadCatalog().map(CatalogSnapshot::manifest)
}
