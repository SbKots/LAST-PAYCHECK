package com.lastpaycheck.domain.model

sealed interface DomainResult<out T> {
    data class Success<T>(val value: T) : DomainResult<T>
    data class Failure(val error: GameError) : DomainResult<Nothing>
}

sealed interface GameError {
    data class InvalidInput(val field: String) : GameError
    data class NotFound(val id: String) : GameError
    data class InsufficientFunds(val requiredMinor: Long, val availableMinor: Long) : GameError
    data class PriceChanged(val unitPriceMinor: Long) : GameError
    data class InvalidSnapshot(val previousAvailable: Boolean) : GameError
    data object InvalidCatalog : GameError
    data object InvalidEvent : GameError
    data object StorageFailure : GameError
    data object RevisionConflict : GameError
    data object CatalogUnavailable : GameError
    data object NoSession : GameError
    data class ConfirmationRequired(val sessionId: String) : GameError
    data object Busy : GameError
    data object Paused : GameError
    data object GameFinished : GameError
    data object GameInProgress : GameError
    data object UnavailableJob : GameError
    data object InsufficientEnergy : GameError
    data object EmptyInventory : GameError
    data object NoActiveShift : GameError
    data object BoxAlreadyPlaced : GameError
    data object LimitExceeded : GameError
}

inline fun <T, R> DomainResult<T>.map(transform: (T) -> R): DomainResult<R> = when (this) {
    is DomainResult.Success -> DomainResult.Success(transform(value))
    is DomainResult.Failure -> this
}

internal inline fun <T, R> DomainResult<T>.flatMap(
    transform: (T) -> DomainResult<R>
): DomainResult<R> = when (this) {
    is DomainResult.Success -> transform(value)
    is DomainResult.Failure -> this
}

internal fun failure(error: GameError) = DomainResult.Failure(error)

internal inline fun <T> checkedArithmetic(block: () -> DomainResult<T>): DomainResult<T> = try {
    block()
} catch (_: ArithmeticException) {
    failure(GameError.LimitExceeded)
}
