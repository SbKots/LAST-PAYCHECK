package com.lastpaycheck.domain.usecase

import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.GameCatalog
import com.lastpaycheck.domain.model.flatMap
import com.lastpaycheck.domain.repository.CatalogRepository

class RefreshCatalogUseCase(private val catalog: CatalogRepository) {
    suspend operator fun invoke(): DomainResult<GameCatalog> =
        catalog.refresh().flatMap { it.validate() }
}
