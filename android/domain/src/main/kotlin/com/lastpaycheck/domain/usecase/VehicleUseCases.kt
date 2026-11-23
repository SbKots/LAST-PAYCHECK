package com.lastpaycheck.domain.usecase

import com.lastpaycheck.domain.engine.GameCommand
import com.lastpaycheck.domain.engine.GameSessionCoordinator
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.OperationKey
import com.lastpaycheck.domain.model.VehicleAction
import com.lastpaycheck.domain.model.VehicleUpgrade
import com.lastpaycheck.domain.model.failure
import com.lastpaycheck.domain.model.flatMap
import com.lastpaycheck.domain.model.map
import com.lastpaycheck.domain.repository.VehicleCatalogRepository

class GetVehicleCatalogUseCase(private val repository: VehicleCatalogRepository) {
    suspend operator fun invoke() = repository.get().flatMap {
        if (it.isValid()) DomainResult.Success(it) else failure(GameError.InvalidCatalog)
    }
}

class BuyVehicleUseCase(
    private val catalog: GetVehicleCatalogUseCase,
    private val coordinator: GameSessionCoordinator
) {
    suspend operator fun invoke(key: OperationKey, modelId: String) = coordinator.submit(key) {
        if (modelId.isBlank()) {
            failure(GameError.InvalidInput("modelId"))
        } else {
            catalog().map { GameCommand.Vehicle(VehicleAction.Buy(modelId), it) }
        }
    }
}

class SelectActiveVehicleUseCase(private val coordinator: GameSessionCoordinator) {
    suspend operator fun invoke(key: OperationKey, modelId: String) = coordinator.submit(key) {
        if (modelId.isBlank()) {
            failure(GameError.InvalidInput("modelId"))
        } else {
            DomainResult.Success(GameCommand.Vehicle(VehicleAction.Select(modelId)))
        }
    }
}

class UpgradeVehicleUseCase(private val coordinator: GameSessionCoordinator) {
    suspend operator fun invoke(key: OperationKey, modelId: String, part: VehicleUpgrade) =
        coordinator.submit(key) {
            if (modelId.isBlank()) {
                failure(GameError.InvalidInput("modelId"))
            } else {
                DomainResult.Success(GameCommand.Vehicle(VehicleAction.Upgrade(modelId, part)))
            }
        }
}

class RepairVehicleUseCase(private val coordinator: GameSessionCoordinator) {
    suspend operator fun invoke(key: OperationKey, modelId: String) = coordinator.submit(key) {
        if (modelId.isBlank()) {
            failure(GameError.InvalidInput("modelId"))
        } else {
            DomainResult.Success(GameCommand.Vehicle(VehicleAction.Repair(modelId)))
        }
    }
}

class StartRaceUseCase(
    private val catalog: GetVehicleCatalogUseCase,
    private val coordinator: GameSessionCoordinator
) {
    suspend operator fun invoke(key: OperationKey, eventId: String) = coordinator.submit(key) {
        if (eventId.isBlank()) {
            failure(GameError.InvalidInput("eventId"))
        } else {
            catalog().map { GameCommand.Vehicle(VehicleAction.StartRace(eventId), it) }
        }
    }
}

class DriveRaceUseCase(private val coordinator: GameSessionCoordinator) {
    suspend operator fun invoke(
        key: OperationKey,
        raceId: String,
        laneDelta: Int,
        braking: Boolean
    ) = coordinator.submit(key) {
        if (raceId.isBlank() || laneDelta !in -1..1) {
            failure(GameError.InvalidInput("raceInput"))
        } else {
            DomainResult.Success(
                GameCommand.Vehicle(VehicleAction.Drive(raceId, laneDelta, braking))
            )
        }
    }
}

class AbandonRaceUseCase(private val coordinator: GameSessionCoordinator) {
    suspend operator fun invoke(key: OperationKey, raceId: String) = coordinator.submit(key) {
        if (raceId.isBlank()) {
            failure(GameError.InvalidInput("raceId"))
        } else {
            DomainResult.Success(GameCommand.Vehicle(VehicleAction.Abandon(raceId)))
        }
    }
}
