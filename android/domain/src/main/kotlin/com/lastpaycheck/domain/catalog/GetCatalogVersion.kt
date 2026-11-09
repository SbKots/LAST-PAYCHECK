package com.lastpaycheck.domain.catalog

class GetCatalogVersion(private val repository: CatalogRepository) {
    suspend operator fun invoke(): String = repository.getVersion()
}
