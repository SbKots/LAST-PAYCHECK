package com.lastpaycheck.data.local

import com.lastpaycheck.data.catalog.DemoVehicleCatalogRepository
import com.lastpaycheck.data.catalog.TrainingGameCatalogRepository
import com.lastpaycheck.domain.engine.GameEngine
import com.lastpaycheck.domain.engine.GameSessionCoordinator
import com.lastpaycheck.domain.model.CommittedOperation
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.FinancialTransaction
import com.lastpaycheck.domain.model.GameSession
import com.lastpaycheck.domain.model.GameTransition
import com.lastpaycheck.domain.model.Needs
import com.lastpaycheck.domain.model.OperationKey
import com.lastpaycheck.domain.model.OperationReceipt
import com.lastpaycheck.domain.model.OwnedVehicle
import com.lastpaycheck.domain.model.PlayerState
import com.lastpaycheck.domain.model.RaceOutcome
import com.lastpaycheck.domain.model.SessionRevision
import com.lastpaycheck.domain.model.VehicleProgress
import com.lastpaycheck.domain.model.VehicleUpgrade
import com.lastpaycheck.domain.repository.CheckpointChoice
import com.lastpaycheck.domain.repository.GameSessionRepository
import com.lastpaycheck.domain.usecase.ContinueGameUseCase
import com.lastpaycheck.domain.usecase.DriveRaceUseCase
import com.lastpaycheck.domain.usecase.GetVehicleCatalogUseCase
import com.lastpaycheck.domain.usecase.StartRaceUseCase
import com.lastpaycheck.domain.usecase.UpdateGameTimeUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RaceBalanceTest {
    @Test fun simulateEveryTrackWithPerfectDrivingAndTwelveFallibleDrivers() = runBlocking {
        val catalog = DemoVehicleCatalogRepository()
        val vehicles = catalog.get().value()
        val training = TrainingGameCatalogRepository().get().value()
        assertEquals(3, vehicles.races.size)
        vehicles.races.forEachIndexed { difficulty, definition ->
            assertTrue(definition.isValid())
            assertTrue(definition.pickups.isNotEmpty())
            assertTrue(
                definition.obstacles.zipWithNext().all { (a, b) ->
                    b.distanceMeters - a.distanceMeters > definition.speedLimitKmh / 3.6 * 1.5
                }
            )
            val samples = (0..12).map { seed ->
                val car = OwnedVehicle(
                    vehicles.vehicles.last(),
                    upgrades = mapOf(VehicleUpgrade.ENGINE to 2, VehicleUpgrade.RELIABILITY to 2)
                )
                val store = MemorySessions(
                    GameSession(
                        "simulation",
                        training.version,
                        PlayerState(
                            100_000,
                            Needs.of(100, 100, 100, 100),
                            training.jobs.first().id
                        ),
                        training.scenarios.first(),
                        100,
                        vehicles = VehicleProgress(
                            listOf(car),
                            car.definition.id,
                            completed = 2,
                            wins = 2
                        )
                    )
                )
                var now = 0L
                var operation = 0
                val coordinator = GameSessionCoordinator(GameEngine(), store) { now }
                ContinueGameUseCase(coordinator)().value()
                fun key() = OperationKey("simulation", "action-${operation++}")
                StartRaceUseCase(GetVehicleCatalogUseCase(catalog), coordinator)(
                    key(),
                    definition.id
                ).value()
                while (coordinator.session.value!!.vehicles.race != null && now < 310_000) {
                    val race = coordinator.session.value!!.vehicles.race!!
                    val hazardIndex = definition.obstacles.indexOfFirst {
                        it.distanceMeters > race.distanceMeters
                    }
                    val hazard = definition.obstacles.getOrNull(hazardIndex)
                    val pickup = definition.pickups.firstOrNull {
                        it.distanceMeters > race.distanceMeters
                    }
                    var lane = race.lane
                    if (pickup != null && pickup.distanceMeters - race.distanceMeters < 85 &&
                        (hazard == null || pickup.distanceMeters < hazard.distanceMeters)
                    ) {
                        lane = pickup.lane
                    }
                    if (hazard != null && hazard.distanceMeters - race.distanceMeters < 55) {
                        val fail = seed > 0 &&
                            (hazardIndex * 37 + seed * 11) % 100 < 3 + difficulty * 2
                        if (fail) {
                            lane = hazard.lane
                        } else if (lane == hazard.lane) lane = (hazard.lane + 1) % 3
                    }
                    if (lane != race.lane &&
                        race.elapsedMillis - race.lastSteerMillis >= car.steeringCooldownMillis
                    ) {
                        DriveRaceUseCase(coordinator)(
                            key(),
                            race.id,
                            if (lane > race.lane) 1 else -1,
                            false
                        ).value()
                    }
                    now += 100
                    UpdateGameTimeUseCase(coordinator)().value()
                }
                val result = coordinator.session.value!!.vehicles.history.single()
                val damageCost = coordinator.session.value!!.vehicles.activeVehicle!!.repairPrice
                if (seed == 0) {
                    assertEquals(RaceOutcome.WON, result.outcome)
                    assertTrue(result.collected > 0)
                    assertTrue(
                        result.elapsedMillis in when (difficulty) {
                            0 -> 45_000L..90_000L
                            1 -> 120_000L..190_000L
                            else -> 120_000L..240_000L
                        }
                    )
                }
                Triple(
                    result.elapsedMillis,
                    result.rewardMinor - definition.entryFeeMinor - damageCost,
                    result.outcome
                )
            }
            val typical = samples.drop(1)
            println(
                "BALANCE ${definition.id}: perfect=${samples.first()}; " +
                    "meanSeconds=${typical.map { it.first / 1000.0 }.average()}; " +
                    "meanNetMinor=${typical.map { it.second.toDouble() }.average()}; " +
                    "wins=${typical.count { it.third == RaceOutcome.WON }}/12"
            )
        }
    }

    private class MemorySessions(var session: GameSession) : GameSessionRepository {
        private val receipts = mutableMapOf<OperationKey, OperationReceipt>()
        private val records = mutableListOf<FinancialTransaction>()
        override suspend fun load(choice: CheckpointChoice) = DomainResult.Success(session)
        override suspend fun readTransactions(revision: SessionRevision) =
            DomainResult.Success(records.toList())
        override suspend fun findReceipt(key: OperationKey) = DomainResult.Success(receipts[key])
        override suspend fun commit(
            expected: SessionRevision,
            key: OperationKey,
            transition: GameTransition
        ): DomainResult<CommittedOperation> {
            receipts[key]?.let { return DomainResult.Success(CommittedOperation(session, it)) }
            check(session.identity == expected)
            session = transition.session.copy(revision = session.revision + 1)
            check(session.isValid())
            val receipt = OperationReceipt(key, session.revision, transition.effect)
            receipts[key] = receipt
            records += transition.transactions
            return DomainResult.Success(CommittedOperation(session, receipt))
        }
        override suspend fun checkpoint(
            expected: SessionRevision,
            session: GameSession
        ): DomainResult<GameSession> {
            check(this.session.identity == expected)
            this.session = session.copy(revision = session.revision + 1)
            return DomainResult.Success(this.session)
        }
        override suspend fun restorePrevious(checkpoint: SessionRevision) =
            error("No rollback in this fake")
        override suspend fun replace(expected: SessionRevision?, session: GameSession) =
            error("No replacement in this fake")
    }

    private fun <T> DomainResult<T>.value() = (this as DomainResult.Success).value
}
