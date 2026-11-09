package com.lastpaycheck.data.catalog

object DemoCatalogVersionDataSource : CatalogVersionDataSource {
    override suspend fun readVersion(): String = "demo-1"
}
