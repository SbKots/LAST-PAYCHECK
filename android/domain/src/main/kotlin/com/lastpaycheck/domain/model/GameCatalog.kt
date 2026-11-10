package com.lastpaycheck.domain.model

import java.math.BigDecimal

data class Job(
    val id: String,
    val title: String,
    val requiredLevel: Int,
    val durationHours: Int,
    val basePayMinor: Long,
    val bonusCapMinor: Long,
    val plan: Int,
    val extraEnergyPerHour: Int
) {
    internal fun isValid() = id.isNotBlank() && title.isNotBlank() && requiredLevel >= 0 &&
        durationHours > 0 && basePayMinor >= 0 && bonusCapMinor >= 0 &&
        plan > 0 && extraEnergyPerHour >= 0
}

data class Product(
    val id: String,
    val basePriceMinor: Long,
    val satietyGain: Int,
    val durationMinutes: Int
) {
    internal fun isValid() = id.isNotBlank() && basePriceMinor >= 0 &&
        satietyGain in 1..100 && durationMinutes > 0
}

data class Course(
    val id: String,
    val priceMinor: Long,
    val durationHours: Int,
    val experienceGain: Int
) {
    internal fun isValid() = id.isNotBlank() && priceMinor >= 0 &&
        durationHours > 0 && experienceGain > 0
}

enum class CatalogSource { PUBLISHED, TRAINING }

data class EconomicScenario(
    val id: String,
    val version: String,
    val country: String,
    val year: Int,
    val annualInflationPercent: BigDecimal,
    val source: CatalogSource
) {
    internal fun isValid() = id.isNotBlank() && version.isNotBlank() &&
        country.length == 3 && country.all { it in 'A'..'Z' } && year > 0 &&
        annualInflationPercent > BigDecimal.valueOf(-100) &&
        annualInflationPercent.toDouble().isFinite()
}

data class InitialConditions(
    val moneyMinor: Long,
    val needs: Needs,
    val jobId: String,
    val dailyRentMinor: Long
)

data class GameCatalog(
    val version: String,
    val source: CatalogSource,
    val initial: InitialConditions,
    val jobs: List<Job>,
    val products: List<Product>,
    val courses: List<Course>,
    val scenarios: List<EconomicScenario>
) {
    fun isValid(): Boolean =
        version.isNotBlank() && initial.moneyMinor >= 0 && initial.dailyRentMinor >= 0 &&
            initial.needs.isValid() && initial.needs.healthUnits > 0 &&
            jobs.size >= 3 && jobs.any { it.id == initial.jobId && it.requiredLevel == 0 } &&
            jobs.validEntries(Job::id, Job::isValid) &&
            products.validEntries(Product::id, Product::isValid) &&
            courses.validEntries(Course::id, Course::isValid) &&
            scenarios.validEntries(EconomicScenario::id) { it.isValid() && it.version == version }

    private fun <T> List<T>.validEntries(id: (T) -> String, valid: (T) -> Boolean) =
        isNotEmpty() && all(valid) && map(id).toSet().size == size
}
