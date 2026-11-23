package com.lastpaycheck.domain.usecase

import com.lastpaycheck.domain.engine.GameEngine
import com.lastpaycheck.domain.engine.GameSessionCoordinator
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.OperationKey
import com.lastpaycheck.domain.model.OwnedVehicle
import com.lastpaycheck.domain.model.PauseReason
import com.lastpaycheck.domain.model.RaceDefinition
import com.lastpaycheck.domain.model.RaceObstacle
import com.lastpaycheck.domain.model.RaceOutcome
import com.lastpaycheck.domain.model.RacePickup
import com.lastpaycheck.domain.model.RaceState
import com.lastpaycheck.domain.model.TransactionCategory
import com.lastpaycheck.domain.model.VehicleCatalog
import com.lastpaycheck.domain.model.VehicleDefinition
import com.lastpaycheck.domain.model.VehicleProgress
import com.lastpaycheck.domain.model.WorkShift
import com.lastpaycheck.domain.repository.VehicleCatalogRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RaceBonusTest {
    @Test fun finishWithoutTheRequiredTokensDoesNotPayThePrize() = runTest {
        val definition = track(listOf(RacePickup("missed", 500, 0)))
            .copy(requiredPickups = 1)
        val game = Game(
            RaceState("race", definition, car.id, distanceMeters = 980.0, speedMps = 20.0)
        )
        game.open()
        game.step(2000)
        assertEquals(RaceOutcome.TOKENS_MISSED, game.state.vehicles.history.single().outcome)
        assertEquals(0L, game.state.vehicles.history.single().rewardMinor)
        assertEquals(0L, game.state.vehicles.wins)
    }

    @Test fun earningWagesAllowsRetryOfThePreviouslyUnaffordableSportCar() = runTest {
        val game = Fixture(
            sampleSession().copy(player = sampleSession().player.copy(moneyMinor = 13999))
        ).open()
        val source = GetVehicleCatalogUseCase(object : VehicleCatalogRepository {
            override suspend fun get() = DomainResult.Success(
                VehicleCatalog("qa", listOf(car), listOf(track()))
            )
        })
        val buy = BuyVehicleUseCase(source, game.coordinator)
        buy(game.key("poor"), car.id).failsWith(GameError.InsufficientFunds(14000, 13999))
        game.startShift(game.key("work"), helperJob.id).ok()
        buy(game.key("busy"), car.id).failsWith(GameError.Busy)
        repeat(24) { index ->
            if (index > 0) game.clock.now += 2500
            val box = (game.session.activeActivity as WorkShift).selectedBox
            PlaceBoxUseCase(game.coordinator)(game.key("box-$index"), box.id, box.category).ok()
        }
        buy(game.key("retry"), car.id).ok()
        assertEquals(499L, game.session.player.moneyMinor)
        assertEquals(car.id, game.session.vehicles.activeVehicleId)
    }

    private val car = VehicleDefinition("sport-coupe", 14000, 200, 3.0, 0.7, 60, 10.0, 5)
    private fun track(
        pickups: List<RacePickup> = emptyList(),
        obstacles: List<RaceObstacle> = emptyList()
    ) = RaceDefinition("test", 1000, 120, 0, 400, 1, obstacles, pickups, 10)

    private inner class Game(race: RaceState) {
        val clock = FakeClock()
        val initial = sampleSession().let {
            it.copy(
                player = it.player.copy(moneyMinor = 100_000),
                vehicles = VehicleProgress(listOf(OwnedVehicle(car)), car.id, race)
            )
        }
        val store = FakeSessions(initial)
        var coordinator = GameSessionCoordinator(GameEngine(), store, clock)
        val state get() = checkNotNull(coordinator.session.value)
        suspend fun open() {
            ContinueGameUseCase(coordinator)().ok()
        }
        suspend fun step(ms: Long) {
            clock.now += ms
            UpdateGameTimeUseCase(coordinator)().ok()
        }
        suspend fun reopen() {
            SaveProgressUseCase(coordinator)().ok()
            coordinator = GameSessionCoordinator(GameEngine(), store, clock)
            open()
        }
    }

    @Test fun pickupRequiresTheCorrectLaneAndCannotBeCollectedTwice() = runTest {
        val definition = track(listOf(RacePickup("gold", 10, 1), RacePickup("miss", 12, 0)))
        val game = Game(RaceState("race", definition, car.id, speedMps = 20.0))
        game.open()
        game.step(1000)
        assertEquals(setOf("gold"), game.state.vehicles.race!!.collectedPickupIds)
        assertEquals(1, game.state.vehicles.race!!.streak)
        game.reopen()
        game.step(1000)
        assertEquals(setOf("gold"), game.state.vehicles.race!!.collectedPickupIds)
        assertEquals(1, game.state.vehicles.race!!.streak)
    }

    @Test fun multiplierThresholdsAreExactAndCappedAtThree() {
        val coins = (1..15).map { RacePickup("$it", it, 1) }
        val expected = listOf(100, 125, 125, 150, 150, 200, 200, 250, 250, 250, 300, 300, 300)
        expected.forEachIndexed { streak, percent ->
            val state = RaceState(
                "race",
                track(coins),
                car.id,
                distanceMeters = 20.0,
                collectedPickupIds = coins.take(streak).map { it.id }.toSet(),
                streak = streak
            )
            assertTrue(state.isValid())
            assertEquals(percent, state.multiplierPercent)
            assertEquals(streak * 10L * percent / 100, state.pendingBonusMinor)
        }
    }

    @Test fun collisionLosesOnlyTheSeriesAndNewTokensStartANewBonus() = runTest {
        val definition = track(
            listOf(RacePickup("a", 890, 1), RacePickup("b", 910, 1), RacePickup("c", 950, 1)),
            listOf(RaceObstacle(930, 1))
        )
        val game = Game(
            RaceState(
                "race",
                definition,
                car.id,
                distanceMeters = 880.0,
                speedMps = 30.0
            )
        )
        game.open()
        game.step(1500)
        assertEquals(2, game.state.vehicles.race!!.streak)
        game.step(500)
        assertEquals(0, game.state.vehicles.race!!.streak)
        assertEquals(100, game.state.vehicles.race!!.multiplierPercent)
        assertEquals(0L, game.state.vehicles.race!!.pendingBonusMinor)
        game.step(10_000)
        val result = game.state.vehicles.history.single()
        assertEquals(RaceOutcome.WON, result.outcome)
        assertEquals(3, result.collected)
        assertEquals(1, result.finalStreak)
        assertEquals(400L, result.baseRewardMinor)
        assertEquals(12L, result.bonusRewardMinor)
        assertEquals(412L, result.rewardMinor)
    }

    @Test fun pauseAndRestartPreserveTheSeriesWithoutGeneratingMoney() = runTest {
        val definition = track(listOf(RacePickup("one", 10, 1)))
        val game = Game(RaceState("race", definition, car.id, speedMps = 20.0))
        game.open()
        game.step(1000)
        SetPauseUseCase(game.coordinator)(PauseReason.MANUAL, true).ok()
        val race = game.state.vehicles.race
        game.step(60_000)
        assertEquals(race, game.state.vehicles.race)
        game.reopen()
        assertEquals(race, game.state.vehicles.race)
        assertEquals(100_000L, game.state.player.moneyMinor)
        assertTrue(game.store.transactions.isEmpty())
    }

    @Test fun finishPaysBaseAndBonusOnceAndLosingPaysNeither() = runTest {
        val definition = track(listOf(RacePickup("one", 990, 1)))
        val game = Game(
            RaceState(
                "race",
                definition,
                car.id,
                distanceMeters = 980.0,
                speedMps = 20.0
            )
        )
        game.open()
        game.step(2000)
        val result = game.state.vehicles.history.single()
        assertEquals(412L, result.rewardMinor)
        game.reopen()
        game.step(1000)
        assertEquals(result, game.state.vehicles.history.single())
        assertEquals(100_412L, game.state.player.moneyMinor)
        assertEquals(
            1,
            game.store.transactions.count { it.category == TransactionCategory.RACE_PRIZE }
        )
        val losing = Game(
            RaceState(
                "lost",
                definition.copy(obstacles = listOf(RaceObstacle(985, 1))),
                car.id,
                distanceMeters = 980.0,
                speedMps = 20.0,
                collisions = 2
            )
        )
        losing.open()
        losing.step(2000)
        assertEquals(0L, losing.state.vehicles.history.single().rewardMinor)
    }

    @Test fun lockedRaceCannotStartAndSportCarPurchaseCanBeRetriedAfterUnpausing() = runTest {
        val game = Fixture(
            sampleSession().copy(player = sampleSession().player.copy(moneyMinor = 14000))
        ).open()
        val catalog = GetVehicleCatalogUseCase(object : VehicleCatalogRepository {
            override suspend fun get() = DomainResult.Success(
                VehicleCatalog("qa", listOf(car), listOf(track().copy(requiredWins = 2)))
            )
        })
        val buy = BuyVehicleUseCase(catalog, game.coordinator)
        SetPauseUseCase(game.coordinator)(PauseReason.MANUAL, true).ok()
        buy(game.key("paused"), car.id).failsWith(GameError.Paused)
        SetPauseUseCase(game.coordinator)(PauseReason.MANUAL, false).ok()
        buy(game.key("retry"), car.id).ok()
        assertEquals(0L, game.session.player.moneyMinor)
        assertEquals(car.id, game.session.vehicles.activeVehicleId)
        assertTrue(
            StartRaceUseCase(catalog, game.coordinator)(
                game.key("locked"),
                "test"
            ) is DomainResult.Failure
        )
        val poor = Fixture().open()
        buySportWith(catalog, poor).failsWith(GameError.InsufficientFunds(14000, 150))
    }

    private suspend fun buySportWith(catalog: GetVehicleCatalogUseCase, game: Fixture) =
        BuyVehicleUseCase(catalog, game.coordinator)(OperationKey(game.session.id, "poor"), car.id)
}
