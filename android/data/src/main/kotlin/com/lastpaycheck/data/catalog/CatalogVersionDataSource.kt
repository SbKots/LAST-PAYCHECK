package com.lastpaycheck.data.catalog

/** Reads the version from a local source or a future catalog manifest adapter. */
interface CatalogVersionDataSource {
    suspend fun readVersion(): String
}
