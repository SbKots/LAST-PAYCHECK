package com.lastpaycheck.domain.repository

import com.lastpaycheck.domain.catalog.CatalogRepository as CatalogVersionRepository
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.GameCatalog

interface CatalogRepository : CatalogVersionRepository {
    suspend fun get(version: String? = null): DomainResult<GameCatalog>

    suspend fun refresh(): DomainResult<GameCatalog>
}
