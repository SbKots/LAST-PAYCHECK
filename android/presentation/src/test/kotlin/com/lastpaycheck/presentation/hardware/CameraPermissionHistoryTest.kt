package com.lastpaycheck.presentation.hardware

import androidx.lifecycle.viewModelScope
import com.lastpaycheck.domain.model.DomainResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CameraPermissionHistoryTest {
    @Test fun firstRequestRetryPermanentDenialAndSettingsReturnUseActualPermission() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val settings = HardwareSettingsFake()
        val camera = FakeWorkCameraSource()
        val model = HardwareViewModel(FakeMotionSensorSource(), camera, settings)
        try {
            runCurrent()
            model.updatePermission(false, false)
            assertEquals(CameraPermission.NOT_REQUESTED, model.state.value.permission)
            var requests = 0
            model.requestPermission {
                assertTrue(
                    (settings.data.value as DomainResult.Success).value.cameraPermissionRequested
                )
                requests++
            }
            runCurrent()
            assertEquals(1, requests)
            model.updatePermission(false, false)
            assertTrue(model.state.value.requestPending)
            model.permissionResult(false, true)
            assertEquals(CameraPermission.RATIONALE, model.state.value.permission)
            model.requestPermission { requests++ }
            runCurrent()
            model.permissionResult(false, false)
            assertEquals(CameraPermission.DENIED, model.state.value.permission)
            model.requestPermission { requests++ }
            runCurrent()
            assertEquals(2, requests)
            model.updatePermission(false, false)
            assertEquals(CameraPermission.DENIED, model.state.value.permission)
            model.updatePermission(true, false)
            assertEquals(CameraPermission.GRANTED, model.state.value.permission)
            assertEquals(0, camera.captures)
            model.capture()
            runCurrent()
            assertEquals(1, camera.captures)
        } finally {
            model.stop()
            model.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test fun recreatedViewModelRetainsHistoryWithoutAssumingPermission() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val settings = HardwareSettingsFake()
        val first = HardwareViewModel(FakeMotionSensorSource(), FakeWorkCameraSource(), settings)
        var next: HardwareViewModel? = null
        try {
            runCurrent()
            first.requestPermission {}
            runCurrent()
            first.permissionResult(false, false)
            first.viewModelScope.cancel()
            next = HardwareViewModel(FakeMotionSensorSource(), FakeWorkCameraSource(), settings)
            runCurrent()
            assertEquals(CameraPermission.DENIED, next.state.value.permission)
            assertFalse(next.state.value.cameraBusy)
            next.updatePermission(true, false)
            assertEquals(CameraPermission.GRANTED, next.state.value.permission)
        } finally {
            next?.viewModelScope?.cancel()
            first.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test fun missingCameraAndFailedHistoryPersistenceDoNotLaunchRequests() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val settings = HardwareSettingsFake()
        val missing = HardwareViewModel(
            FakeMotionSensorSource(),
            FakeWorkCameraSource(false),
            settings
        )
        val existing = HardwareViewModel(FakeMotionSensorSource(), FakeWorkCameraSource(), settings)
        try {
            runCurrent()
            var requests = 0
            missing.requestPermission { requests++ }
            runCurrent()
            assertEquals(0, requests)
            settings.failWrites = true
            existing.requestPermission { requests++ }
            runCurrent()
            assertEquals(0, requests)
            assertTrue(existing.state.value.permissionStorageError)
            assertFalse(existing.state.value.requestPending)
        } finally {
            missing.viewModelScope.cancel()
            existing.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }
}
