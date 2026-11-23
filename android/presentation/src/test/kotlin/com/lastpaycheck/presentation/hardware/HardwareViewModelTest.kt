package com.lastpaycheck.presentation.hardware

import com.lastpaycheck.domain.model.CameraResult
import com.lastpaycheck.domain.model.TiltDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HardwareViewModelTest {
    private fun verify(
        block: suspend TestScope.(
            HardwareViewModel,
            FakeMotionSensorSource,
            FakeWorkCameraSource
        ) -> Unit
    ) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val motion = FakeMotionSensorSource()
        val camera = FakeWorkCameraSource()
        val model = HardwareViewModel(motion, camera)
        try {
            block(model, motion, camera)
        } finally {
            model.stop()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun observationCanBeEnabledOnceAndStoppedWithResourcesReleased() = verify { model, fake, _ ->
        model.enableMotion()
        runCurrent()
        model.enableMotion()
        assertTrue(fake.active)
        assertEquals(MotionStatus.ACTIVE, model.state.value.motionStatus)
        model.disableMotion()
        runCurrent()
        assertFalse(fake.active)
        assertEquals(1, fake.stops)
        assertEquals(MotionStatus.STOPPED, model.state.value.motionStatus)
    }

    @Test
    fun missingSensorDoesNotStartObservation() = verify { model, fake, _ ->
        fake.available = false
        model.enableMotion()
        runCurrent()
        assertEquals(MotionStatus.UNAVAILABLE, model.state.value.motionStatus)
        assertFalse(fake.active)
    }

    @Test
    fun fakeAxesPrepareLeftNeutralAndRightWithoutDuplicateCommands() = verify { model, fake, _ ->
        model.enableMotion()
        runCurrent()
        var time = 0L
        fun send(x: Double) {
            repeat(20) { fake.sample(x, 0.0, 9.8, time.also { time += 100_000_000 }) }
        }
        send(4.0)
        runCurrent()
        assertEquals(TiltDirection.LEFT, model.state.value.reading?.direction)
        assertEquals(1L, model.state.value.prepared?.sequence)
        send(0.0)
        runCurrent()
        assertEquals(TiltDirection.NEUTRAL, model.state.value.reading?.direction)
        send(-4.0)
        runCurrent()
        assertEquals(TiltDirection.RIGHT, model.state.value.reading?.direction)
        assertEquals(2L, model.state.value.prepared?.sequence)
    }

    @Test
    fun sensorFailureDoesNotCrashAndReleasesObservation() = verify { model, fake, _ ->
        model.enableMotion()
        runCurrent()
        fake.fail()
        runCurrent()
        assertEquals(MotionStatus.ERROR, model.state.value.motionStatus)
        assertFalse(fake.active)
    }

    @Test
    fun unexpectedSensorExceptionIsConvertedToAState() = verify { model, fake, _ ->
        fake.throwOnObserve = true
        model.enableMotion()
        runCurrent()
        assertEquals(MotionStatus.ERROR, model.state.value.motionStatus)
    }

    @Test
    fun initialRequestDenialAndRationaleNeverStartCamera() = verify { model, _, fake ->
        assertEquals(CameraPermission.NOT_REQUESTED, model.state.value.permission)
        model.capture()
        assertEquals(CameraResult.PermissionDenied, model.state.value.cameraResult)
        model.updatePermission(false, false, true)
        assertEquals(CameraPermission.DENIED, model.state.value.permission)
        model.updatePermission(false, true, true)
        assertEquals(CameraPermission.RATIONALE, model.state.value.permission)
        model.capture()
        assertEquals(0, fake.captures)
    }

    @Test
    fun grantedPermissionEnablesRealContractAndResultIsPassedThrough() = verify { model, _, fake ->
        model.updatePermission(true, false, true)
        model.capture()
        runCurrent()
        assertEquals(1, fake.captures)
        assertEquals(fake.result, model.state.value.cameraResult)
        assertFalse(model.state.value.cameraBusy)
    }

    @Test
    fun revokedPermissionCannotBeImitatedAsSuccess() = verify { model, _, fake ->
        fake.result = CameraResult.PermissionDenied
        model.updatePermission(true, false, true)
        model.capture()
        runCurrent()
        assertEquals(CameraPermission.DENIED, model.state.value.permission)
        assertEquals(CameraResult.PermissionDenied, model.state.value.cameraResult)
    }

    @Test
    fun missingCameraHasAnExplicitState() = runTest {
        val camera = FakeWorkCameraSource(available = false)
        val model = HardwareViewModel(FakeMotionSensorSource(), camera)
        model.capture()
        assertFalse(model.state.value.cameraAvailable)
        assertEquals(CameraResult.Unavailable, model.state.value.cameraResult)
        assertEquals(0, camera.captures)
    }

    @Test
    fun cameraErrorsAllowRetryAndLeavingClearsTheFrame() = verify { model, _, fake ->
        model.updatePermission(true, false, true)
        fake.fail = true
        model.capture()
        runCurrent()
        assertEquals(CameraResult.Failure, model.state.value.cameraResult)
        fake.fail = false
        model.capture()
        runCurrent()
        assertNotNull(model.state.value.cameraResult as? CameraResult.Frame)
        model.stop()
        assertEquals(null, model.state.value.cameraResult)
    }

    @Test
    fun leavingCancelsAnInFlightCameraOperationAndReleasesIt() = verify { model, _, fake ->
        fake.waitForCancellation = true
        model.updatePermission(true, false, true)
        model.capture()
        runCurrent()
        assertTrue(fake.active)
        model.stop()
        runCurrent()
        assertFalse(fake.active)
        assertEquals(1, fake.released)
        assertFalse(model.state.value.cameraBusy)
        assertEquals(null, model.state.value.cameraResult)
    }
}
