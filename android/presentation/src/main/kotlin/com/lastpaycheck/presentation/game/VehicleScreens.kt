package com.lastpaycheck.presentation.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.GameSession
import com.lastpaycheck.domain.model.OwnedVehicle
import com.lastpaycheck.domain.model.RaceOutcome
import com.lastpaycheck.domain.model.VehicleAction
import com.lastpaycheck.domain.model.VehicleCatalog
import com.lastpaycheck.domain.model.VehicleDefinition
import com.lastpaycheck.domain.model.VehicleFailure
import com.lastpaycheck.domain.model.VehicleUpgrade
import com.lastpaycheck.presentation.R
import com.lastpaycheck.presentation.ui.components.LpBody
import com.lastpaycheck.presentation.ui.components.LpButton
import com.lastpaycheck.presentation.ui.components.LpButtonStyle
import com.lastpaycheck.presentation.ui.components.LpCaption
import com.lastpaycheck.presentation.ui.theme.LpSpacing
import java.util.UUID
import kotlinx.coroutines.delay

@Composable
private fun VehiclePage(
    title: String,
    tag: String,
    session: GameSession,
    game: MainViewModel,
    onBack: () -> Unit,
    content: @Composable ColumnScope.(VehicleCatalog, Boolean) -> Unit
) {
    val catalog by game.vehicleCatalog.collectAsStateWithLifecycle()
    val busy by game.sectionBusy.collectAsStateWithLifecycle()
    val error by game.vehicleError.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { game.loadVehicles() }
    SectionPage(
        title,
        gameTime(session.clock),
        session,
        onBack,
        { GameBackButton(onBack) },
        screenTag = tag
    ) {
        GamePauseNotice(session, game::togglePause, busy)
        error?.let {
            LpCaption(vehicleError(it), Modifier.testTag("vehicle.error"))
            if (it == GameError.StorageFailure || it == GameError.RevisionConflict) {
                LpButton(stringResource(R.string.catalog_retry), game::save)
            }
        }
        when (val loaded = catalog) {
            null -> GameLoading()
            is DomainResult.Failure -> GameErrorPanel(loaded.error, game::loadVehicles)
            is DomainResult.Success -> content(loaded.value, busy)
        }
    }
}

@Composable
internal fun DealershipScreen(
    session: GameSession,
    game: MainViewModel,
    onCar: (String) -> Unit,
    onGarage: () -> Unit,
    onBack: () -> Unit
) = VehiclePage(
    stringResource(R.string.stores_motors),
    "screen.dealership",
    session,
    game,
    onBack
) { catalog, _ ->
    VehicleArtwork(catalog.vehicles.first().id)
    LpCaption(stringResource(R.string.vehicle_catalog_source, catalog.version))
    catalog.vehicles.forEach { car ->
        SectionCard(vehicleName(car.id)) {
            LpBody(gameMoney(car.priceMinor))
            VehicleStats(car, null)
            LpButton(
                stringResource(R.string.vehicle_details),
                { onCar(car.id) },
                Modifier.fillMaxWidth().testTag("vehicle.details.${car.id}")
            )
        }
    }
    SectionCard(stringResource(R.string.vehicle_compare)) {
        catalog.vehicles.forEach { car ->
            LpCaption(vehicleName(car.id) + " · " + gameMoney(car.priceMinor))
            LpCaption(
                stringResource(
                    R.string.vehicle_specs,
                    car.maxSpeedKmh.toDouble(),
                    car.acceleration,
                    car.handling,
                    car.tankLiters,
                    car.consumption
                )
            )
        }
    }
    LpButton(stringResource(R.string.city_garage), onGarage, Modifier.fillMaxWidth())
}

@Composable
internal fun GarageScreen(
    session: GameSession,
    game: MainViewModel,
    onCar: (String) -> Unit,
    onDealer: () -> Unit,
    onRaces: () -> Unit,
    onCity: () -> Unit,
    onBack: () -> Unit
) = VehiclePage(
    stringResource(R.string.vehicle_garage),
    "screen.garage",
    session,
    game,
    onBack
) { _, _ ->
    val progress = session.vehicles
    VehicleArtwork(progress.activeVehicleId.orEmpty(), garage = true)
    if (progress.garage.isEmpty()) {
        SectionCard(stringResource(R.string.vehicle_empty)) {
            LpCaption(stringResource(R.string.vehicle_empty_hint))
            LpButton(
                stringResource(R.string.stores_open_dealership),
                onDealer,
                Modifier.fillMaxWidth().testTag("garage.dealership")
            )
        }
    } else {
        progress.garage.forEach { car ->
            SectionCard(vehicleName(car.definition.id)) {
                if (progress.activeVehicleId == car.definition.id) {
                    LpCaption(
                        stringResource(R.string.vehicle_active)
                    )
                }
                LpBody(stringResource(R.string.vehicle_condition, car.condition))
                LpButton(
                    stringResource(R.string.vehicle_details),
                    { onCar(car.definition.id) },
                    Modifier.fillMaxWidth().testTag("garage.car.${car.definition.id}")
                )
            }
        }
        LpButton(
            stringResource(R.string.vehicle_races),
            onRaces,
            Modifier.fillMaxWidth().testTag("garage.races")
        )
    }
    LpCaption(stringResource(R.string.vehicle_statistics, progress.completed, progress.wins))
    LpButton(
        stringResource(R.string.stores_open_dealership),
        onDealer,
        Modifier.fillMaxWidth(),
        LpButtonStyle.Secondary
    )
    LpButton(
        stringResource(R.string.vehicle_to_city),
        onCity,
        Modifier.fillMaxWidth(),
        LpButtonStyle.Secondary
    )
}

@Composable
internal fun VehicleDetailScreen(
    session: GameSession,
    game: MainViewModel,
    modelId: String,
    onGarage: () -> Unit,
    onBack: () -> Unit
) = VehiclePage(
    vehicleName(modelId),
    "screen.vehicle.detail",
    session,
    game,
    onBack
) { catalog, busy ->
    val car = catalog.vehicles.find { it.id == modelId }
    val owned = session.vehicles.garage.find { it.definition.id == modelId }
    var pending by remember(modelId) { mutableStateOf<VehicleAction?>(null) }
    var price by remember(modelId) { mutableStateOf(0L) }
    var operationId by remember(modelId) { mutableStateOf("") }
    fun confirm(action: VehicleAction, amount: Long) {
        pending = action
        price = amount
        operationId = UUID.randomUUID().toString()
    }
    val definition = owned?.definition ?: car
    if (definition == null) {
        LpBody(stringResource(R.string.game_item_missing))
    } else {
        VehicleArtwork(modelId, garage = owned != null)
        SectionCard(vehicleName(modelId)) {
            VehicleStats(definition, owned)
            LpBody(gameMoney(definition.priceMinor))
            if (owned == null) {
                val enough = session.player.moneyMinor >= definition.priceMinor
                val blocked = when {
                    session.clock.isPaused -> R.string.progress_unpause_hint
                    session.activeActivity != null ||
                        session.vehicles.race != null -> R.string.game_busy
                    !enough -> R.string.game_insufficient_funds
                    busy -> R.string.vehicle_processing
                    else -> null
                }
                blocked?.let { LpCaption(stringResource(it), Modifier.testTag("vehicle.blocked")) }
                LpButton(
                    stringResource(R.string.vehicle_buy),
                    { confirm(VehicleAction.Buy(modelId), definition.priceMinor) },
                    Modifier.fillMaxWidth().testTag("vehicle.buy.$modelId"),
                    enabled = blocked == null
                )
            } else {
                LpBody(stringResource(R.string.vehicle_condition, owned.condition))
                LpButton(
                    stringResource(
                        if (session.vehicles.activeVehicleId == modelId) {
                            R.string.vehicle_active
                        } else {
                            R.string.vehicle_select
                        }
                    ),
                    {
                        game.vehicleAction(
                            VehicleAction.Select(modelId),
                            UUID.randomUUID().toString()
                        )
                    },
                    Modifier.fillMaxWidth().testTag("vehicle.select"),
                    enabled = !busy && !session.clock.isPaused &&
                        session.vehicles.activeVehicleId != modelId
                )
                LpButton(
                    stringResource(R.string.vehicle_repair, gameMoney(owned.repairPrice)),
                    { confirm(VehicleAction.Repair(modelId), owned.repairPrice) },
                    Modifier.fillMaxWidth(),
                    enabled = !busy && !session.clock.isPaused &&
                        owned.condition < 100 && session.player.moneyMinor >= owned.repairPrice
                )
            }
        }
        owned?.let {
            SectionCard(stringResource(R.string.vehicle_upgrades)) {
                LpCaption(stringResource(R.string.vehicle_upgrade_hint))
                VehicleUpgrade.entries.forEach { part ->
                    val level = it.level(part)
                    LpCaption(
                        stringResource(upgradeLabel(part)) + " · " +
                            stringResource(R.string.vehicle_level, level)
                    )
                    LpButton(
                        stringResource(
                            R.string.vehicle_upgrade_price,
                            gameMoney(it.upgradePrice(part))
                        ),
                        { confirm(VehicleAction.Upgrade(modelId, part), it.upgradePrice(part)) },
                        Modifier.fillMaxWidth().testTag("vehicle.upgrade.${part.name}"),
                        enabled = level < 3 && !busy && !session.clock.isPaused &&
                            session.player.moneyMinor >= it.upgradePrice(part)
                    )
                }
            }
        }
        LpButton(stringResource(R.string.city_garage), onGarage, Modifier.fillMaxWidth())
    }
    pending?.let { action ->
        GameConfirmation(
            stringResource(R.string.vehicle_confirmation),
            vehicleName(modelId) + " · " + gameMoney(price),
            stringResource(R.string.vehicle_confirm),
            { pending = null },
            {
                pending = null
                game.vehicleAction(action, operationId)
            }
        )
    }
}

@Composable
internal fun RaceSelectionScreen(
    session: GameSession,
    game: MainViewModel,
    onPrepare: (String) -> Unit,
    onResult: (String) -> Unit,
    onResume: () -> Unit,
    onBack: () -> Unit
) = VehiclePage(
    stringResource(R.string.vehicle_races),
    "screen.races",
    session,
    game,
    onBack
) { catalog, busy ->
    VehicleArtwork(session.vehicles.activeVehicleId.orEmpty(), garage = true)
    if (session.vehicles.race != null) {
        LpButton(
            stringResource(R.string.race_resume),
            onResume,
            Modifier.fillMaxWidth()
        )
    }
    catalog.races.forEach { race ->
        SectionCard(raceName(race.id)) {
            LpCaption(
                stringResource(
                    R.string.race_track_rules,
                    race.lengthMeters,
                    race.speedLimitKmh,
                    race.collisionLimit
                )
            )
            LpCaption(stringResource(R.string.race_estimate, raceEstimate(race.id)))
            LpCaption(
                stringResource(
                    R.string.race_price,
                    gameMoney(race.entryFeeMinor),
                    gameMoney(race.rewardMinor)
                )
            )
            LpCaption(stringResource(R.string.race_requirement, race.requiredCondition))
            LpCaption(stringResource(R.string.race_required_tokens, race.requiredPickups))
            if (session.vehicles.wins < race.requiredWins) {
                LpCaption(
                    stringResource(
                        R.string.race_locked,
                        race.requiredWins,
                        session.vehicles.wins
                    )
                )
            }
            if (session.vehicles.activeVehicle == null) {
                LpCaption(
                    stringResource(R.string.vehicle_no_active)
                )
            }
            LpButton(
                stringResource(R.string.race_prepare),
                { onPrepare(race.id) },
                Modifier.fillMaxWidth().testTag("race.prepare.${race.id}"),
                enabled = !busy && session.vehicles.activeVehicle != null &&
                    session.vehicles.race == null && session.vehicles.wins >= race.requiredWins
            )
        }
    }
    session.vehicles.history.reversed().take(10).forEach { result ->
        LpButton(
            stringResource(outcomeLabel(result.outcome)) + " · " + gameMoney(result.rewardMinor),
            { onResult(result.raceId) },
            Modifier.fillMaxWidth(),
            LpButtonStyle.Secondary
        )
    }
}

@Composable
internal fun RacePreparationScreen(
    session: GameSession,
    game: MainViewModel,
    eventId: String,
    onStart: () -> Unit,
    onBack: () -> Unit
) = VehiclePage(
    stringResource(R.string.race_prepare),
    "screen.race.prepare",
    session,
    game,
    onBack
) { catalog, busy ->
    val race = catalog.races.find { it.id == eventId }
    val car = session.vehicles.activeVehicle
    VehicleArtwork(car?.definition?.id.orEmpty(), garage = true)
    if (race != null && car != null) {
        SectionCard(raceName(race.id)) {
            VehicleStats(car.definition, car)
            LpCaption(stringResource(R.string.vehicle_condition, car.condition))
            LpCaption(
                stringResource(
                    R.string.race_track_rules,
                    race.lengthMeters,
                    race.speedLimitKmh,
                    race.collisionLimit
                )
            )
            LpCaption(stringResource(R.string.race_controls_short))
            LpCaption(stringResource(R.string.race_bonus_hint))
            LpCaption(stringResource(R.string.race_required_tokens, race.requiredPickups))
            LpCaption(
                stringResource(
                    R.string.race_price,
                    gameMoney(race.entryFeeMinor),
                    gameMoney(race.rewardMinor)
                )
            )
            LpButton(
                stringResource(R.string.race_start),
                {
                    game.vehicleAction(
                        VehicleAction.StartRace(eventId),
                        UUID.randomUUID().toString(),
                        onStart
                    )
                },
                Modifier.fillMaxWidth().testTag("race.start"),
                enabled = !busy && !session.clock.isPaused &&
                    car.condition >= race.requiredCondition &&
                    session.vehicles.wins >= race.requiredWins &&
                    session.player.moneyMinor >= race.entryFeeMinor
            )
        }
    } else {
        LpBody(stringResource(R.string.vehicle_no_active))
    }
}

@Composable
internal fun RaceScreen(
    session: GameSession,
    game: MainViewModel,
    onResult: (String) -> Unit,
    onGarage: () -> Unit
) {
    val race = session.vehicles.race
    val busy by game.sectionBusy.collectAsStateWithLifecycle()
    val error by game.vehicleError.collectAsStateWithLifecycle()
    var abandoning by remember { mutableStateOf(false) }
    var lastCollected by remember(race?.id) { mutableStateOf(race?.collectedPickupIds?.size ?: 0) }
    var lastCollisions by remember(race?.id) { mutableStateOf(race?.collisions ?: 0) }
    var notice by remember(race?.id) { mutableStateOf<Int?>(null) }
    LaunchedEffect(race?.collectedPickupIds?.size, race?.collisions) {
        race?.let {
            notice = when {
                it.collisions > lastCollisions -> R.string.race_combo_lost
                it.collectedPickupIds.size > lastCollected -> R.string.race_collected
                else -> notice
            }
            lastCollected = it.collectedPickupIds.size
            lastCollisions = it.collisions
            delay(1500)
            notice = null
        }
    }
    val back = { if (race == null) onGarage() else abandoning = true }
    BackHandler(onBack = back)
    LaunchedEffect(race?.id) {
        if (race == null) session.vehicles.history.lastOrNull()?.let { onResult(it.raceId) }
    }
    SectionPage(
        raceName(race?.definition?.id.orEmpty()),
        gameTime(session.clock),
        session,
        back,
        footer = {
            race?.let {
                val enabled = !session.clock.isPaused && !busy
                val steering = enabled && it.elapsedMillis - it.lastSteerMillis >=
                    (session.vehicles.activeVehicle?.steeringCooldownMillis ?: 1000)
                fun drive(delta: Int, brake: Boolean) {
                    game.vehicleAction(
                        VehicleAction.Drive(it.id, delta, brake),
                        UUID.randomUUID().toString()
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(LpSpacing.sm)) {
                    LpButton(
                        stringResource(R.string.race_left),
                        { drive(-1, it.braking) },
                        Modifier.weight(1f).testTag("race.left"),
                        enabled = steering && it.lane > 0
                    )
                    LpButton(
                        stringResource(if (it.braking) R.string.race_gas else R.string.race_brake),
                        { drive(0, !it.braking) },
                        Modifier.weight(1f).testTag("race.brake"),
                        enabled = enabled
                    )
                    LpButton(
                        stringResource(R.string.race_right),
                        { drive(1, it.braking) },
                        Modifier.weight(1f).testTag("race.right"),
                        enabled = steering && it.lane < 2
                    )
                }
            }
        },
        screenTag = "screen.race"
    ) {
        GamePauseNotice(session, game::togglePause, busy)
        error?.let {
            LpCaption(vehicleError(it))
            if (it == GameError.StorageFailure || it == GameError.RevisionConflict) {
                LpButton(stringResource(R.string.catalog_retry), game::save)
            }
        }
        race?.let {
            LpBody(
                stringResource(
                    R.string.race_progress,
                    it.distanceMeters.toInt(),
                    it.definition.lengthMeters,
                    ((it.definition.limitSeconds * 1000 - it.elapsedMillis) / 1000).toInt()
                ),
                Modifier.testTag("race.progress")
            )
            LpCaption(
                stringResource(
                    R.string.race_metrics,
                    it.speedMps * 3.6,
                    it.collisions,
                    session.vehicles.activeVehicle?.condition ?: 0,
                    it.definition.collisionLimit
                ),
                Modifier.testTag("race.metrics")
            )
            LpBody(
                stringResource(
                    R.string.race_combo,
                    gameAmount(it.multiplierPercent.toLong()),
                    it.streak,
                    gameMoney(it.pendingBonusMinor)
                ),
                Modifier.testTag("race.combo")
            )
            notice?.let { message ->
                LpCaption(
                    stringResource(message),
                    Modifier.testTag("race.feedback")
                )
            }
            RaceArtwork(it)
            LpCaption(stringResource(R.string.race_controls_short))
            LpButton(
                stringResource(
                    if (session.clock.isPaused) R.string.game_unpause else R.string.game_pause
                ),
                game::togglePause,
                Modifier.testTag("race.pause"),
                LpButtonStyle.Secondary,
                !busy
            )
        }
    }
    if (abandoning && race != null) {
        GameConfirmation(
            stringResource(R.string.race_abandon),
            stringResource(R.string.race_abandon_hint),
            stringResource(R.string.race_abandon),
            { abandoning = false },
            {
                abandoning = false
                game.vehicleAction(VehicleAction.Abandon(race.id), UUID.randomUUID().toString())
            }
        )
    }
}

@Composable
internal fun RaceResultScreen(session: GameSession, raceId: String, onGarage: () -> Unit) {
    val result = session.vehicles.history.find { it.raceId == raceId }
    SectionPage(
        stringResource(R.string.race_result),
        "",
        session,
        onGarage,
        { LpButton(stringResource(R.string.city_garage), onGarage, Modifier.fillMaxWidth()) },
        screenTag = "screen.race.result"
    ) {
        result?.let {
            VehicleArtwork(it.vehicleId, garage = true)
            SectionCard(stringResource(outcomeLabel(it.outcome))) {
                LpBody(gameMoney(it.rewardMinor), Modifier.testTag("race.reward"))
                LpCaption(stringResource(R.string.race_base_reward, gameMoney(it.baseRewardMinor)))
                LpCaption(
                    stringResource(
                        R.string.race_bonus_reward,
                        gameMoney(it.bonusRewardMinor)
                    )
                )
                LpCaption(stringResource(R.string.race_token_result, it.collected, it.finalStreak))
                LpCaption(
                    stringResource(R.string.race_summary, it.elapsedMillis / 1000.0, it.collisions)
                )
                LpCaption(stringResource(R.string.race_saved))
            }
        } ?: LpBody(stringResource(R.string.game_item_missing))
    }
}

@Composable
private fun VehicleStats(definition: VehicleDefinition, owned: OwnedVehicle?) {
    LpCaption(
        stringResource(
            R.string.vehicle_specs,
            owned?.maxSpeedKmh ?: definition.maxSpeedKmh.toDouble(),
            owned?.acceleration ?: definition.acceleration,
            definition.handling,
            definition.tankLiters,
            definition.consumption
        )
    )
    owned?.let {
        LpCaption(
            stringResource(
                R.string.vehicle_effective,
                it.steeringCooldownMillis,
                it.braking,
                it.level(VehicleUpgrade.RELIABILITY)
            )
        )
    }
}

@Composable
internal fun vehicleName(id: String) = stringResource(
    when (id) {
        "city-hatch" -> R.string.vehicle_hatch
        "family-wagon" -> R.string.vehicle_wagon
        "sport-coupe" -> R.string.vehicle_coupe
        else -> R.string.game_item_missing
    }
)

private fun upgradeLabel(part: VehicleUpgrade) = when (part) {
    VehicleUpgrade.ENGINE -> R.string.vehicle_engine
    VehicleUpgrade.HANDLING -> R.string.vehicle_handling
    VehicleUpgrade.BRAKES -> R.string.vehicle_brakes
    VehicleUpgrade.RELIABILITY -> R.string.vehicle_reliability
}

private fun outcomeLabel(outcome: RaceOutcome) = when (outcome) {
    RaceOutcome.WON -> R.string.race_won
    RaceOutcome.CRASHED -> R.string.race_crashed
    RaceOutcome.TIMEOUT -> R.string.race_timeout
    RaceOutcome.ABANDONED -> R.string.race_abandoned
    RaceOutcome.HEALTH -> R.string.race_health
    RaceOutcome.TOKENS_MISSED -> R.string.race_tokens_missed
}

@Composable
internal fun vehicleError(error: GameError): String = stringResource(
    when (error) {
        is GameError.InsufficientFunds -> R.string.game_insufficient_funds
        GameError.Paused -> R.string.progress_unpause_hint
        GameError.Busy -> R.string.game_busy
        is GameError.Vehicle -> when (error.reason) {
            VehicleFailure.ALREADY_OWNED -> R.string.vehicle_owned
            VehicleFailure.NOT_OWNED -> R.string.vehicle_not_owned
            VehicleFailure.NO_ACTIVE_CAR -> R.string.vehicle_no_active
            VehicleFailure.MAX_UPGRADE -> R.string.vehicle_max_upgrade
            VehicleFailure.DAMAGED -> R.string.vehicle_damaged
            VehicleFailure.NO_RACE -> R.string.race_missing
            VehicleFailure.STEERING_WAIT -> R.string.race_wait
            VehicleFailure.RACE_LOCKED -> R.string.vehicle_race_locked
        }
        else -> R.string.game_storage_error
    }
)

@Composable
private fun raceName(id: String) = stringResource(
    when (id) {
        "city-circuit" -> R.string.race_city
        "industrial-run" -> R.string.race_industrial
        else -> R.string.race_intro
    }
)

@Composable
private fun raceEstimate(id: String) = stringResource(
    when (id) {
        "city-circuit" -> R.string.race_time_city
        "industrial-run" -> R.string.race_time_expert
        else -> R.string.race_time_intro
    }
)
