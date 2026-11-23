package com.lastpaycheck.data.catalog

import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.RaceDefinition
import com.lastpaycheck.domain.model.RaceObstacle
import com.lastpaycheck.domain.model.RacePickup
import com.lastpaycheck.domain.model.RaceStyle
import com.lastpaycheck.domain.model.VehicleCatalog
import com.lastpaycheck.domain.model.VehicleDefinition
import com.lastpaycheck.domain.repository.VehicleCatalogRepository

class DemoVehicleCatalogRepository : VehicleCatalogRepository {
    override suspend fun get() = DomainResult.Success(
        VehicleCatalog(
            "demo-1+tracks-2",
            listOf(
                VehicleDefinition("city-hatch", 6000, 140, 3.0, 0.7, 40, 6.0, 5),
                VehicleDefinition("family-wagon", 9500, 160, 3.0, 0.7, 50, 7.5, 5),
                VehicleDefinition("sport-coupe", 14000, 200, 3.0, 0.7, 60, 10.0, 5)
            ),
            listOf(
                track("night-standard", 1200, 120, 90, 0, 400, 2, 0, RaceStyle.NIGHT),
                track("city-circuit", 3600, 240, 100, 100, 1000, 4, 1, RaceStyle.CITY),
                track("industrial-run", 6000, 300, 125, 300, 2200, 6, 2, RaceStyle.INDUSTRIAL)
            )
        )
    )
}

private fun track(
    id: String,
    length: Int,
    limit: Int,
    speed: Int,
    fee: Long,
    prize: Long,
    token: Long,
    wins: Long,
    style: RaceStyle
): RaceDefinition {
    val barriers = mutableListOf<RaceObstacle>()
    val pickups = mutableListOf<RacePickup>()
    var distance = 0
    var index = 0
    while (true) {
        val gap = when (style) {
            RaceStyle.NIGHT -> 150
            RaceStyle.CITY -> if (distance < length / 2) 140 else 115
            RaceStyle.INDUSTRIAL -> when {
                distance < length / 3 -> 140
                distance < length * 2 / 3 -> 110
                else -> 90
            }
        }
        val next = distance + gap
        if (next >= length) break
        val lane = listOf(1, 0, 2, 0, 1, 2, 1)[index % 7]
        barriers += RaceObstacle(next, lane)
        pickups += RacePickup("$id-token-$index", distance + gap / 2, (lane + 1 + index % 2) % 3)
        distance = next
        index++
    }
    return RaceDefinition(
        id, length, limit, fee, prize, 30, barriers, pickups, token, speed,
        if (style == RaceStyle.NIGHT) 3 else if (style == RaceStyle.CITY) 4 else 5,
        wins, style, if (style == RaceStyle.NIGHT) 0 else if (style == RaceStyle.CITY) 3 else 8
    )
}
