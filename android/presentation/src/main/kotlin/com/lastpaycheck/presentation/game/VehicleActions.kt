package com.lastpaycheck.presentation.game

import com.lastpaycheck.domain.model.OperationKey
import com.lastpaycheck.domain.model.VehicleAction
import com.lastpaycheck.domain.usecase.AbandonRaceUseCase
import com.lastpaycheck.domain.usecase.BuyVehicleUseCase
import com.lastpaycheck.domain.usecase.DriveRaceUseCase
import com.lastpaycheck.domain.usecase.GetVehicleCatalogUseCase
import com.lastpaycheck.domain.usecase.RepairVehicleUseCase
import com.lastpaycheck.domain.usecase.SelectActiveVehicleUseCase
import com.lastpaycheck.domain.usecase.StartRaceUseCase
import com.lastpaycheck.domain.usecase.UpgradeVehicleUseCase

class VehicleActions(
    val catalog: GetVehicleCatalogUseCase,
    private val buy: BuyVehicleUseCase,
    private val select: SelectActiveVehicleUseCase,
    private val upgrade: UpgradeVehicleUseCase,
    private val repair: RepairVehicleUseCase,
    private val start: StartRaceUseCase,
    private val drive: DriveRaceUseCase,
    private val abandon: AbandonRaceUseCase
) {
    suspend fun execute(key: OperationKey, action: VehicleAction) = when (action) {
        is VehicleAction.Buy -> buy(key, action.modelId)
        is VehicleAction.Select -> select(key, action.modelId)
        is VehicleAction.Upgrade -> upgrade(key, action.modelId, action.part)
        is VehicleAction.Repair -> repair(key, action.modelId)
        is VehicleAction.StartRace -> start(key, action.eventId)
        is VehicleAction.Drive -> drive(key, action.raceId, action.laneDelta, action.braking)
        is VehicleAction.Abandon -> abandon(key, action.raceId)
    }
}
