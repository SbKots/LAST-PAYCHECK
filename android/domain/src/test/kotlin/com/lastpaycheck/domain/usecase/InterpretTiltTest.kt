package com.lastpaycheck.domain.usecase

import com.lastpaycheck.domain.model.AccelerationSample
import com.lastpaycheck.domain.model.TiltDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InterpretTiltTest {
    private val interpret = InterpretTilt()
    private var time = 0L

    private fun sample(x: Double = 0.0, y: Double = 0.0, rotation: Int = 0) =
        AccelerationSample(x, y, 9.80665, time.also { time += 100_000_000 }, rotation)

    @Test
    fun neutralPositionDoesNotPrepareCommands() {
        repeat(10) {
            val value = checkNotNull(interpret(sample()))
            assertEquals(TiltDirection.NEUTRAL, value.direction)
            assertNull(value.command)
        }
    }

    @Test
    fun sustainedLeftTiltPreparesExactlyOneCommand() {
        val values = List(30) { interpret(sample(4.0))!! }
        assertEquals(TiltDirection.LEFT, values.last().direction)
        assertEquals(1, values.count { it.command != null })
        assertEquals(1L, values.firstNotNullOf { it.command }.sequence)
    }

    @Test
    fun sustainedRightTiltPreparesRight() {
        val values = List(10) { interpret(sample(-4.0))!! }
        assertEquals(TiltDirection.RIGHT, values.last().direction)
        assertEquals(TiltDirection.RIGHT, values.firstNotNullOf { it.command }.direction)
    }

    @Test
    fun smallAlternatingNoiseStaysNeutral() {
        repeat(100) {
            val reading = interpret(sample(if (it % 2 == 0) 0.7 else -0.7))!!
            assertEquals(TiltDirection.NEUTRAL, reading.direction)
            assertNull(reading.command)
        }
    }

    @Test
    fun briefSpikeDoesNotPrepareAnAction() {
        repeat(4) { interpret(sample()) }
        val values = listOf(interpret(sample(9.0))!!) + List(10) { interpret(sample())!! }
        assertTrue(values.all { it.command == null })
    }

    @Test
    fun neutralRearmsAndSequenceRemainsUniqueAfterReset() {
        val first = List(10) { interpret(sample(4.0))!! }.firstNotNullOf { it.command }
        repeat(15) { interpret(sample()) }
        val second = List(10) { interpret(sample(4.0))!! }.firstNotNullOf { it.command }
        interpret.reset()
        val third = List(10) { interpret(sample(-4.0))!! }.firstNotNullOf { it.command }
        assertTrue(first.sequence < second.sequence && second.sequence < third.sequence)
    }

    @Test
    fun rotationMapsAllFourOrientationsToTheSameScreenDirection() {
        listOf(
            0 to (4.0 to 0.0),
            90 to (0.0 to -4.0),
            180 to (-4.0 to 0.0),
            270 to (0.0 to 4.0)
        ).forEach { (rotation, axes) ->
            interpret.reset()
            val readings = List(10) { interpret(sample(axes.first, axes.second, rotation))!! }
            assertEquals(TiltDirection.LEFT, readings.last().direction)
        }
    }

    @Test
    fun invalidOrOutOfOrderMeasurementsAreIgnored() {
        val valid = sample()
        interpret(valid)
        assertNull(interpret(valid))
        assertNull(interpret(valid.copy(timestampNanos = -1)))
        assertNull(interpret(sample(Double.NaN)))
        assertNull(interpret(sample(y = Double.POSITIVE_INFINITY)))
        assertNull(interpret(sample(rotation = 45)))
    }

    @Test
    fun normalizationClampsAndRetainsTheOriginalModelAndTimestamp() {
        val raw = sample(100.0, 2.0)
        val reading = interpret(raw)!!
        assertEquals(raw, reading.sample)
        assertEquals(-1.0, reading.horizontal, 0.0)
        assertEquals(2.0, reading.sample.y, 0.0)
        assertEquals(9.80665, reading.sample.z, 0.0)
    }

    @Test
    fun hysteresisKeepsStableTiltBetweenTheEntryAndExitThresholds() {
        repeat(10) { interpret(sample(4.0)) }
        val readings = List(20) { interpret(sample(9.80665 * 0.14))!! }
        assertTrue(readings.all { it.direction == TiltDirection.LEFT && it.command == null })
        val neutral = List(20) { interpret(sample(9.80665 * 0.05))!! }
        assertEquals(TiltDirection.NEUTRAL, neutral.last().direction)
    }
}
