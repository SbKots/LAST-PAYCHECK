package com.lastpaycheck.domain.usecase

import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.NewGamePreview
import com.lastpaycheck.domain.model.failure
import com.lastpaycheck.domain.model.flatMap
import com.lastpaycheck.domain.repository.CatalogRepository

class PreviewNewGameUseCase(private val catalog: CatalogRepository) {
    suspend operator fun invoke(
        scenarioId: String,
        catalogVersion: String? = null
    ): DomainResult<NewGamePreview> {
        if (scenarioId.isBlank()) return failure(GameError.InvalidInput("scenarioId"))
        if (catalogVersion?.isBlank() == true) {
            return failure(GameError.InvalidInput("catalogVersion"))
        }
        return catalog.validatedCatalog(catalogVersion).flatMap { current ->
            val scenario = current.scenarios.find { it.id == scenarioId }
                ?: return@flatMap failure(GameError.NotFound(scenarioId))
            DomainResult.Success(NewGamePreview(current.initial, scenario))
        }
    }
}
