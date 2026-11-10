package com.lastpaycheck.domain.model

const val GAME_MINUTE_MILLIS = 60_000L
const val GAME_HOUR_MILLIS = 60 * GAME_MINUTE_MILLIS
const val GAME_DAY_MILLIS = 24 * GAME_HOUR_MILLIS

/** One resource point is stored as GAME_HOUR_MILLIS units for exact hourly changes. */
data class Needs(
    val satietyUnits: Long,
    val energyUnits: Long,
    val moodUnits: Long,
    val healthUnits: Long
) {
    val satiety: Double get() = satietyUnits.toDouble() / GAME_HOUR_MILLIS
    val energy: Double get() = energyUnits.toDouble() / GAME_HOUR_MILLIS
    val mood: Double get() = moodUnits.toDouble() / GAME_HOUR_MILLIS
    val health: Double get() = healthUnits.toDouble() / GAME_HOUR_MILLIS

    internal fun isValid() = satietyUnits in 0..MAX_UNITS && energyUnits in 0..MAX_UNITS &&
        moodUnits in 0..MAX_UNITS && healthUnits in 0..MAX_UNITS

    companion object {
        internal const val MAX_UNITS = 100 * GAME_HOUR_MILLIS

        fun of(satiety: Int, energy: Int, mood: Int, health: Int) = Needs(
            satiety * GAME_HOUR_MILLIS,
            energy * GAME_HOUR_MILLIS,
            mood * GAME_HOUR_MILLIS,
            health * GAME_HOUR_MILLIS
        )
    }
}

data class PlayerState(
    val moneyMinor: Long,
    val needs: Needs,
    val selectedJobId: String,
    val rentDebtMinor: Long = 0,
    val experience: Int = 0,
    val taskRemainder: Int = 0
) {
    val level: Int get() = experience / 100

    internal fun isValid() = moneyMinor >= 0 && rentDebtMinor >= 0 && experience >= 0 &&
        taskRemainder in 0..9 && selectedJobId.isNotBlank() && needs.isValid()
}

enum class PauseReason { MANUAL, BACKGROUND, FOCUS_LOST, STORAGE_FAILURE }

data class GameClock(
    val gameMillis: Long = 0,
    val pauseReasons: Set<PauseReason> = emptySet(),
    val fractionalRemainder: Int = 0
) {
    val day: Long get() = gameMillis / GAME_DAY_MILLIS + 1
    val minuteOfDay: Int get() = (gameMillis % GAME_DAY_MILLIS / GAME_MINUTE_MILLIS).toInt()
    val isPaused: Boolean get() = pauseReasons.isNotEmpty()
}

data class Inventory(val quantities: Map<String, Int> = emptyMap()) {
    fun quantity(productId: String): Int = quantities[productId] ?: 0

    internal fun add(productId: String, quantity: Int) =
        Inventory(quantities + (productId to Math.addExact(quantity(productId), quantity)))

    internal fun consume(productId: String): Inventory {
        val remaining = quantity(productId) - 1
        return Inventory(
            if (remaining == 0) quantities - productId else quantities + (productId to remaining)
        )
    }
}

enum class SessionStatus { ACTIVE, FINISHED }
enum class FinishReason { HEALTH_DEPLETED }

data class SessionRevision(val sessionId: String, val revision: Long)

data class GameSession(
    val id: String,
    val catalogVersion: String,
    val player: PlayerState,
    val scenario: EconomicScenario,
    val dailyRentMinor: Long,
    val revision: Long = 0,
    val clock: GameClock = GameClock(),
    val inventory: Inventory = Inventory(),
    val activeActivity: Activity? = null,
    val status: SessionStatus = SessionStatus.ACTIVE,
    val finishReason: FinishReason? = null,
    val lastRentDay: Long = 1,
    val workMillis: Long = 0,
    val randomState: Long = 0,
    val events: List<GameEvent> = emptyList(),
    val lastShiftResult: ShiftResult? = null
) {
    val identity: SessionRevision get() = SessionRevision(id, revision)
    val speed: Int get() = if (activeActivity is TimedActivity.Sleep) 4 else 1
    val lastEvent: GameEvent? get() = events.lastOrNull()

    internal fun isValid(): Boolean {
        val finished = status == SessionStatus.FINISHED
        val active = activeActivity
        return id.isNotBlank() && revision >= 0 && catalogVersion == scenario.version &&
            scenario.isValid() && player.isValid() && dailyRentMinor >= 0 && workMillis >= 0 &&
            clock.gameMillis >= workMillis && clock.fractionalRemainder in 0..3 &&
            lastRentDay == clock.day &&
            inventory.quantities.all { (id, quantity) -> id.isNotBlank() && quantity > 0 } &&
            finished == (finishReason != null) && finished == (player.needs.healthUnits == 0L) &&
            (!finished || active == null) &&
            (active == null || active.isValid()) &&
            (active !is WorkShift || active.isConsistentWithSession()) &&
            (active !is TimedActivity.Food || inventory.quantity(active.product.id) > 0) &&
            events.all { it.isValid() && it.occurredAt <= clock.gameMillis } &&
            (events.firstOrNull()?.occurredAt ?: Long.MAX_VALUE) >= 3 * GAME_DAY_MILLIS &&
            events.zipWithNext().all { (a, b) ->
                b.occurredAt - a.occurredAt >= maxOf(3 * GAME_DAY_MILLIS, a.durationGameMillis)
            } &&
            (lastShiftResult == null || lastShiftResult.isValid())
    }

    private fun ShiftResult.isValid() = correct >= 0 && errors >= 0 &&
        workedGameMillis in 0..workMillis && wageMinor >= 0 && debtPaidMinor in 0..wageMinor &&
        experienceGained >= 0

    private fun WorkShift.isConsistentWithSession() = job.id == player.selectedJobId &&
        job.requiredLevel <= player.level &&
        job.durationHours * GAME_HOUR_MILLIS - remainingGameMillis <= workMillis
}
