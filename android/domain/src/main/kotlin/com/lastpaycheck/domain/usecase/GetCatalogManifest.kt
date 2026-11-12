package com.lastpaycheck.domain.usecase

import com.lastpaycheck.domain.repository.CatalogManifestRepository

class GetCatalogManifest(private val repository: CatalogManifestRepository) {
    suspend operator fun invoke() = repository.getManifest()
}
