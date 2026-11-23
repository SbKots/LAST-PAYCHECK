package com.lastpaycheck.domain.usecase

import com.lastpaycheck.domain.model.AccelerationSample
import com.lastpaycheck.domain.model.PreparedSortDirection
import com.lastpaycheck.domain.model.TiltDirection
import com.lastpaycheck.domain.model.TiltReading

class InterpretTilt {
    private var lastTimestamp = -1L
    private var rotation = 0
    private var filtered = 0.0
    private var direction = TiltDirection.NEUTRAL
    private var candidate = TiltDirection.NEUTRAL
    private var candidateSince = 0L
    private var sequence = 0L

    fun reset() {
        lastTimestamp = -1
        filtered = 0.0
        direction = TiltDirection.NEUTRAL
        candidate = TiltDirection.NEUTRAL
        candidateSince = 0
    }

    operator fun invoke(sample: AccelerationSample): TiltReading? {
        if (!sample.x.isFinite() || !sample.y.isFinite() || !sample.z.isFinite() ||
            sample.timestampNanos < 0 || sample.timestampNanos <= lastTimestamp ||
            sample.rotationDegrees !in listOf(0, 90, 180, 270)
        ) {
            return null
        }
        val screenX = when (sample.rotationDegrees) {
            90 -> -sample.y
            180 -> -sample.x
            270 -> sample.y
            else -> sample.x
        }
        val normalized = (-screenX / 9.80665).coerceIn(-1.0, 1.0)
        val delta = (sample.timestampNanos - lastTimestamp) / 1_000_000.0
        if (lastTimestamp < 0 || rotation != sample.rotationDegrees || delta > 500) {
            filtered = normalized
            direction = TiltDirection.NEUTRAL
            candidate = TiltDirection.NEUTRAL
        } else {
            filtered += (normalized - filtered) * delta / (160 + delta)
        }
        rotation = sample.rotationDegrees
        lastTimestamp = sample.timestampNanos
        val next = when {
            filtered <= -0.18 -> TiltDirection.LEFT
            filtered >= 0.18 -> TiltDirection.RIGHT
            direction == TiltDirection.LEFT && filtered < -0.10 -> direction
            direction == TiltDirection.RIGHT && filtered > 0.10 -> direction
            else -> TiltDirection.NEUTRAL
        }
        if (candidate != next) {
            candidate = next
            candidateSince = sample.timestampNanos
        }
        var command: PreparedSortDirection? = null
        if (next != direction && sample.timestampNanos - candidateSince >= 250_000_000) {
            direction = next
            if (direction != TiltDirection.NEUTRAL) {
                command = PreparedSortDirection(direction, ++sequence)
            }
        }
        return TiltReading(sample, filtered, direction, command)
    }
}
