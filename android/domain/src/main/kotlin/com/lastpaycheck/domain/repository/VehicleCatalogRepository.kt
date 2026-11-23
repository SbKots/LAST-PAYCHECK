package com.lastpaycheck.domain.repository

import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.VehicleCatalog

interface VehicleCatalogRepository {
    suspend fun get(): DomainResult<VehicleCatalog>
}
