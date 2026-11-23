package com.lastpaycheck.domain.engine

import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.GameSession
import com.lastpaycheck.domain.model.GameTransition
import com.lastpaycheck.domain.model.OperationKey
import com.lastpaycheck.domain.model.OwnedVehicle
import com.lastpaycheck.domain.model.RaceOutcome
import com.lastpaycheck.domain.model.RaceResult
import com.lastpaycheck.domain.model.RaceState
import com.lastpaycheck.domain.model.SessionStatus
import com.lastpaycheck.domain.model.TransactionCategory
import com.lastpaycheck.domain.model.VehicleAction
import com.lastpaycheck.domain.model.VehicleFailure
import com.lastpaycheck.domain.model.VehicleUpgrade
import com.lastpaycheck.domain.model.failure
import kotlin.math.min

internal class VehicleRules {
    fun handle(
        session: GameSession,
        key: OperationKey,
        command: GameCommand.Vehicle,
        ledger: Ledger
    ): DomainResult<GameTransition> {
        val progress = session.vehicles
        val action = command.action
        if (session.activeActivity != null) return failure(GameError.Busy)
        if (progress.race != null && action !is VehicleAction.Drive &&
            action !is VehicleAction.Abandon
        ) {
            return failure(GameError.Busy)
        }
        var cars = progress.garage
        var next = progress
        var money = session.player.moneyMinor
        fun charge(amount: Long, category: TransactionCategory): Boolean {
            if (amount > money) return false
            money -= amount
            ledger.record(session, category, -amount)
            return true
        }
        fun insufficient(amount: Long) = failure(GameError.InsufficientFunds(amount, money))
        fun car(id: String) = cars.find { it.definition.id == id }
        when (action) {
            is VehicleAction.Buy -> {
                if (car(action.modelId) != null) return vehicleFailure(VehicleFailure.ALREADY_OWNED)
                val definition = command.catalog?.vehicles?.find { it.id == action.modelId }
                    ?: return failure(GameError.NotFound(action.modelId))
                if (!charge(definition.priceMinor, TransactionCategory.CAR_PURCHASE)) {
                    return insufficient(definition.priceMinor)
                }
                cars = cars + OwnedVehicle(definition)
                next = next.copy(activeVehicleId = next.activeVehicleId ?: definition.id)
            }
            is VehicleAction.Select -> {
                if (car(action.modelId) == null) return vehicleFailure(VehicleFailure.NOT_OWNED)
                next = next.copy(activeVehicleId = action.modelId)
            }
            is VehicleAction.Upgrade -> {
                val owned = car(action.modelId) ?: return vehicleFailure(VehicleFailure.NOT_OWNED)
                val level = owned.level(action.part)
                if (level >= 3) return vehicleFailure(VehicleFailure.MAX_UPGRADE)
                val price = owned.upgradePrice(action.part)
                if (!charge(price, TransactionCategory.CAR_UPGRADE)) return insufficient(price)
                cars = cars.map {
                    if (it == owned) {
                        it.copy(upgrades = it.upgrades + (action.part to level + 1))
                    } else {
                        it
                    }
                }
            }
            is VehicleAction.Repair -> {
                val owned = car(action.modelId) ?: return vehicleFailure(VehicleFailure.NOT_OWNED)
                if (owned.condition == 100) return failure(GameError.InvalidInput("condition"))
                if (!charge(owned.repairPrice, TransactionCategory.CAR_REPAIR)) {
                    return insufficient(owned.repairPrice)
                }
                cars = cars.map { if (it == owned) it.copy(condition = 100) else it }
            }
            is VehicleAction.StartRace -> {
                val owned = progress.activeVehicle
                    ?: return vehicleFailure(VehicleFailure.NO_ACTIVE_CAR)
                val definition = command.catalog?.races?.find { it.id == action.eventId }
                    ?: return failure(GameError.NotFound(action.eventId))
                if (progress.wins < definition.requiredWins) {
                    return vehicleFailure(VehicleFailure.RACE_LOCKED)
                }
                if (owned.condition < definition.requiredCondition) {
                    return vehicleFailure(
                        VehicleFailure.DAMAGED
                    )
                }
                if (!charge(definition.entryFeeMinor, TransactionCategory.RACE_ENTRY)) {
                    return insufficient(definition.entryFeeMinor)
                }
                next = next.copy(race = RaceState(key.id, definition, owned.definition.id))
            }
            is VehicleAction.Drive -> {
                val race = progress.race?.takeIf { it.id == action.raceId }
                    ?: return vehicleFailure(VehicleFailure.NO_RACE)
                if (action.laneDelta !in -1..1) return failure(GameError.InvalidInput("laneDelta"))
                val owned = checkNotNull(progress.activeVehicle)
                if (action.laneDelta != 0 &&
                    race.elapsedMillis - race.lastSteerMillis < owned.steeringCooldownMillis
                ) {
                    return vehicleFailure(VehicleFailure.STEERING_WAIT)
                }
                next = next.copy(
                    race = race.copy(
                        lane = (race.lane + action.laneDelta).coerceIn(0, 2),
                        previousLane = if (action.laneDelta != 0) race.lane else race.previousLane,
                        braking = action.braking,
                        lastSteerMillis = if (action.laneDelta != 0) {
                            race.elapsedMillis
                        } else {
                            race.lastSteerMillis
                        }
                    )
                )
            }
            is VehicleAction.Abandon -> {
                val race = progress.race?.takeIf { it.id == action.raceId }
                    ?: return vehicleFailure(VehicleFailure.NO_RACE)
                return DomainResult.Success(finish(session, race, RaceOutcome.ABANDONED, ledger))
            }
        }
        return DomainResult.Success(
            GameTransition(
                session.copy(
                    player = session.player.copy(moneyMinor = money),
                    vehicles = next.copy(garage = cars)
                ),
                ledger.transactions
            )
        )
    }

    fun advance(session: GameSession, elapsedMillis: Long, ledger: Ledger): GameSession {
        var race = session.vehicles.race ?: return session
        if (session.status == SessionStatus.FINISHED) {
            return finish(session, race, RaceOutcome.HEALTH, ledger).session
        }
        var owned = checkNotNull(session.vehicles.activeVehicle)
        var remaining =
            min(elapsedMillis, race.definition.limitSeconds * 1000L - race.elapsedMillis)
        var current = session
        while (remaining > 0) {
            val millis = min(100L, remaining)
            val seconds = millis / 1000.0
            var speed = if (race.braking) {
                (race.speedMps - owned.braking * seconds).coerceAtLeast(0.0)
            } else {
                (race.speedMps + owned.acceleration * seconds).coerceAtMost(
                    min(owned.maxSpeedKmh, race.definition.speedLimitKmh.toDouble()) / 3.6
                )
            }
            val distance = min(
                race.definition.lengthMeters.toDouble(),
                race.distanceMeters + (speed + race.speedMps) * 0.5 * seconds
            )
            val hits = race.definition.obstacles.count {
                it.distanceMeters > race.distanceMeters &&
                    it.distanceMeters <= distance && it.lane == race.collisionLane
            }
            if (hits > 0) {
                val damage = (
                    20 - owned.level(VehicleUpgrade.RELIABILITY) * 3 -
                        (if (race.braking) owned.level(VehicleUpgrade.BRAKES) * 2 else 0)
                    ).coerceAtLeast(5)
                owned = owned.copy(condition = (owned.condition - damage * hits).coerceAtLeast(0))
                speed *= 0.35
            }
            val collected = race.definition.pickups.filter {
                it.id !in race.collectedPickupIds && it.distanceMeters > race.distanceMeters &&
                    it.distanceMeters <= distance && it.lane == race.collisionLane
            }.map { it.id }
            race = race.copy(
                elapsedMillis = race.elapsedMillis + millis, distanceMeters = distance,
                speedMps = speed,
                collisions = (race.collisions + hits).coerceAtMost(race.definition.collisionLimit),
                collectedPickupIds = race.collectedPickupIds + collected,
                streak = (if (hits > 0) 0 else race.streak) + collected.size
            )
            current = current.copy(
                vehicles = current.vehicles.copy(
                    race = race,
                    garage = current.vehicles.garage.map {
                        if (it.definition.id == owned.definition.id) owned else it
                    }
                )
            )
            val outcome = when {
                race.collisions >= race.definition.collisionLimit ||
                    owned.condition == 0 -> RaceOutcome.CRASHED
                distance >= race.definition.lengthMeters -> {
                    if (race.collectedPickupIds.size >= race.definition.requiredPickups) {
                        RaceOutcome.WON
                    } else {
                        RaceOutcome.TOKENS_MISSED
                    }
                }
                race.elapsedMillis >= race.definition.limitSeconds * 1000L -> RaceOutcome.TIMEOUT
                else -> null
            }
            if (outcome != null) return finish(current, race, outcome, ledger).session
            remaining -= millis
        }
        return current
    }

    private fun finish(
        session: GameSession,
        race: RaceState,
        outcome: RaceOutcome,
        ledger: Ledger
    ): GameTransition {
        val base = if (outcome == RaceOutcome.WON) race.definition.rewardMinor else 0
        val bonus = if (outcome == RaceOutcome.WON) race.pendingBonusMinor else 0
        val reward = Math.addExact(base, bonus)
        ledger.record(session, TransactionCategory.RACE_PRIZE, reward)
        val result = RaceResult(
            race.id,
            race.definition.id,
            race.vehicleId,
            outcome,
            race.elapsedMillis,
            race.collisions,
            reward,
            base,
            bonus,
            race.collectedPickupIds.size,
            race.streak
        )
        val progress = session.vehicles
        return GameTransition(
            session.copy(
                player = session.player.copy(
                    moneyMinor = Math.addExact(session.player.moneyMinor, reward)
                ),
                vehicles = progress.copy(
                    race = null,
                    history = (progress.history + result).takeLast(100),
                    completed = Math.addExact(progress.completed, 1),
                    wins = Math.addExact(progress.wins, if (outcome == RaceOutcome.WON) 1 else 0)
                )
            ),
            ledger.transactions
        )
    }

    private fun vehicleFailure(reason: VehicleFailure) = failure(GameError.Vehicle(reason))
}
