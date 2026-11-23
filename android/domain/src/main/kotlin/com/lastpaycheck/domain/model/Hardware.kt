package com.lastpaycheck.domain.model

data class AccelerationSample(
    val x: Double,
    val y: Double,
    val z: Double,
    val timestampNanos: Long,
    val rotationDegrees: Int = 0
)

enum class TiltDirection { LEFT, NEUTRAL, RIGHT }

data class PreparedSortDirection(val direction: TiltDirection, val sequence: Long)

data class TiltReading(
    val sample: AccelerationSample,
    val horizontal: Double,
    val direction: TiltDirection,
    val command: PreparedSortDirection? = null
)

sealed interface MotionEvent {
    data object Started : MotionEvent
    data class Sample(val value: AccelerationSample) : MotionEvent
    data object Unavailable : MotionEvent
    data object Failure : MotionEvent
}

sealed interface CameraResult {
    data class Frame(val jpeg: ByteArray, val rotationDegrees: Int) : CameraResult
    data object PermissionDenied : CameraResult
    data object Unavailable : CameraResult
    data object Failure : CameraResult
}
