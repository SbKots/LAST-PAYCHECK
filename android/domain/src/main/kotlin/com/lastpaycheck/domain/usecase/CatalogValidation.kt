package com.lastpaycheck.domain.usecase

import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.GameCatalog
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.failure
import com.lastpaycheck.domain.model.flatMap
import com.lastpaycheck.domain.repository.CatalogRepository

internal suspend fun CatalogRepository.validatedCatalog(version: String? = null) =
    get(version).flatMap { it.validate(version) }

internal fun GameCatalog.validate(version: String? = null) =
    if (isValid() && (version == null || version == this.version)) {
        DomainResult.Success(this)
    } else {
        failure(GameError.InvalidCatalog)
    }
