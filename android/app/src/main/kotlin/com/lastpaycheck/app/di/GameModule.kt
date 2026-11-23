package com.lastpaycheck.app.di

import android.os.SystemClock
import com.lastpaycheck.data.catalog.DemoVehicleCatalogRepository
import com.lastpaycheck.data.catalog.TrainingGameCatalogRepository
import com.lastpaycheck.domain.engine.GameEngine
import com.lastpaycheck.domain.engine.GameSessionCoordinator
import com.lastpaycheck.domain.repository.ActiveGameClock
import com.lastpaycheck.domain.repository.CatalogRepository
import com.lastpaycheck.domain.repository.GameSessionRepository
import com.lastpaycheck.domain.usecase.AbandonRaceUseCase
import com.lastpaycheck.domain.usecase.BuyProductUseCase
import com.lastpaycheck.domain.usecase.BuyVehicleUseCase
import com.lastpaycheck.domain.usecase.ContinueGameUseCase
import com.lastpaycheck.domain.usecase.DriveRaceUseCase
import com.lastpaycheck.domain.usecase.FinishShiftUseCase
import com.lastpaycheck.domain.usecase.GetFinancialOverviewUseCase
import com.lastpaycheck.domain.usecase.GetFoodPreviewUseCase
import com.lastpaycheck.domain.usecase.GetPlayerStatusUseCase
import com.lastpaycheck.domain.usecase.GetVehicleCatalogUseCase
import com.lastpaycheck.domain.usecase.PlaceBoxUseCase
import com.lastpaycheck.domain.usecase.PreviewNewGameUseCase
import com.lastpaycheck.domain.usecase.QuoteProductUseCase
import com.lastpaycheck.domain.usecase.RenameCharacterUseCase
import com.lastpaycheck.domain.usecase.RepairVehicleUseCase
import com.lastpaycheck.domain.usecase.SaveProgressUseCase
import com.lastpaycheck.domain.usecase.SelectActiveVehicleUseCase
import com.lastpaycheck.domain.usecase.SetPauseUseCase
import com.lastpaycheck.domain.usecase.StartActivityUseCase
import com.lastpaycheck.domain.usecase.StartNewGameUseCase
import com.lastpaycheck.domain.usecase.StartRaceUseCase
import com.lastpaycheck.domain.usecase.StartShiftUseCase
import com.lastpaycheck.domain.usecase.UpdateGameTimeUseCase
import com.lastpaycheck.domain.usecase.UpgradeVehicleUseCase
import com.lastpaycheck.presentation.game.SectionActions
import com.lastpaycheck.presentation.game.VehicleActions
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped

@Module
@InstallIn(ViewModelComponent::class)
object GameModule {
    @Provides
    fun vehicleActions(coordinator: GameSessionCoordinator): VehicleActions {
        val catalog = GetVehicleCatalogUseCase(
            DemoVehicleCatalogRepository()
        )
        return VehicleActions(
            catalog,
            BuyVehicleUseCase(catalog, coordinator),
            SelectActiveVehicleUseCase(coordinator),
            UpgradeVehicleUseCase(coordinator),
            RepairVehicleUseCase(coordinator),
            StartRaceUseCase(catalog, coordinator),
            DriveRaceUseCase(coordinator),
            AbandonRaceUseCase(coordinator)
        )
    }

    @Provides
    fun foodPreview() = GetFoodPreviewUseCase()

    @Provides
    fun rename(coordinator: GameSessionCoordinator) = RenameCharacterUseCase(coordinator)

    @Provides
    fun finances(repository: GameSessionRepository) = GetFinancialOverviewUseCase(repository)

    @Provides
    fun catalog(): CatalogRepository = TrainingGameCatalogRepository()

    @Provides
    @ViewModelScoped
    fun clock() = ActiveGameClock(SystemClock::elapsedRealtime)

    @Provides
    @ViewModelScoped
    fun coordinator(repository: GameSessionRepository, clock: ActiveGameClock) =
        GameSessionCoordinator(GameEngine(), repository, clock)

    @Provides
    fun preview(catalog: CatalogRepository) = PreviewNewGameUseCase(catalog)

    @Provides
    fun start(catalog: CatalogRepository, coordinator: GameSessionCoordinator) =
        StartNewGameUseCase(catalog, coordinator)

    @Provides
    fun continueGame(coordinator: GameSessionCoordinator) = ContinueGameUseCase(coordinator)

    @Provides
    fun status(coordinator: GameSessionCoordinator) = GetPlayerStatusUseCase(coordinator)

    @Provides
    fun activity(catalog: CatalogRepository, coordinator: GameSessionCoordinator) =
        StartActivityUseCase(catalog, coordinator)

    @Provides
    fun tick(coordinator: GameSessionCoordinator) = UpdateGameTimeUseCase(coordinator)

    @Provides
    fun pause(coordinator: GameSessionCoordinator) = SetPauseUseCase(coordinator)

    @Provides
    fun save(coordinator: GameSessionCoordinator) = SaveProgressUseCase(coordinator)

    @Provides
    fun gameplay(catalog: CatalogRepository, coordinator: GameSessionCoordinator) = SectionActions(
        StartShiftUseCase(catalog, coordinator),
        PlaceBoxUseCase(coordinator),
        FinishShiftUseCase(coordinator),
        QuoteProductUseCase(catalog, coordinator),
        BuyProductUseCase(catalog, coordinator)
    )
}
