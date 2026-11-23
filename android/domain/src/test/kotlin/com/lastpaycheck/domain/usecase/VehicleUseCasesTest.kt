package com.lastpaycheck.domain.usecase

import com.lastpaycheck.domain.engine.GameEngine
import com.lastpaycheck.domain.engine.GameSessionCoordinator
import com.lastpaycheck.domain.model.ActivityRequest
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.OperationKey
import com.lastpaycheck.domain.model.OwnedVehicle
import com.lastpaycheck.domain.model.PauseReason
import com.lastpaycheck.domain.model.RaceDefinition
import com.lastpaycheck.domain.model.RaceObstacle
import com.lastpaycheck.domain.model.RaceOutcome
import com.lastpaycheck.domain.model.TransactionCategory
import com.lastpaycheck.domain.model.VehicleCatalog
import com.lastpaycheck.domain.model.VehicleDefinition
import com.lastpaycheck.domain.model.VehicleFailure
import com.lastpaycheck.domain.model.VehicleProgress
import com.lastpaycheck.domain.model.VehicleUpgrade
import com.lastpaycheck.domain.repository.VehicleCatalogRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleUseCasesTest {
    private class Fixture(money: Long = 100_000, owned: OwnedVehicle? = null) {
        val clock = FakeClock()
        val initial = sampleSession().let {
            it.copy(
                player = it.player.copy(moneyMinor = money),
                vehicles = if (owned == null) {
                    VehicleProgress()
                } else {
                    VehicleProgress(
                        listOf(owned),
                        owned.definition.id
                    )
                }
            )
        }
        val store = FakeSessions(initial)
        val coordinator = GameSessionCoordinator(GameEngine(), store, clock)
        var source: DomainResult<VehicleCatalog> = DomainResult.Success(catalog())
        val get = GetVehicleCatalogUseCase(object : VehicleCatalogRepository {
            override suspend fun get() = source
        })
        val buy = BuyVehicleUseCase(get, coordinator)
        val select = SelectActiveVehicleUseCase(coordinator)
        val upgrade = UpgradeVehicleUseCase(coordinator)
        val repair = RepairVehicleUseCase(coordinator)
        val start = StartRaceUseCase(get, coordinator)
        val drive = DriveRaceUseCase(coordinator)
        val abandon = AbandonRaceUseCase(coordinator)
        var sequence = 0
        fun key() = OperationKey(initial.id, "vehicle-${sequence++}")
        val state get() = checkNotNull(coordinator.session.value)
        suspend fun open() {
            ContinueGameUseCase(coordinator)().vehicleValue()
        }
        suspend fun advance(millis: Long) {
            clock.now += millis
            UpdateGameTimeUseCase(coordinator)().vehicleValue()
        }
    }

    @Test fun catalogAvailabilityValidationAndRepositoryErrors() = runBlocking {
        val f = Fixture()
        assertEquals(3, f.get().vehicleValue().vehicles.size)
        f.source = DomainResult.Success(catalog().copy(vehicles = emptyList()))
        assertEquals(DomainResult.Failure(GameError.InvalidCatalog), f.get())
        f.source = DomainResult.Failure(GameError.CatalogUnavailable)
        assertEquals(f.source, f.get())
    }

    @Test fun purchaseChargesExactlyOnceAndPersistsOwnershipWithJournal() = runBlocking {
        val f = Fixture()
        f.open()
        val key = f.key()
        f.buy(key, "city-hatch").vehicleValue()
        f.buy(key, "city-hatch").vehicleValue()
        assertEquals(94_000L, f.state.player.moneyMinor)
        assertEquals("city-hatch", f.state.vehicles.activeVehicleId)
        assertEquals(1, f.store.persisted!!.vehicles.garage.size)
        assertEquals(listOf(-6000L), f.store.transactions.map { it.amountMinor })
        assertEquals(TransactionCategory.CAR_PURCHASE, f.store.transactions.single().category)
    }

    @Test fun repeatedModelWithANewOperationCannotChargeAgain() = runBlocking {
        val f = Fixture()
        f.open()
        f.buy(f.key(), "city-hatch").vehicleValue()
        assertEquals(
            DomainResult.Failure(GameError.Vehicle(VehicleFailure.ALREADY_OWNED)),
            f.buy(f.key(), "city-hatch")
        )
        assertEquals(94_000L, f.state.player.moneyMinor)
    }

    @Test fun insufficientFundsAndUnknownModelDoNotMutateAnything() = runBlocking {
        val f = Fixture(money = 5999)
        f.open()
        assertEquals(
            DomainResult.Failure(GameError.InsufficientFunds(6000, 5999)),
            f.buy(f.key(), "city-hatch")
        )
        assertTrue(f.buy(f.key(), "unknown") is DomainResult.Failure)
        assertTrue(f.buy(f.key(), "") is DomainResult.Failure)
        assertTrue(f.state.vehicles.garage.isEmpty())
        assertEquals(5999L, f.state.player.moneyMinor)
        assertTrue(f.store.transactions.isEmpty())
    }

    @Test fun activeVehicleMustBeOwnedAndSelectionSurvivesReopen() = runBlocking {
        val f = Fixture()
        f.open()
        assertTrue(f.select(f.key(), "city-hatch") is DomainResult.Failure)
        f.buy(f.key(), "city-hatch").vehicleValue()
        f.buy(f.key(), "sport-coupe").vehicleValue()
        f.select(f.key(), "sport-coupe").vehicleValue()
        val restored = ContinueGameUseCase(
            GameSessionCoordinator(GameEngine(), f.store, f.clock)
        )().vehicleValue()
        assertEquals("sport-coupe", restored.vehicles.activeVehicleId)
        assertEquals(2, restored.vehicles.garage.size)
    }

    @Test fun upgradesHaveCostsLimitsAndEffectiveStatsAndAreIdempotent() = runBlocking {
        val f = Fixture()
        f.open()
        f.buy(f.key(), "city-hatch").vehicleValue()
        val base = f.state.vehicles.activeVehicle!!
        val key = f.key()
        f.upgrade(key, "city-hatch", VehicleUpgrade.ENGINE).vehicleValue()
        f.upgrade(key, "city-hatch", VehicleUpgrade.ENGINE).vehicleValue()
        assertEquals(1, f.state.vehicles.activeVehicle!!.level(VehicleUpgrade.ENGINE))
        assertEquals(93_400L, f.state.player.moneyMinor)
        f.upgrade(f.key(), "city-hatch", VehicleUpgrade.ENGINE).vehicleValue()
        f.upgrade(f.key(), "city-hatch", VehicleUpgrade.ENGINE).vehicleValue()
        assertTrue(f.upgrade(f.key(), "city-hatch", VehicleUpgrade.ENGINE) is DomainResult.Failure)
        f.upgrade(f.key(), "city-hatch", VehicleUpgrade.HANDLING).vehicleValue()
        f.upgrade(f.key(), "city-hatch", VehicleUpgrade.BRAKES).vehicleValue()
        val improved = f.state.vehicles.activeVehicle!!
        assertTrue(improved.acceleration > base.acceleration)
        assertTrue(improved.maxSpeedKmh > base.maxSpeedKmh)
        assertTrue(improved.steeringCooldownMillis < base.steeringCooldownMillis)
        assertTrue(improved.braking > base.braking)
    }

    @Test fun upgradeFundsAndUnownedVehicleAreValidated() = runBlocking {
        val f = Fixture(money = 6000)
        f.open()
        f.buy(f.key(), "city-hatch").vehicleValue()
        assertTrue(f.upgrade(f.key(), "city-hatch", VehicleUpgrade.ENGINE) is DomainResult.Failure)
        assertTrue(f.upgrade(f.key(), "none", VehicleUpgrade.ENGINE) is DomainResult.Failure)
        assertEquals(0, f.state.vehicles.activeVehicle!!.level(VehicleUpgrade.ENGINE))
    }

    @Test fun raceRequiresAnActiveUsableCarAndChargesTheDefinedEntryFee() = runBlocking {
        val f = Fixture()
        f.open()
        assertTrue(f.start(f.key(), "night-standard") is DomainResult.Failure)
        f.buy(f.key(), "city-hatch").vehicleValue()
        f.source = DomainResult.Success(
            catalog().let {
                it.copy(races = it.races.map { race -> race.copy(entryFeeMinor = 150) })
            }
        )
        f.start(f.key(), "night-standard").vehicleValue()
        assertEquals(93_850L, f.state.player.moneyMinor)
        assertEquals(TransactionCategory.RACE_ENTRY, f.store.transactions.last().category)
        assertTrue(f.start(f.key(), "night-standard") is DomainResult.Failure)
        val damaged = Fixture(owned = OwnedVehicle(catalog().vehicles.first(), condition = 0))
        damaged.open()
        assertTrue(damaged.start(damaged.key(), "night-standard") is DomainResult.Failure)
    }

    @Test fun driveValidatesRaceIdDirectionAndSteeringCooldown() = runBlocking {
        val f = Fixture(owned = OwnedVehicle(catalog().vehicles.first()))
        f.open()
        f.start(f.key(), "night-standard").vehicleValue()
        val id = f.state.vehicles.race!!.id
        assertTrue(f.drive(f.key(), id, 2, false) is DomainResult.Failure)
        assertTrue(f.drive(f.key(), "stale", 1, false) is DomainResult.Failure)
        val key = f.key()
        f.drive(key, id, 1, false).vehicleValue()
        f.drive(key, id, 1, false).vehicleValue()
        assertEquals(2, f.state.vehicles.race!!.lane)
        assertTrue(f.drive(f.key(), id, -1, false) is DomainResult.Failure)
        f.advance(400)
        f.drive(f.key(), id, -1, true).vehicleValue()
        assertEquals(1, f.state.vehicles.race!!.lane)
        assertTrue(f.state.vehicles.race!!.braking)
    }

    @Test fun playerCanAvoidObstaclesWinAndReceiveExactlyOneDurablePrize() = runBlocking {
        val f = Fixture(owned = OwnedVehicle(catalog().vehicles.first()))
        f.open()
        f.start(f.key(), "night-standard").vehicleValue()
        var ticks = 0
        while (f.state.vehicles.race != null && ticks++ < 1300) {
            val race = f.state.vehicles.race!!
            val hazard = race.definition.obstacles.firstOrNull {
                it.distanceMeters > race.distanceMeters
            }
            if (hazard != null && hazard.distanceMeters - race.distanceMeters < 45 &&
                hazard.lane == race.lane
            ) {
                f.drive(f.key(), race.id, if (race.lane == 2) -1 else 1, false).vehicleValue()
            }
            f.advance(100)
        }
        val result = f.state.vehicles.history.single()
        assertEquals(RaceOutcome.WON, result.outcome)
        assertEquals(180L, result.rewardMinor)
        assertEquals(100_180L, f.store.persisted!!.player.moneyMinor)
        f.advance(1000)
        assertEquals(
            1,
            f.store.transactions.count { it.category == TransactionCategory.RACE_PRIZE }
        )
        val restored = ContinueGameUseCase(
            GameSessionCoordinator(GameEngine(), f.store, f.clock)
        )().vehicleValue()
        assertEquals(result, restored.vehicles.history.single())
        assertEquals(1L, restored.vehicles.wins)
    }

    @Test fun ignoringObstaclesLosesAndReliabilityReducesPersistentDamage() = runBlocking {
        suspend fun lose(level: Int): Fixture {
            val f =
                Fixture(
                    owned = OwnedVehicle(
                        catalog().vehicles.first(),
                        upgrades = mapOf(VehicleUpgrade.RELIABILITY to level)
                    )
                )
            f.open()
            f.start(f.key(), "night-standard").vehicleValue()
            f.advance(100_000)
            assertEquals(RaceOutcome.CRASHED, f.state.vehicles.history.single().outcome)
            assertEquals(0L, f.state.vehicles.history.single().rewardMinor)
            assertEquals(100_000L, f.state.player.moneyMinor)
            return f
        }
        val normal = lose(0)
        val improved = lose(3)
        assertTrue(
            improved.state.vehicles.activeVehicle!!.condition >
                normal.state.vehicles.activeVehicle!!.condition
        )
    }

    @Test fun brakingCanCauseTimeoutAndEngineChangesRaceAcceleration() = runBlocking {
        val f = Fixture(owned = OwnedVehicle(catalog().vehicles.first()))
        f.open()
        f.start(f.key(), "night-standard").vehicleValue()
        f.drive(f.key(), f.state.vehicles.race!!.id, 0, true).vehicleValue()
        f.advance(120_000)
        assertEquals(RaceOutcome.TIMEOUT, f.state.vehicles.history.single().outcome)
        val boosted =
            Fixture(
                owned = OwnedVehicle(
                    catalog().vehicles.first(),
                    upgrades = mapOf(VehicleUpgrade.ENGINE to 3)
                )
            )
        boosted.open()
        boosted.start(boosted.key(), "night-standard").vehicleValue()
        boosted.advance(1000)
        assertTrue(boosted.state.vehicles.race!!.speedMps > 3.0)
    }

    @Test fun abandoningIsSavedWithoutRewardAndCannotBePaidAgain() = runBlocking {
        val f = Fixture(owned = OwnedVehicle(catalog().vehicles.first()))
        f.open()
        f.start(f.key(), "night-standard").vehicleValue()
        val id = f.state.vehicles.race!!.id
        val key = f.key()
        f.abandon(key, id).vehicleValue()
        f.abandon(key, id).vehicleValue()
        assertEquals(1L, f.state.vehicles.completed)
        assertEquals(RaceOutcome.ABANDONED, f.state.vehicles.history.single().outcome)
        assertTrue(f.abandon(f.key(), id) is DomainResult.Failure)
        assertEquals(100_000L, f.state.player.moneyMinor)
    }

    @Test fun repairsRestoreConditionChargeActualDamageAndSurviveReload() = runBlocking {
        val f = Fixture(owned = OwnedVehicle(catalog().vehicles.first(), condition = 40))
        f.open()
        f.repair(f.key(), "city-hatch").vehicleValue()
        assertEquals(99_700L, f.state.player.moneyMinor)
        assertEquals(100, f.state.vehicles.activeVehicle!!.condition)
        assertTrue(f.repair(f.key(), "city-hatch") is DomainResult.Failure)
        assertEquals(TransactionCategory.CAR_REPAIR, f.store.transactions.single().category)
    }

    @Test fun repositoryFailureDoesNotLeaveAPartialPurchase() = runBlocking {
        val f = Fixture()
        f.open()
        f.store.writeError = GameError.StorageFailure
        assertEquals(DomainResult.Failure(GameError.StorageFailure), f.buy(f.key(), "city-hatch"))
        assertEquals(100_000L, f.store.persisted!!.player.moneyMinor)
        assertTrue(f.store.persisted!!.vehicles.garage.isEmpty())
        assertTrue(f.store.transactions.isEmpty())
    }

    @Test fun pausedRaceDoesNotMoveAndOtherActivitiesCannotOverlapIt() = runBlocking {
        val f = Fixture(owned = OwnedVehicle(catalog().vehicles.first()))
        f.open()
        f.start(f.key(), "night-standard").vehicleValue()
        assertTrue(
            StartActivityUseCase(
                FakeCatalog(),
                f.coordinator
            )(f.key(), ActivityRequest.Rest) is DomainResult.Failure
        )
        SetPauseUseCase(f.coordinator)(PauseReason.MANUAL, true).vehicleValue()
        val race = f.state.vehicles.race
        f.advance(10_000)
        assertEquals(race, f.state.vehicles.race)
        assertTrue(f.drive(f.key(), race!!.id, 1, false) is DomainResult.Failure)
    }

    companion object {
        private fun catalog() = VehicleCatalog(
            "demo-1",
            listOf(
                VehicleDefinition("city-hatch", 6000, 140, 3.0, 0.7, 40, 6.0, 5),
                VehicleDefinition("family-wagon", 9500, 160, 3.0, 0.7, 50, 7.5, 5),
                VehicleDefinition("sport-coupe", 14000, 200, 3.0, 0.7, 60, 10.0, 5)
            ),
            listOf(
                RaceDefinition(
                    "night-standard",
                    1000,
                    120,
                    0,
                    180,
                    1,
                    listOf(1, 0, 2, 1, 2, 0, 1, 0, 2).mapIndexed { i, lane ->
                        RaceObstacle(
                            (i + 1) * 100,
                            lane
                        )
                    }
                )
            )
        )
    }
}

private fun <T> DomainResult<T>.vehicleValue(): T = (this as DomainResult.Success).value
