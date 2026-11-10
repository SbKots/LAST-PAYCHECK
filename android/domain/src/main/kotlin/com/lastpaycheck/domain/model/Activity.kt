package com.lastpaycheck.domain.model

sealed interface Activity {
    val id: String
    val remainingGameMillis: Long
}

enum class BoxCategory { FOOD, FRAGILE, GENERAL }

data class WarehouseBox(val id: String, val category: BoxCategory)

data class WorkShift(
    override val id: String,
    val job: Job,
    val selectedBox: WarehouseBox,
    override val remainingGameMillis: Long = job.durationHours * GAME_HOUR_MILLIS,
    val correct: Int = 0,
    val errors: Int = 0,
    val processedBoxIds: Set<String> = emptySet(),
    val boxPosition: Double = 0.0
) : Activity

sealed interface TimedActivity : Activity {
    data class Food(
        override val id: String,
        val product: Product,
        override val remainingGameMillis: Long = product.durationMinutes * GAME_MINUTE_MILLIS
    ) : TimedActivity

    data class Sleep(
        override val id: String,
        override val remainingGameMillis: Long = 8 * GAME_HOUR_MILLIS
    ) : TimedActivity

    data class Rest(
        override val id: String,
        override val remainingGameMillis: Long = GAME_HOUR_MILLIS
    ) : TimedActivity

    data class Course(
        override val id: String,
        val course: com.lastpaycheck.domain.model.Course,
        override val remainingGameMillis: Long = course.durationHours * GAME_HOUR_MILLIS
    ) : TimedActivity
}

sealed interface ActivityRequest {
    data class Food(val productId: String) : ActivityRequest
    data class Course(val courseId: String) : ActivityRequest
    data object Sleep : ActivityRequest
    data object Rest : ActivityRequest
}

internal fun Activity.isValid(): Boolean {
    if (id.isBlank() || remainingGameMillis <= 0) return false
    return when (this) {
        is WorkShift -> job.isValid() &&
            remainingGameMillis <= job.durationHours * GAME_HOUR_MILLIS &&
            correct >= 0 && errors >= 0 &&
            correct.toLong() + errors == processedBoxIds.size.toLong() &&
            processedBoxIds.none(String::isBlank) &&
            selectedBox.id.isNotBlank() && selectedBox.id !in processedBoxIds &&
            boxPosition in -1.0..1.0
        is TimedActivity.Food -> product.isValid() &&
            remainingGameMillis <= product.durationMinutes * GAME_MINUTE_MILLIS
        is TimedActivity.Sleep -> remainingGameMillis <= 8 * GAME_HOUR_MILLIS
        is TimedActivity.Rest -> remainingGameMillis <= GAME_HOUR_MILLIS
        is TimedActivity.Course -> course.isValid() &&
            remainingGameMillis <= course.durationHours * GAME_HOUR_MILLIS
    }
}
