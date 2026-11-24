package com.lastpaycheck.presentation.hardware

import androidx.lifecycle.viewModelScope
import com.lastpaycheck.domain.model.BoxCategory
import com.lastpaycheck.domain.model.ControlMode
import com.lastpaycheck.domain.model.DomainResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkTiltViewModelTest {
    private class Setup {
        val source = FakeMotionSensorSource()
        val settings = HardwareSettingsFake()
        val model = WorkTiltViewModel(source, settings)
        var time = 0L
        fun send(x: Double = 0.0, rotation: Int = 0, count: Int = 15) {
            repeat(count) {
                source.sample(x, 0.0, 9.80665, time.also { time += 100_000_000 }, rotation)
            }
        }
    }
    private fun verify(block: suspend TestScope.(Setup) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val s = Setup()
        try {
            runCurrent()
            block(s)
        } finally {
            s.model.viewModelScope.cancel()
            runCurrent()
            Dispatchers.resetMain()
        }
    }
    private fun TestScope.enable(s: Setup) {
        s.model.scene(true, "shift", "box-1", true)
        s.model.mode(ControlMode.TILT)
        runCurrent()
        s.send()
        runCurrent()
        s.model.calibrate()
        runCurrent()
        s.send()
        runCurrent()
    }

    @Test fun touchModeDoesNotSubscribeAndTiltUsesExactlyOneListener() = verify { s ->
        s.model.scene(true, "shift", "box-1", true)
        runCurrent()
        assertFalse(s.source.active)
        enable(s)
        val observations = s.source.observations
        repeat(20) { s.model.scene(true, "shift", "box-1", true) }
        runCurrent()
        assertEquals(observations, s.source.observations)
        assertTrue(s.source.active)
        s.model.mode(ControlMode.TOUCH)
        runCurrent()
        assertFalse(s.source.active)
    }

    @Test fun heldTiltBoxChangeAndDuplicateEventCannotSortTwice() = verify { s ->
        enable(s)
        val events = mutableListOf<TiltPlacement>()
        val listener = backgroundScope.launch { s.model.placements.collect { events += it } }
        runCurrent()
        s.send(4.0, count = 50)
        runCurrent()
        assertEquals(1, events.size)
        assertEquals(BoxCategory.FOOD, events.single().zone)
        assertTrue(s.model.accept(events.single()))
        assertFalse(s.model.accept(events.single()))
        s.model.scene(true, "shift", "box-2", true)
        s.send(4.0, count = 50)
        runCurrent()
        assertEquals(1, events.size)
        s.send()
        runCurrent()
        s.send(-4.0)
        runCurrent()
        assertEquals(2, events.size)
        assertEquals("box-2", events.last().boxId)
        assertEquals(BoxCategory.GENERAL, events.last().zone)
        listener.cancel()
    }

    @Test fun backgroundStopsSensorRejectsOldCommandsAndRequiresNeutralOnResume() = verify { s ->
        enable(s)
        val events = mutableListOf<TiltPlacement>()
        backgroundScope.launch { s.model.placements.collect { events += it } }
        runCurrent()
        s.send(4.0)
        runCurrent()
        val old = events.single()
        s.model.scene(false, "shift", "box-1", false)
        runCurrent()
        assertFalse(s.source.active)
        assertFalse(s.model.accept(old))
        s.send(-4.0)
        runCurrent()
        assertEquals(1, events.size)
        s.model.scene(true, "shift", "box-1", true)
        runCurrent()
        s.send(4.0)
        runCurrent()
        assertEquals(1, events.size)
        s.send()
        runCurrent()
        s.send(-4.0)
        runCurrent()
        assertEquals(2, events.size)
    }

    @Test fun noSensorDoesNotLoopAndLeavesTouchFallbackAvailable() = verify { s ->
        s.source.available = false
        s.model.mode(ControlMode.TILT)
        runCurrent()
        repeat(30) { s.model.scene(true, "shift", "box-1", true) }
        runCurrent()
        assertEquals(MotionStatus.UNAVAILABLE, s.model.state.value.motion)
        assertFalse(s.model.state.value.usesTilt)
        assertEquals(0, s.source.observations)
        s.model.mode(ControlMode.TOUCH)
        runCurrent()
        assertEquals(ControlMode.TOUCH, s.model.state.value.mode)
    }

    @Test fun failedSensorAndSettingsWriteHaveSafeFallbacks() = verify { s ->
        enable(s)
        s.source.fail()
        runCurrent()
        assertEquals(MotionStatus.ERROR, s.model.state.value.motion)
        assertFalse(s.model.state.value.usesTilt)
        s.settings.failWrites = true
        s.model.mode(ControlMode.TOUCH)
        runCurrent()
        assertTrue(s.model.state.value.settingsError)
        assertFalse(s.source.active)
    }

    @Test fun neutralConfirmationIsExplicitAndCalibrationCanBeRepeated() = verify { s ->
        enable(s)
        val events = mutableListOf<TiltPlacement>()
        backgroundScope.launch { s.model.placements.collect { events += it } }
        runCurrent()
        assertTrue(events.isEmpty())
        s.model.confirmCenter()
        s.model.confirmCenter()
        runCurrent()
        assertEquals(BoxCategory.FRAGILE, events.single().zone)
        s.send()
        runCurrent()
        s.model.calibrate()
        runCurrent()
        assertFalse(s.model.state.value.saving)
        s.send()
        runCurrent()
        assertTrue(s.model.state.value.calibrated)
        assertEquals(1, events.size)
        assertTrue((s.settings.data.value as DomainResult.Success).value.calibrationRotation == 0)
    }

    @Test fun rotationInvalidatesCalibrationAndControlsSurviveANewViewModel() = verify { s ->
        enable(s)
        s.send(rotation = 90)
        runCurrent()
        assertFalse(s.model.state.value.calibrated)
        s.model.calibrate()
        runCurrent()
        s.model.scene(false, "shift", "box-1", false)
        runCurrent()
        val replacement = WorkTiltViewModel(s.source, s.settings)
        try {
            runCurrent()
            assertEquals(ControlMode.TILT, replacement.state.value.mode)
            replacement.scene(true, "shift", "box-1", true)
            runCurrent()
            s.send(4.0)
            runCurrent()
            assertFalse(replacement.state.value.calibrated)
            assertFalse(replacement.state.value.armed)
        } finally {
            replacement.viewModelScope.cancel()
        }
    }
}
