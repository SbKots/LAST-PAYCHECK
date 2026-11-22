package com.lastpaycheck.data.local

import com.lastpaycheck.domain.model.Activity
import com.lastpaycheck.domain.model.BoxCategory
import com.lastpaycheck.domain.model.CatalogSource
import com.lastpaycheck.domain.model.Course
import com.lastpaycheck.domain.model.EconomicScenario
import com.lastpaycheck.domain.model.FinishReason
import com.lastpaycheck.domain.model.GameClock
import com.lastpaycheck.domain.model.GameEffect
import com.lastpaycheck.domain.model.GameEvent
import com.lastpaycheck.domain.model.GameSession
import com.lastpaycheck.domain.model.Inventory
import com.lastpaycheck.domain.model.Job
import com.lastpaycheck.domain.model.Needs
import com.lastpaycheck.domain.model.PauseReason
import com.lastpaycheck.domain.model.PlayerState
import com.lastpaycheck.domain.model.Product
import com.lastpaycheck.domain.model.SessionStatus
import com.lastpaycheck.domain.model.ShiftResult
import com.lastpaycheck.domain.model.TimedActivity
import com.lastpaycheck.domain.model.WarehouseBox
import com.lastpaycheck.domain.model.WorkShift
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class StoredNeeds(
    val satietyUnits: Long,
    val energyUnits: Long,
    val moodUnits: Long,
    val healthUnits: Long
)

private fun Needs.toStored() = StoredNeeds(
    satietyUnits = satietyUnits,
    energyUnits = energyUnits,
    moodUnits = moodUnits,
    healthUnits = healthUnits
)

private fun StoredNeeds.toDomain() = Needs(
    satietyUnits = satietyUnits,
    energyUnits = energyUnits,
    moodUnits = moodUnits,
    healthUnits = healthUnits
)

@Serializable
internal data class StoredPlayerState(
    val moneyMinor: Long,
    val needs: StoredNeeds,
    val selectedJobId: String,
    val rentDebtMinor: Long,
    val experience: Int,
    val taskRemainder: Int,
    val name: String = ""
)

private fun PlayerState.toStored() = StoredPlayerState(
    moneyMinor = moneyMinor,
    needs = needs.toStored(),
    selectedJobId = selectedJobId,
    rentDebtMinor = rentDebtMinor,
    experience = experience,
    taskRemainder = taskRemainder,
    name = name
)

private fun StoredPlayerState.toDomain() = PlayerState(
    moneyMinor = moneyMinor,
    needs = needs.toDomain(),
    selectedJobId = selectedJobId,
    rentDebtMinor = rentDebtMinor,
    experience = experience,
    taskRemainder = taskRemainder,
    name = name
)

@Serializable
internal data class StoredEconomicScenario(
    val id: String,
    val version: String,
    val country: String,
    val year: Int,
    val annualInflationPercent: String,
    val source: CatalogSource
)

private fun EconomicScenario.toStored() = StoredEconomicScenario(
    id = id,
    version = version,
    country = country,
    year = year,
    annualInflationPercent = annualInflationPercent.toString(),
    source = source
)

private fun StoredEconomicScenario.toDomain() = EconomicScenario(
    id = id,
    version = version,
    country = country,
    year = year,
    annualInflationPercent = annualInflationPercent.toBigDecimal(),
    source = source
)

@Serializable
internal data class StoredGameClock(
    val gameMillis: Long,
    val pauseReasons: Set<PauseReason>,
    val fractionalRemainder: Int
)

private fun GameClock.toStored() = StoredGameClock(
    gameMillis = gameMillis,
    pauseReasons = pauseReasons,
    fractionalRemainder = fractionalRemainder
)

private fun StoredGameClock.toDomain() = GameClock(
    gameMillis = gameMillis,
    pauseReasons = pauseReasons,
    fractionalRemainder = fractionalRemainder
)

@Serializable
internal data class StoredGameEvent(
    val definitionId: String,
    val description: String,
    val occurredAt: Long,
    val moneyLostMinor: Long,
    val healthLost: Int,
    val durationGameMillis: Long,
    val extraWorkEnergyPerHour: Int
)

private fun GameEvent.toStored() = StoredGameEvent(
    definitionId = definitionId,
    description = description,
    occurredAt = occurredAt,
    moneyLostMinor = moneyLostMinor,
    healthLost = healthLost,
    durationGameMillis = durationGameMillis,
    extraWorkEnergyPerHour = extraWorkEnergyPerHour
)

private fun StoredGameEvent.toDomain() = GameEvent(
    definitionId = definitionId,
    description = description,
    occurredAt = occurredAt,
    moneyLostMinor = moneyLostMinor,
    healthLost = healthLost,
    durationGameMillis = durationGameMillis,
    extraWorkEnergyPerHour = extraWorkEnergyPerHour
)

@Serializable
internal data class StoredShiftResult(
    val correct: Int,
    val errors: Int,
    val workedGameMillis: Long,
    val wageMinor: Long,
    val debtPaidMinor: Long,
    val experienceGained: Int
)

private fun ShiftResult.toStored() = StoredShiftResult(
    correct = correct,
    errors = errors,
    workedGameMillis = workedGameMillis,
    wageMinor = wageMinor,
    debtPaidMinor = debtPaidMinor,
    experienceGained = experienceGained
)

private fun StoredShiftResult.toDomain() = ShiftResult(
    correct = correct,
    errors = errors,
    workedGameMillis = workedGameMillis,
    wageMinor = wageMinor,
    debtPaidMinor = debtPaidMinor,
    experienceGained = experienceGained
)

@Serializable
internal data class StoredJob(
    val id: String,
    val title: String,
    val requiredLevel: Int,
    val durationHours: Int,
    val basePayMinor: Long,
    val bonusCapMinor: Long,
    val plan: Int,
    val extraEnergyPerHour: Int
)

private fun Job.toStored() = StoredJob(
    id = id,
    title = title,
    requiredLevel = requiredLevel,
    durationHours = durationHours,
    basePayMinor = basePayMinor,
    bonusCapMinor = bonusCapMinor,
    plan = plan,
    extraEnergyPerHour = extraEnergyPerHour
)

private fun StoredJob.toDomain() = Job(
    id = id,
    title = title,
    requiredLevel = requiredLevel,
    durationHours = durationHours,
    basePayMinor = basePayMinor,
    bonusCapMinor = bonusCapMinor,
    plan = plan,
    extraEnergyPerHour = extraEnergyPerHour
)

@Serializable
internal data class StoredProduct(
    val id: String,
    val basePriceMinor: Long,
    val satietyGain: Int,
    val durationMinutes: Int
)

private fun Product.toStored() = StoredProduct(
    id = id,
    basePriceMinor = basePriceMinor,
    satietyGain = satietyGain,
    durationMinutes = durationMinutes
)

private fun StoredProduct.toDomain() = Product(
    id = id,
    basePriceMinor = basePriceMinor,
    satietyGain = satietyGain,
    durationMinutes = durationMinutes
)

@Serializable
internal data class StoredCourse(
    val id: String,
    val priceMinor: Long,
    val durationHours: Int,
    val experienceGain: Int
)

private fun Course.toStored() = StoredCourse(
    id = id,
    priceMinor = priceMinor,
    durationHours = durationHours,
    experienceGain = experienceGain
)

private fun StoredCourse.toDomain() = Course(
    id = id,
    priceMinor = priceMinor,
    durationHours = durationHours,
    experienceGain = experienceGain
)

@Serializable
internal data class StoredWarehouseBox(
    val id: String,
    val category: BoxCategory
)

private fun WarehouseBox.toStored() = StoredWarehouseBox(
    id = id,
    category = category
)

private fun StoredWarehouseBox.toDomain() = WarehouseBox(
    id = id,
    category = category
)

@Serializable
internal data class StoredGameSession(
    val id: String,
    val catalogVersion: String,
    val player: StoredPlayerState,
    val scenario: StoredEconomicScenario,
    val dailyRentMinor: Long,
    val revision: Long,
    val clock: StoredGameClock,
    val inventory: Map<String, Int>,
    val activeActivity: StoredActivity?,
    val status: SessionStatus,
    val finishReason: FinishReason?,
    val lastRentDay: Long,
    val workMillis: Long,
    val randomState: Long,
    val events: List<StoredGameEvent>,
    val lastShiftResult: StoredShiftResult?
)

internal fun GameSession.toStored() = StoredGameSession(
    id = id,
    catalogVersion = catalogVersion,
    player = player.toStored(),
    scenario = scenario.toStored(),
    dailyRentMinor = dailyRentMinor,
    revision = revision,
    clock = clock.toStored(),
    inventory = inventory.quantities,
    activeActivity = activeActivity?.toStored(),
    status = status,
    finishReason = finishReason,
    lastRentDay = lastRentDay,
    workMillis = workMillis,
    randomState = randomState,
    events = events.map(GameEvent::toStored),
    lastShiftResult = lastShiftResult?.toStored()
)

internal fun StoredGameSession.toDomain() = GameSession(
    id = id,
    catalogVersion = catalogVersion,
    player = player.toDomain(),
    scenario = scenario.toDomain(),
    dailyRentMinor = dailyRentMinor,
    revision = revision,
    clock = clock.toDomain(),
    inventory = Inventory(inventory),
    activeActivity = activeActivity?.toDomain(),
    status = status,
    finishReason = finishReason,
    lastRentDay = lastRentDay,
    workMillis = workMillis,
    randomState = randomState,
    events = events.map(StoredGameEvent::toDomain),
    lastShiftResult = lastShiftResult?.toDomain()
)

@Serializable
internal sealed interface StoredActivity {
    @Serializable
    @SerialName("work")
    data class Work(
        val id: String,
        val job: StoredJob,
        val selectedBox: StoredWarehouseBox,
        val remainingGameMillis: Long,
        val correct: Int,
        val errors: Int,
        val processedBoxIds: Set<String>,
        val boxPosition: Double
    ) : StoredActivity

    @Serializable
    @SerialName("food")
    data class Food(
        val id: String,
        val product: StoredProduct,
        val remainingGameMillis: Long
    ) : StoredActivity

    @Serializable
    @SerialName("sleep")
    data class Sleep(val id: String, val remainingGameMillis: Long) : StoredActivity

    @Serializable
    @SerialName("rest")
    data class Rest(val id: String, val remainingGameMillis: Long) : StoredActivity

    @Serializable
    @SerialName("course")
    data class Course(
        val id: String,
        val course: StoredCourse,
        val remainingGameMillis: Long
    ) : StoredActivity
}

private fun Activity.toStored(): StoredActivity = when (this) {
    is WorkShift -> StoredActivity.Work(
        id,
        job.toStored(),
        selectedBox.toStored(),
        remainingGameMillis,
        correct,
        errors,
        processedBoxIds,
        boxPosition
    )
    is TimedActivity.Food -> StoredActivity.Food(id, product.toStored(), remainingGameMillis)
    is TimedActivity.Sleep -> StoredActivity.Sleep(id, remainingGameMillis)
    is TimedActivity.Rest -> StoredActivity.Rest(id, remainingGameMillis)
    is TimedActivity.Course -> StoredActivity.Course(id, course.toStored(), remainingGameMillis)
}

private fun StoredActivity.toDomain(): Activity = when (this) {
    is StoredActivity.Work -> WorkShift(
        id,
        job.toDomain(),
        selectedBox.toDomain(),
        remainingGameMillis,
        correct,
        errors,
        processedBoxIds,
        boxPosition
    )
    is StoredActivity.Food -> TimedActivity.Food(id, product.toDomain(), remainingGameMillis)
    is StoredActivity.Sleep -> TimedActivity.Sleep(id, remainingGameMillis)
    is StoredActivity.Rest -> TimedActivity.Rest(id, remainingGameMillis)
    is StoredActivity.Course -> TimedActivity.Course(id, course.toDomain(), remainingGameMillis)
}

@Serializable
internal sealed interface StoredEffect {
    @Serializable
    @SerialName("applied")
    data object Applied : StoredEffect

    @Serializable
    @SerialName("purchase")
    data class Purchase(
        val productId: String,
        val quantity: Int,
        val costMinor: Long
    ) : StoredEffect

    @Serializable
    @SerialName("placement")
    data class Placement(val correct: Boolean) : StoredEffect

    @Serializable
    @SerialName("shift")
    data class Shift(val result: StoredShiftResult) : StoredEffect
}

internal fun GameEffect.toStored(): StoredEffect = when (this) {
    GameEffect.Applied -> StoredEffect.Applied
    is GameEffect.Purchase -> StoredEffect.Purchase(productId, quantity, costMinor)
    is GameEffect.Placement -> StoredEffect.Placement(correct)
    is GameEffect.ShiftFinished -> StoredEffect.Shift(result.toStored())
}

internal fun StoredEffect.toDomain(): GameEffect = when (this) {
    StoredEffect.Applied -> GameEffect.Applied
    is StoredEffect.Purchase -> GameEffect.Purchase(productId, quantity, costMinor)
    is StoredEffect.Placement -> GameEffect.Placement(correct)
    is StoredEffect.Shift -> GameEffect.ShiftFinished(result.toDomain())
}
