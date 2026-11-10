package com.lastpaycheck.domain.engine

import com.lastpaycheck.domain.model.ActivityRequest
import com.lastpaycheck.domain.model.BoxCategory
import com.lastpaycheck.domain.model.GameCatalog

internal sealed interface GameCommand {
    data class Buy(
        val catalog: GameCatalog,
        val productId: String,
        val quantity: Int,
        val quotedUnitPriceMinor: Long
    ) : GameCommand

    data class SelectJob(val catalog: GameCatalog, val jobId: String) : GameCommand
    data class StartShift(val catalog: GameCatalog, val jobId: String) : GameCommand
    data class MoveBox(val direction: Double) : GameCommand
    data class PlaceBox(val boxId: String, val zone: BoxCategory) : GameCommand
    data object FinishShift : GameCommand
    data class StartActivity(val catalog: GameCatalog?, val request: ActivityRequest) : GameCommand
}
