package com.lastpaycheck.data.catalog

import com.lastpaycheck.domain.catalog.CatalogRepository

class DefaultCatalogRepository(
    private val dataSource: CatalogVersionDataSource
) : CatalogRepository {
    override suspend fun getVersion(): String = dataSource.readVersion()
}
