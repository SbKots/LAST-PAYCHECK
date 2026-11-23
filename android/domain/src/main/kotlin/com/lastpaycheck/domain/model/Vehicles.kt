package com.lastpaycheck.domain.model

enum class VehicleUpgrade { ENGINE, HANDLING, BRAKES, RELIABILITY }
enum class VehicleFailure {
    ALREADY_OWNED,
    NOT_OWNED,
    NO_ACTIVE_CAR,
    MAX_UPGRADE,
    DAMAGED,
    NO_RACE,
    STEERING_WAIT,
    RACE_LOCKED
}
enum class RaceOutcome { WON, CRASHED, TIMEOUT, ABANDONED, HEALTH, TOKENS_MISSED }

data class VehicleDefinition(
    val id: String,
    val priceMinor: Long,
    val maxSpeedKmh: Int,
    val acceleration: Double,
    val handling: Double,
    val tankLiters: Int,
    val consumption: Double,
    val repairPerPointMinor: Long
) {
    fun isValid() = id.isNotBlank() && priceMinor in 1..1_000_000_000 &&
        maxSpeedKmh in 1..500 && acceleration in 0.1..50.0 && handling in 0.01..1.0 &&
        tankLiters in 1..300 && consumption in 0.0..100.0 && repairPerPointMinor in 0..100_000
}

data class OwnedVehicle(
    val definition: VehicleDefinition,
    val condition: Int = 100,
    val upgrades: Map<VehicleUpgrade, Int> = emptyMap()
) {
    fun level(part: VehicleUpgrade) = upgrades[part] ?: 0
    val acceleration get() = definition.acceleration * (1 + level(VehicleUpgrade.ENGINE) * 0.15)
    val maxSpeedKmh get() = definition.maxSpeedKmh * (1 + level(VehicleUpgrade.ENGINE) * 0.05) *
        (0.5 + condition / 200.0)
    val steeringCooldownMillis get() = (
        450 - definition.handling * 200 -
            level(VehicleUpgrade.HANDLING) * 50
        ).toLong().coerceAtLeast(180)
    val braking get() = 6.0 + level(VehicleUpgrade.BRAKES) * 2
    fun upgradePrice(part: VehicleUpgrade) =
        (definition.priceMinor * (level(part) + 1) / 10).coerceAtLeast(1)
    val repairPrice get() = definition.repairPerPointMinor * (100 - condition)
    fun isValid() =
        definition.isValid() && condition in 0..100 && upgrades.values.all { it in 0..3 }
}

data class RaceObstacle(val distanceMeters: Int, val lane: Int)
data class RacePickup(val id: String, val distanceMeters: Int, val lane: Int)
enum class RaceStyle { NIGHT, CITY, INDUSTRIAL }

data class RaceDefinition(
    val id: String,
    val lengthMeters: Int,
    val limitSeconds: Int,
    val entryFeeMinor: Long,
    val rewardMinor: Long,
    val requiredCondition: Int,
    val obstacles: List<RaceObstacle>,
    val pickups: List<RacePickup> = emptyList(),
    val bonusValueMinor: Long = 0,
    val speedLimitKmh: Int = 500,
    val collisionLimit: Int = 3,
    val requiredWins: Long = 0,
    val style: RaceStyle = RaceStyle.NIGHT,
    val requiredPickups: Int = 0
) {
    fun isValid() = id.isNotBlank() && lengthMeters in 100..100_000 && limitSeconds in 10..600 &&
        entryFeeMinor in 0..1_000_000 && rewardMinor in 0..1_000_000 &&
        requiredCondition in 1..100 &&
        obstacles.all { it.distanceMeters in 1 until lengthMeters && it.lane in 0..2 } &&
        obstacles.zipWithNext().all { (a, b) -> a.distanceMeters < b.distanceMeters } &&
        bonusValueMinor in 0..10_000 && speedLimitKmh in 30..500 &&
        collisionLimit in 1..10 && requiredWins in 0..100 && pickups.size <= 1000 &&
        pickups.all {
            it.id.isNotBlank() && it.distanceMeters in 1 until lengthMeters &&
                it.lane in 0..2
        } &&
        pickups.map { it.id }.distinct().size == pickups.size &&
        pickups.zipWithNext().all { (a, b) -> a.distanceMeters < b.distanceMeters } &&
        pickups.none { pickup -> obstacles.any { it.distanceMeters == pickup.distanceMeters } } &&
        requiredPickups in 0..pickups.size
}

data class VehicleCatalog(
    val version: String,
    val vehicles: List<VehicleDefinition>,
    val races: List<RaceDefinition>
) {
    fun isValid() = version.isNotBlank() && vehicles.isNotEmpty() && races.isNotEmpty() &&
        vehicles.all { it.isValid() } && races.all { it.isValid() } &&
        vehicles.map { it.id }.distinct().size == vehicles.size &&
        races.map { it.id }.distinct().size == races.size
}

data class RaceState(
    val id: String,
    val definition: RaceDefinition,
    val vehicleId: String,
    val elapsedMillis: Long = 0,
    val distanceMeters: Double = 0.0,
    val speedMps: Double = 0.0,
    val lane: Int = 1,
    val braking: Boolean = false,
    val collisions: Int = 0,
    val lastSteerMillis: Long = -1000,
    val previousLane: Int = lane,
    val collectedPickupIds: Set<String> = emptySet(),
    val streak: Int = 0
) {
    val multiplierPercent get() = when (streak) {
        0 -> 100
        in 1..2 -> 125
        in 3..4 -> 150
        in 5..6 -> 200
        in 7..9 -> 250
        else -> 300
    }
    val pendingBonusMinor get() = streak * definition.bonusValueMinor * multiplierPercent / 100
    val collisionLane get() = if (elapsedMillis - lastSteerMillis < 90) previousLane else lane
    fun isValid() = id.isNotBlank() && definition.isValid() && vehicleId.isNotBlank() &&
        elapsedMillis in 0..definition.limitSeconds * 1000L &&
        distanceMeters in 0.0..definition.lengthMeters.toDouble() && speedMps in 0.0..200.0 &&
        lane in 0..2 && previousLane in 0..2 && collisions in 0..definition.collisionLimit &&
        lastSteerMillis in -1000..elapsedMillis && streak in 0..collectedPickupIds.size &&
        collectedPickupIds.all { id ->
            definition.pickups.any {
                it.id == id &&
                    it.distanceMeters <= distanceMeters
            }
        }
}

data class RaceResult(
    val raceId: String,
    val eventId: String,
    val vehicleId: String,
    val outcome: RaceOutcome,
    val elapsedMillis: Long,
    val collisions: Int,
    val rewardMinor: Long,
    val baseRewardMinor: Long = rewardMinor,
    val bonusRewardMinor: Long = 0,
    val collected: Int = 0,
    val finalStreak: Int = 0
)

data class VehicleProgress(
    val garage: List<OwnedVehicle> = emptyList(),
    val activeVehicleId: String? = null,
    val race: RaceState? = null,
    val history: List<RaceResult> = emptyList(),
    val completed: Long = 0,
    val wins: Long = 0
) {
    val activeVehicle get() = garage.find { it.definition.id == activeVehicleId }
    fun isValid() =
        garage.all { it.isValid() } && garage.map { it.definition.id }.distinct().size ==
            garage.size && (activeVehicleId == null || activeVehicle != null) &&
            (race == null || (race.isValid() && race.vehicleId == activeVehicleId)) &&
            history.size <= 100 && history.map { it.raceId }.distinct().size == history.size &&
            history.all {
                it.raceId.isNotBlank() && it.eventId.isNotBlank() &&
                    garage.any { car -> car.definition.id == it.vehicleId } &&
                    it.elapsedMillis in 0..600_000 && it.collisions in 0..10 &&
                    it.rewardMinor in 0..31_000_000 && it.baseRewardMinor >= 0 &&
                    it.bonusRewardMinor >= 0 &&
                    it.rewardMinor == it.baseRewardMinor + it.bonusRewardMinor &&
                    it.collected in 0..1000 && it.finalStreak in 0..it.collected &&
                    (it.outcome == RaceOutcome.WON || it.rewardMinor == 0L)
            } && completed >= history.size && wins in 0..completed
}

sealed interface VehicleAction {
    data class Buy(val modelId: String) : VehicleAction
    data class Select(val modelId: String) : VehicleAction
    data class Upgrade(val modelId: String, val part: VehicleUpgrade) : VehicleAction
    data class Repair(val modelId: String) : VehicleAction
    data class StartRace(val eventId: String) : VehicleAction
    data class Drive(val raceId: String, val laneDelta: Int, val braking: Boolean) : VehicleAction
    data class Abandon(val raceId: String) : VehicleAction
}
