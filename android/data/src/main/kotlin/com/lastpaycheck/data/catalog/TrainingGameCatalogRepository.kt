package com.lastpaycheck.data.catalog

import com.lastpaycheck.domain.model.CatalogSource
import com.lastpaycheck.domain.model.Course
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.EconomicScenario
import com.lastpaycheck.domain.model.GameCatalog
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.InitialConditions
import com.lastpaycheck.domain.model.Job
import com.lastpaycheck.domain.model.Needs
import com.lastpaycheck.domain.model.Product
import com.lastpaycheck.domain.repository.CatalogRepository
import java.math.BigDecimal

class TrainingGameCatalogRepository : CatalogRepository {
    override suspend fun getVersion(): String = catalog.version

    override suspend fun get(version: String?): DomainResult<GameCatalog> =
        if (version == null || version == catalog.version) {
            DomainResult.Success(catalog)
        } else {
            DomainResult.Failure(GameError.CatalogUnavailable)
        }

    override suspend fun refresh(): DomainResult<GameCatalog> = get()

    private companion object {
        const val VERSION = "training-1"

        val catalog = GameCatalog(
            version = VERSION,
            source = CatalogSource.TRAINING,
            initial = InitialConditions(
                moneyMinor = 100_000,
                needs = Needs.of(100, 100, 100, 100),
                jobId = "warehouse-helper",
                dailyRentMinor = 10_000
            ),
            jobs = listOf(
                Job("warehouse-helper", "Помощник склада", 0, 10, 400, 100, 40, 2),
                Job("warehouse-picker", "Комплектовщик", 1, 10, 500, 120, 45, 3),
                Job("warehouse-senior", "Старший сортировщик", 2, 10, 600, 150, 50, 4)
            ),
            products = listOf(Product("meal", 5_000, 30, 10)),
            courses = listOf(Course("logistics", 25_000, 2, 100)),
            scenarios = listOf(
                EconomicScenario(
                    id = "training-rus",
                    version = VERSION,
                    country = "RUS",
                    year = 2026,
                    annualInflationPercent = BigDecimal.valueOf(5),
                    source = CatalogSource.TRAINING
                )
            )
        )
    }
}
