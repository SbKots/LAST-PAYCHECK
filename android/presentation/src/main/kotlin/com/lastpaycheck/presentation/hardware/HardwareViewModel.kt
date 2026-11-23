package com.lastpaycheck.presentation.hardware

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lastpaycheck.domain.model.CameraResult
import com.lastpaycheck.domain.model.MotionEvent
import com.lastpaycheck.domain.model.PreparedSortDirection
import com.lastpaycheck.domain.model.TiltReading
import com.lastpaycheck.domain.repository.MotionSensorSource
import com.lastpaycheck.domain.repository.WorkCameraSource
import com.lastpaycheck.domain.usecase.InterpretTilt
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class MotionStatus { STOPPED, STARTING, ACTIVE, UNAVAILABLE, ERROR }
enum class CameraPermission { NOT_REQUESTED, GRANTED, DENIED, RATIONALE }

data class HardwareUiState(
    val motionStatus: MotionStatus = MotionStatus.STOPPED,
    val reading: TiltReading? = null,
    val prepared: PreparedSortDirection? = null,
    val cameraAvailable: Boolean = false,
    val permission: CameraPermission = CameraPermission.NOT_REQUESTED,
    val cameraBusy: Boolean = false,
    val cameraResult: CameraResult? = null
)

@HiltViewModel
class HardwareViewModel @Inject constructor(
    private val motion: MotionSensorSource,
    private val camera: WorkCameraSource
) : ViewModel() {
    private val mutableState = MutableStateFlow(HardwareUiState(cameraAvailable = camera.available))
    val state = mutableState.asStateFlow()
    private var motionJob: Job? = null
    private var cameraJob: Job? = null
    private val interpret = InterpretTilt()

    fun enableMotion() {
        if (motionJob?.isActive == true) return
        interpret.reset()
        mutableState.value = state.value.copy(
            motionStatus = if (motion.available) {
                MotionStatus.STARTING
            } else {
                MotionStatus.UNAVAILABLE
            },
            reading = null,
            prepared = null
        )
        if (!motion.available) return
        motionJob = viewModelScope.launch {
            try {
                motion.observe().collect { event ->
                    mutableState.value = when (event) {
                        MotionEvent.Started -> state.value.copy(motionStatus = MotionStatus.ACTIVE)
                        MotionEvent.Unavailable -> state.value.copy(
                            motionStatus = MotionStatus.UNAVAILABLE
                        )
                        MotionEvent.Failure -> state.value.copy(motionStatus = MotionStatus.ERROR)
                        is MotionEvent.Sample -> interpret(event.value)?.let {
                            state.value.copy(
                                reading = it,
                                prepared = it.command ?: state.value.prepared
                            )
                        } ?: state.value
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = state.value.copy(motionStatus = MotionStatus.ERROR)
            } finally {
                if (state.value.motionStatus == MotionStatus.ACTIVE) {
                    mutableState.value = state.value.copy(motionStatus = MotionStatus.STOPPED)
                }
            }
        }
    }

    fun disableMotion() {
        motionJob?.cancel()
        motionJob = null
        interpret.reset()
        mutableState.value = state.value.copy(
            motionStatus = MotionStatus.STOPPED,
            reading = null,
            prepared = null
        )
    }

    fun updatePermission(granted: Boolean, rationale: Boolean, requested: Boolean) {
        mutableState.value = state.value.copy(
            permission = when {
                granted -> CameraPermission.GRANTED
                rationale -> CameraPermission.RATIONALE
                requested -> CameraPermission.DENIED
                else -> CameraPermission.NOT_REQUESTED
            }
        )
    }

    fun capture() {
        if (state.value.cameraBusy) return
        if (!state.value.cameraAvailable) {
            mutableState.value = state.value.copy(cameraResult = CameraResult.Unavailable)
            return
        }
        if (state.value.permission != CameraPermission.GRANTED) {
            mutableState.value = state.value.copy(cameraResult = CameraResult.PermissionDenied)
            return
        }
        mutableState.value = state.value.copy(cameraBusy = true, cameraResult = null)
        cameraJob = viewModelScope.launch {
            try {
                val result = camera.capture()
                mutableState.value = state.value.copy(cameraResult = result)
                if (result == CameraResult.PermissionDenied) {
                    updatePermission(granted = false, rationale = false, requested = true)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = state.value.copy(cameraResult = CameraResult.Failure)
            } finally {
                mutableState.value = state.value.copy(cameraBusy = false)
            }
        }
    }

    fun stop() {
        disableMotion()
        cameraJob?.cancel()
        cameraJob = null
        mutableState.value = state.value.copy(cameraResult = null, cameraBusy = false)
    }
}
