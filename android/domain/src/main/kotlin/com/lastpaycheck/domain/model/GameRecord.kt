package com.lastpaycheck.domain.model

enum class TransactionCategory {
    PURCHASE,
    COURSE,
    WAGE,
    RENT,
    DEBT_PAYMENT,
    EVENT,
    CAR_PURCHASE,
    CAR_UPGRADE,
    CAR_REPAIR,
    RACE_ENTRY,
    RACE_PRIZE
}

data class FinancialTransaction(
    val id: String,
    val operationId: String,
    val sessionId: String,
    val gameMillis: Long,
    val category: TransactionCategory,
    val amountMinor: Long
)

data class ShiftResult(
    val correct: Int,
    val errors: Int,
    val workedGameMillis: Long,
    val wageMinor: Long,
    val debtPaidMinor: Long,
    val experienceGained: Int
)

data class GameStatistics(
    val sessionId: String,
    val incomeMinor: Long,
    val expenseMinor: Long,
    val byCategoryMinor: Map<TransactionCategory, Long>,
    val days: Long,
    val workMillis: Long,
    val finalPlayer: PlayerState,
    val finishReason: FinishReason?
)

data class GameEvent(
    val definitionId: String,
    val description: String,
    val occurredAt: Long,
    val moneyLostMinor: Long,
    val healthLost: Int,
    val durationGameMillis: Long,
    val extraWorkEnergyPerHour: Int
) {
    fun remainingAt(gameMillis: Long): Long {
        if (gameMillis < occurredAt) return 0
        return (durationGameMillis - (gameMillis - occurredAt)).coerceAtLeast(0)
    }

    internal fun isValid() = definitionId.isNotBlank() && description.isNotBlank() &&
        occurredAt >= 0 && moneyLostMinor >= 0 && healthLost in 0..100 &&
        durationGameMillis >= 0 && extraWorkEnergyPerHour >= 0
}

data class EventDefinition(
    val id: String,
    val description: String,
    val moneyLossPercent: Int,
    val healthLoss: Int,
    val durationGameMillis: Long = 0,
    val extraWorkEnergyPerHour: Int = 0
)

enum class ControlMode { TOUCH, TILT }

enum class ThemePreference { SYSTEM, LIGHT, DARK }
enum class AppLanguage { SYSTEM, RUSSIAN, ENGLISH }

data class GameSettings(
    val controls: ControlMode,
    val calibration: Double,
    val soundEnabled: Boolean,
    val vibrationEnabled: Boolean,
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val calibrationRotation: Int? = null,
    val cameraPermissionRequested: Boolean = false
)

data class OperationKey(val sessionId: String, val id: String)

sealed interface GameEffect {
    data object Applied : GameEffect
    data class Purchase(val productId: String, val quantity: Int, val costMinor: Long) : GameEffect
    data class Placement(
        val correct: Boolean,
        val expected: BoxCategory? = null,
        val actual: BoxCategory? = null
    ) : GameEffect
    data class ShiftFinished(val result: ShiftResult) : GameEffect
}

data class OperationReceipt(
    val key: OperationKey,
    val revision: Long,
    val effect: GameEffect
)

data class GameTransition(
    val session: GameSession,
    val transactions: List<FinancialTransaction> = emptyList(),
    val effect: GameEffect = GameEffect.Applied
)

data class CommittedOperation(val session: GameSession, val receipt: OperationReceipt)
