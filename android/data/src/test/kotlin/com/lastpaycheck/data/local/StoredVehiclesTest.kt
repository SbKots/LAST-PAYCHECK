package com.lastpaycheck.data.local

import com.lastpaycheck.data.catalog.DemoVehicleCatalogRepository
import com.lastpaycheck.data.catalog.TrainingGameCatalogRepository
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.GameSession
import com.lastpaycheck.domain.model.Needs
import com.lastpaycheck.domain.model.OwnedVehicle
import com.lastpaycheck.domain.model.PlayerState
import com.lastpaycheck.domain.model.RaceOutcome
import com.lastpaycheck.domain.model.RaceResult
import com.lastpaycheck.domain.model.RaceState
import com.lastpaycheck.domain.model.VehicleProgress
import com.lastpaycheck.domain.model.VehicleUpgrade
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoredVehiclesTest {
    @Test fun tokenIdsSeriesAndLegacyRaceFieldsSurviveSerialization() = runBlocking {
        val catalog = (DemoVehicleCatalogRepository().get() as DomainResult.Success).value
        val definition = catalog.races.first()
        val token = definition.pickups.first()
        val car = OwnedVehicle(catalog.vehicles.first())
        val race = RaceState(
            "tokens",
            definition,
            car.definition.id,
            distanceMeters = token.distanceMeters + 1.0,
            collectedPickupIds = setOf(token.id),
            streak = 1
        )
        val expected = session().copy(
            vehicles = VehicleProgress(
                listOf(car),
                car.definition.id,
                race
            )
        )
        val restored = Json.decodeFromString<StoredGameSession>(
            Json.encodeToString(expected.toStored())
        ).toDomain()
        assertEquals(expected, restored)
        assertEquals(125, restored.vehicles.race!!.multiplierPercent)
        val oldResult = """
            {"id":"old","event":"night-standard","car":"city-hatch","outcome":"WON",
            "elapsed":32000,"collisions":0,"reward":180}
        """.trimIndent()
        val legacy = Json.decodeFromString<StoredRaceResult>(oldResult)
        assertEquals(180L, legacy.base)
        assertEquals(0L, legacy.bonus)
        assertEquals(0, legacy.collected)
    }

    private suspend fun session(): GameSession {
        val catalog = (TrainingGameCatalogRepository().get() as DomainResult.Success).value
        return GameSession(
            "legacy",
            catalog.version,
            PlayerState(100_000, Needs.of(100, 100, 100, 100), catalog.jobs.first().id),
            catalog.scenarios.first(),
            100
        )
    }

    @Test fun snapshotBeforeVehicleSupportLoadsWithAnEmptyGarage() = runBlocking {
        val original = session()
        val json = Json.encodeToString(original.toStored())
        assertFalse(json.contains("\"vehicles\""))
        val restored = Json.decodeFromString<StoredGameSession>(json).toDomain()
        assertEquals(original, restored)
        assertTrue(restored.vehicles.garage.isEmpty())
        assertTrue(restored.isValid())
    }

    @Test fun ownershipUpgradesSelectionAndRunningRaceRoundTrip() = runBlocking {
        val catalog = (DemoVehicleCatalogRepository().get() as DomainResult.Success).value
        val car = OwnedVehicle(catalog.vehicles.first(), 82, mapOf(VehicleUpgrade.ENGINE to 2))
        val race = RaceState(
            "race-1",
            catalog.races.first(),
            car.definition.id,
            elapsedMillis = 1234,
            distanceMeters = 3.0,
            speedMps = 4.0,
            lane = 2
        )
        val expected = session().copy(
            vehicles = VehicleProgress(listOf(car), car.definition.id, race)
        )
        assertTrue(expected.isValid())
        val restored = Json.decodeFromString<StoredGameSession>(
            Json.encodeToString(expected.toStored())
        ).toDomain()
        assertEquals(expected, restored)
        assertTrue(restored.isValid())
    }

    @Test fun durableResultAndLifetimeCountersRoundTrip() = runBlocking {
        val catalog = (DemoVehicleCatalogRepository().get() as DomainResult.Success).value
        val car = OwnedVehicle(catalog.vehicles.first())
        val result =
            RaceResult("won", "night-standard", car.definition.id, RaceOutcome.WON, 34000, 0, 180)
        val expected = session().copy(
            vehicles = VehicleProgress(
                listOf(car),
                car.definition.id,
                history = listOf(result),
                completed = 1,
                wins = 1
            )
        )
        assertEquals(
            expected,
            Json.decodeFromString<StoredGameSession>(
                Json.encodeToString(expected.toStored())
            ).toDomain()
        )
    }

    @Test fun corruptVehicleStateIsRejectedBySessionValidation() = runBlocking {
        val catalog = (DemoVehicleCatalogRepository().get() as DomainResult.Success).value
        val car = OwnedVehicle(catalog.vehicles.first(), condition = 101)
        assertFalse(
            session().copy(vehicles = VehicleProgress(listOf(car), car.definition.id)).isValid()
        )
    }
}
