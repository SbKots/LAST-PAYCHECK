package com.lastpaycheck.data.hardware

import androidx.test.core.app.ApplicationProvider
import com.lastpaycheck.domain.model.CameraResult
import com.lastpaycheck.domain.model.MotionEvent
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidHardwareTest {
    @Test
    fun realSensorReturnsFiniteAxesWithMonotonicTimestampsAndCanBeStopped() = runBlocking {
        val source = AndroidMotionSensorSource(ApplicationProvider.getApplicationContext())
        assertTrue(source.available)
        repeat(2) {
            val samples = withTimeout(5_000) {
                source.observe().filterIsInstance<MotionEvent.Sample>().take(3).toList()
            }.map { it.value }
            assertEquals(3, samples.size)
            assertTrue(samples.all { it.x.isFinite() && it.y.isFinite() && it.z.isFinite() })
            assertTrue(samples.zipWithNext().all { (a, b) -> a.timestampNanos < b.timestampNanos })
        }
    }

    @Test
    fun cameraCannotOpenWithoutTheActualAndroidPermission() = runBlocking {
        val source = AndroidWorkCameraSource(ApplicationProvider.getApplicationContext())
        assertEquals(CameraResult.PermissionDenied, source.capture())
    }
}
