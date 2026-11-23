package com.lastpaycheck.data.local

import com.lastpaycheck.domain.model.OwnedVehicle
import com.lastpaycheck.domain.model.RaceDefinition
import com.lastpaycheck.domain.model.RaceObstacle
import com.lastpaycheck.domain.model.RaceOutcome
import com.lastpaycheck.domain.model.RacePickup
import com.lastpaycheck.domain.model.RaceResult
import com.lastpaycheck.domain.model.RaceState
import com.lastpaycheck.domain.model.RaceStyle
import com.lastpaycheck.domain.model.VehicleDefinition
import com.lastpaycheck.domain.model.VehicleProgress
import com.lastpaycheck.domain.model.VehicleUpgrade
import kotlinx.serialization.Serializable

@Serializable
internal data class StoredCar(
    val id: String,
    val price: Long,
    val speed: Int,
    val acceleration: Double,
    val handling: Double,
    val tank: Int,
    val consumption: Double,
    val repair: Long,
    val condition: Int,
    val upgrades: Map<VehicleUpgrade, Int>
)

@Serializable
internal data class StoredRaceDefinition(
    val id: String,
    val length: Int,
    val limit: Int,
    val fee: Long,
    val prize: Long,
    val condition: Int,
    val obstacles: List<List<Int>>,
    val pickups: List<StoredPickup> = emptyList(),
    val bonusValue: Long = 0,
    val speedLimit: Int = 500,
    val collisionLimit: Int = 3,
    val requiredWins: Long = 0,
    val style: RaceStyle = RaceStyle.NIGHT,
    val requiredPickups: Int = 0
)

@Serializable
internal data class StoredPickup(val id: String, val distance: Int, val lane: Int)

@Serializable
internal data class StoredRace(
    val id: String,
    val definition: StoredRaceDefinition,
    val car: String,
    val elapsed: Long,
    val distance: Double,
    val speed: Double,
    val lane: Int,
    val braking: Boolean,
    val collisions: Int,
    val lastSteer: Long,
    val previousLane: Int = lane,
    val collected: Set<String> = emptySet(),
    val streak: Int = 0
)

@Serializable
internal data class StoredRaceResult(
    val id: String,
    val event: String,
    val car: String,
    val outcome: RaceOutcome,
    val elapsed: Long,
    val collisions: Int,
    val reward: Long,
    val base: Long = reward,
    val bonus: Long = 0,
    val collected: Int = 0,
    val streak: Int = 0
)

@Serializable
internal data class StoredVehicles(
    val garage: List<StoredCar> = emptyList(),
    val active: String? = null,
    val race: StoredRace? = null,
    val history: List<StoredRaceResult> = emptyList(),
    val completed: Long = 0,
    val wins: Long = 0
)

internal fun VehicleProgress.toStoredVehicles() = StoredVehicles(
    garage.map { car ->
        car.definition.let {
            StoredCar(
                it.id, it.priceMinor, it.maxSpeedKmh, it.acceleration, it.handling,
                it.tankLiters, it.consumption, it.repairPerPointMinor, car.condition, car.upgrades
            )
        }
    },
    activeVehicleId,
    race?.let {
        StoredRace(
            it.id, it.definition.toStoredRace(), it.vehicleId, it.elapsedMillis,
            it.distanceMeters, it.speedMps, it.lane, it.braking, it.collisions, it.lastSteerMillis,
            it.previousLane, it.collectedPickupIds, it.streak
        )
    },
    history.map {
        StoredRaceResult(
            it.raceId,
            it.eventId,
            it.vehicleId,
            it.outcome,
            it.elapsedMillis,
            it.collisions,
            it.rewardMinor,
            it.baseRewardMinor,
            it.bonusRewardMinor,
            it.collected,
            it.finalStreak
        )
    },
    completed,
    wins
)

private fun RaceDefinition.toStoredRace() = StoredRaceDefinition(
    id,
    lengthMeters,
    limitSeconds,
    entryFeeMinor,
    rewardMinor,
    requiredCondition,
    obstacles.map { listOf(it.distanceMeters, it.lane) },
    pickups.map { StoredPickup(it.id, it.distanceMeters, it.lane) },
    bonusValueMinor,
    speedLimitKmh,
    collisionLimit,
    requiredWins,
    style,
    requiredPickups
)

internal fun StoredVehicles.toVehicleProgress() = VehicleProgress(
    garage.map {
        OwnedVehicle(
            VehicleDefinition(
                it.id,
                it.price,
                it.speed,
                it.acceleration,
                it.handling,
                it.tank,
                it.consumption,
                it.repair
            ),
            it.condition,
            it.upgrades
        )
    },
    active,
    race?.let {
        RaceState(
            it.id, it.definition.toRaceDefinition(), it.car, it.elapsed,
            it.distance, it.speed, it.lane, it.braking, it.collisions, it.lastSteer,
            it.previousLane, it.collected, it.streak
        )
    },
    history.map {
        RaceResult(
            it.id, it.event, it.car, it.outcome, it.elapsed, it.collisions, it.reward,
            it.base, it.bonus, it.collected, it.streak
        )
    },
    completed,
    wins
)

private fun StoredRaceDefinition.toRaceDefinition(): RaceDefinition {
    require(obstacles.all { it.size == 2 })
    return RaceDefinition(
        id,
        length,
        limit,
        fee,
        prize,
        condition,
        obstacles.map { RaceObstacle(it[0], it[1]) },
        pickups.map { RacePickup(it.id, it.distance, it.lane) },
        bonusValue,
        speedLimit,
        collisionLimit,
        requiredWins,
        style,
        requiredPickups
    )
}
