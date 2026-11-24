package com.lastpaycheck.domain.usecase

import com.lastpaycheck.domain.model.AccelerationSample
import com.lastpaycheck.domain.model.BoxCategory
import com.lastpaycheck.domain.model.TiltDirection

data class TiltCalibration(val horizontal: Double, val rotationDegrees: Int)
data class TiltSortingReading(
    val direction: TiltDirection = TiltDirection.NEUTRAL,
    val calibrated: Boolean = false,
    val canCalibrate: Boolean = false,
    val armed: Boolean = false,
    val zone: BoxCategory? = null
)

class TiltSortingGesture {
    private val interpret = InterpretTilt()
    private var calibration: TiltCalibration? = null
    private val window = ArrayDeque<AccelerationSample>()
    private var lastTimestamp = -1L
    private var neutralSince: Long? = null
    private var armed = false
    var reading = TiltSortingReading()
        private set

    fun reset() {
        window.clear()
        lastTimestamp = -1
        neutralSince = null
        armed = false
        interpret.reset()
        reading = TiltSortingReading()
    }

    fun configure(value: TiltCalibration?) {
        calibration = value
        value?.let { interpret.calibrate(it.horizontal, it.rotationDegrees) }
        reset()
    }

    fun calibrate(): TiltCalibration? {
        if (!reading.canCalibrate) return null
        val value = TiltCalibration(
            window.map(InterpretTilt::horizontal).average(),
            window.last().rotationDegrees
        )
        configure(value)
        return value
    }

    fun sample(sample: AccelerationSample, boxReady: Boolean): TiltSortingReading {
        if (!sample.x.isFinite() || !sample.y.isFinite() || !sample.z.isFinite() ||
            sample.timestampNanos < 0 || sample.timestampNanos <= lastTimestamp ||
            sample.rotationDegrees !in listOf(0, 90, 180, 270)
        ) {
            return reading.copy(zone = null)
        }
        if (window.lastOrNull()?.rotationDegrees != sample.rotationDegrees ||
            lastTimestamp >= 0 && sample.timestampNanos - lastTimestamp > 500_000_000
        ) {
            reset()
        }
        lastTimestamp = sample.timestampNanos
        window.addLast(sample)
        while (window.size > 1 &&
            sample.timestampNanos - window.first().timestampNanos > 600_000_000
        ) {
            window.removeFirst()
        }
        val horizontal = window.map(InterpretTilt::horizontal)
        val stable = window.size >= 6 &&
            sample.timestampNanos - window.first().timestampNanos >= 500_000_000 &&
            horizontal.max() - horizontal.min() <= 0.05 &&
            window.all { it.x * it.x + it.y * it.y + it.z * it.z in 49.0..169.0 }
        if (calibration?.rotationDegrees != sample.rotationDegrees) {
            reading = TiltSortingReading(canCalibrate = stable)
            return reading
        }
        val value = interpret(sample) ?: return reading.copy(zone = null)
        if (value.direction == TiltDirection.NEUTRAL && kotlin.math.abs(value.horizontal) <= 0.10) {
            if (neutralSince == null) neutralSince = sample.timestampNanos
            if (sample.timestampNanos - checkNotNull(neutralSince) >= 250_000_000) armed = true
        } else {
            neutralSince = null
        }
        var zone: BoxCategory? = null
        value.command?.let {
            if (armed && boxReady) {
                zone = when (it.direction) {
                    TiltDirection.LEFT -> BoxCategory.FOOD
                    TiltDirection.RIGHT -> BoxCategory.GENERAL
                    TiltDirection.NEUTRAL -> null
                }
            }
            armed = false
        }
        reading = TiltSortingReading(value.direction, true, stable, armed, zone)
        return reading
    }

    fun confirmCenter(boxReady: Boolean): BoxCategory? {
        if (!boxReady || !reading.calibrated || !armed ||
            reading.direction != TiltDirection.NEUTRAL
        ) {
            return null
        }
        armed = false
        neutralSince = null
        reading = reading.copy(armed = false, zone = null)
        return BoxCategory.FRAGILE
    }
}
