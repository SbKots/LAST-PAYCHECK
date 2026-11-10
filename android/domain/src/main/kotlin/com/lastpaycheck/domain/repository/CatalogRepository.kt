package com.lastpaycheck.domain.repository

import com.lastpaycheck.domain.catalog.CatalogRepository as CatalogVersionRepository
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.GameCatalog

interface CatalogRepository : CatalogVersionRepository {
    /** Reads the pinned version, or the latest valid local catalog when version is null. */
    suspend fun get(version: String? = null): DomainResult<GameCatalog>

    /** Validates before publishing locally. Failed updates leave the prior version available. */
    suspend fun refresh(): DomainResult<GameCatalog>
}
