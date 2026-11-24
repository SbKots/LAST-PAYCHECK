package com.lastpaycheck.domain.usecase

import com.lastpaycheck.domain.model.AccelerationSample
import com.lastpaycheck.domain.model.BoxCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TiltSortingGestureTest {
    private val gesture = TiltSortingGesture()
    private var time = 0L
    private fun send(x: Double = 0.0, y: Double = 0.0, rotation: Int = 0, ready: Boolean = true) =
        gesture.sample(
            AccelerationSample(
                x,
                y,
                9.80665,
                time.also { time += 100_000_000 },
                rotation
            ),
            ready
        )
    private fun neutral() {
        repeat(15) { send() }
    }
    private fun configured() {
        gesture.configure(TiltCalibration(0.0, 0))
        neutral()
    }

    @Test fun initialTiltAndNeutralDoNotPlaceAnythingBeforeCalibration() {
        assertTrue(List(50) { send(4.0) }.all { it.zone == null && !it.calibrated })
        neutral()
        assertNull(gesture.confirmCenter(true))
    }

    @Test fun leftAndRightMapToAAndCOnlyAfterNeutral() {
        configured()
        assertEquals(listOf(BoxCategory.FOOD), List(50) { send(4.0) }.mapNotNull { it.zone })
        assertTrue(List(30) { send(-4.0) }.all { it.zone == null })
        neutral()
        assertEquals(listOf(BoxCategory.GENERAL), List(30) { send(-4.0) }.mapNotNull { it.zone })
    }

    @Test fun neutralNeedsExplicitConfirmationForBAndCannotDuplicateAClick() {
        configured()
        assertTrue(List(50) { send() }.all { it.zone == null })
        assertEquals(BoxCategory.FRAGILE, gesture.confirmCenter(true))
        assertNull(gesture.confirmCenter(true))
        neutral()
        assertEquals(BoxCategory.FRAGILE, gesture.confirmCenter(true))
    }

    @Test fun aHeldGestureDuringConveyorWaitCannotSortTheNextBox() {
        configured()
        assertTrue(List(20) { send(4.0, ready = false) }.all { it.zone == null })
        assertTrue(List(50) { send(4.0) }.all { it.zone == null })
        neutral()
        assertEquals(1, List(20) { send(4.0) }.count { it.zone != null })
    }

    @Test fun calibrationNeedsHalfASecondOfStableMeasurementsAndDoesNotPlace() {
        repeat(5) { send(2.0) }
        assertFalse(gesture.reading.canCalibrate)
        assertNull(gesture.calibrate())
        send(2.0)
        assertTrue(gesture.reading.canCalibrate)
        val calibration = checkNotNull(gesture.calibrate())
        assertEquals(-2.0 / 9.80665, calibration.horizontal, 0.00001)
        assertTrue(List(20) { send(2.0) }.all { it.zone == null })
        assertEquals(BoxCategory.FOOD, List(20) { send(6.0) }.mapNotNull { it.zone }.single())
    }

    @Test fun shakingCannotBeUsedAsAStableCalibration() {
        repeat(20) { send(if (it % 2 == 0) 3.0 else -3.0) }
        assertFalse(gesture.reading.canCalibrate)
        assertNull(gesture.calibrate())
    }

    @Test fun rotationRequiresNewCalibrationAndCorrectlyRemapsTheAxes() {
        configured()
        repeat(10) { assertNull(send(y = -4.0, rotation = 90).zone) }
        assertFalse(gesture.reading.calibrated)
        repeat(10) { send(rotation = 90) }
        assertEquals(90, gesture.calibrate()!!.rotationDegrees)
        repeat(15) { send(rotation = 90) }
        assertEquals(
            BoxCategory.FOOD,
            List(20) {
                send(
                    y = -4.0,
                    rotation = 90
                )
            }.mapNotNull { it.zone }.single()
        )
    }

    @Test fun resumedHeldPhoneAndNoiseDoNotRepeatAnOldGesture() {
        configured()
        assertEquals(1, List(20) { send(4.0) }.count { it.zone != null })
        gesture.reset()
        assertTrue(List(50) { send(4.0) }.all { it.zone == null })
        neutral()
        assertTrue(List(30) { send(if (it % 2 == 0) .7 else -.7) }.all { it.zone == null })
        assertEquals(1, List(20) { send(-4.0) }.count { it.zone != null })
    }

    @Test fun briefSpikesAndInvalidSamplesDoNotPlace() {
        configured()
        val values = listOf(send(9.0)) + List(15) { send() }
        assertTrue(values.all { it.zone == null })
        assertNull(send(Double.NaN).zone)
        assertNull(send(rotation = 45).zone)
        assertNull(gesture.confirmCenter(false))
    }
}
