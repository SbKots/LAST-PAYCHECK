package com.lastpaycheck.domain.catalog

interface CatalogRepository {
    suspend fun getVersion(): String
}
