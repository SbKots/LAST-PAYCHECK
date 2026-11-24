package com.lastpaycheck.presentation.hardware

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lastpaycheck.domain.model.BoxCategory
import com.lastpaycheck.domain.model.ControlMode
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.GameSettings
import com.lastpaycheck.domain.model.MotionEvent
import com.lastpaycheck.domain.model.TiltDirection
import com.lastpaycheck.domain.repository.MotionSensorSource
import com.lastpaycheck.domain.repository.SettingsRepository
import com.lastpaycheck.domain.usecase.TiltCalibration
import com.lastpaycheck.domain.usecase.TiltSortingGesture
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TiltPlacement(
    val boxId: String,
    val zone: BoxCategory,
    val operationId: String,
    val lease: Long
)
data class WorkTiltState(
    val mode: ControlMode = ControlMode.TOUCH,
    val motion: MotionStatus = MotionStatus.STOPPED,
    val calibrated: Boolean = false,
    val canCalibrate: Boolean = false,
    val armed: Boolean = false,
    val direction: TiltDirection = TiltDirection.NEUTRAL,
    val saving: Boolean = true,
    val settingsError: Boolean = false
) {
    val usesTilt get() = mode == ControlMode.TILT &&
        motion != MotionStatus.UNAVAILABLE && motion != MotionStatus.ERROR
}

@HiltViewModel
class WorkTiltViewModel @Inject constructor(
    private val source: MotionSensorSource,
    private val settings: SettingsRepository
) : ViewModel() {
    private val mutable = MutableStateFlow(WorkTiltState())
    val state = mutable.asStateFlow()
    private val events = MutableSharedFlow<TiltPlacement>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val placements = events.asSharedFlow()
    private val gesture = TiltSortingGesture()
    private var preferences: GameSettings? = null
    private var motionJob: Job? = null
    private var saving: Job? = null
    private var scene = false
    private var ready = false
    private var box = ""
    private var shift = ""
    private var lease = 0L
    private var sequence = 0L
    private val run = UUID.randomUUID().toString()
    private var delivered = ""
    private var blocked = false

    init {
        viewModelScope.launch {
            settings.observe().collect { result ->
                if (result is DomainResult.Success) {
                    val old = preferences
                    preferences = result.value
                    if (old?.controls != result.value.controls ||
                        old?.calibration != result.value.calibration ||
                        old?.calibrationRotation != result.value.calibrationRotation
                    ) {
                        stopMotion()
                    }
                    mutable.value = state.value.copy(
                        mode = result.value.controls, saving = false, settingsError = false
                    )
                    startIfNeeded()
                } else {
                    stopMotion()
                    mutable.value = state.value.copy(
                        mode = ControlMode.TOUCH, saving = false, settingsError = true
                    )
                }
            }
        }
    }

    fun scene(active: Boolean, shiftId: String, boxId: String, boxReady: Boolean) {
        val changed = shift != shiftId
        if (changed || scene != active) {
            stopMotion()
            blocked = false
        }
        shift = shiftId
        box = boxId
        ready = boxReady
        scene = active
        startIfNeeded()
    }

    fun mode(mode: ControlMode) {
        val current = preferences ?: return
        if (saving?.isActive == true) return
        stopMotion()
        blocked = false
        save(current.copy(controls = mode))
    }

    fun calibrate() {
        if (!scene || saving?.isActive == true) return
        val value = gesture.calibrate() ?: return
        stopMotion()
        preferences?.let {
            save(
                it.copy(
                    calibration = value.horizontal,
                    calibrationRotation = value.rotationDegrees
                )
            )
        }
    }

    fun confirmCenter() {
        if (!scene || !state.value.usesTilt || state.value.saving) return
        gesture.confirmCenter(ready)?.let(::emit)
        mutable.value = state.value.copy(armed = gesture.reading.armed)
    }

    fun accept(event: TiltPlacement): Boolean {
        if (!scene || !ready || event.lease != lease || event.boxId != box ||
            !state.value.usesTilt || event.operationId == delivered || state.value.saving
        ) {
            return false
        }
        delivered = event.operationId
        return true
    }

    private fun save(value: GameSettings) {
        mutable.value = state.value.copy(saving = true)
        saving = viewModelScope.launch {
            when (val result = settings.update(value)) {
                is DomainResult.Success -> {
                    preferences = result.value
                    mutable.value = state.value.copy(mode = result.value.controls, saving = false)
                    startIfNeeded()
                }
                is DomainResult.Failure -> mutable.value = state.value.copy(
                    mode = ControlMode.TOUCH, saving = false, settingsError = true
                )
            }
        }
    }

    private fun startIfNeeded() {
        if (!scene || state.value.mode != ControlMode.TILT || state.value.saving ||
            motionJob?.isActive == true || blocked
        ) {
            return
        }
        if (!source.available) {
            blocked = true
            mutable.value = state.value.copy(motion = MotionStatus.UNAVAILABLE)
            return
        }
        val prefs = preferences ?: return
        gesture.configure(prefs.calibrationRotation?.let { TiltCalibration(prefs.calibration, it) })
        val token = lease
        mutable.value = state.value.copy(motion = MotionStatus.STARTING)
        motionJob = viewModelScope.launch {
            try {
                source.observe().collect { event ->
                    if (lease != token || !scene) return@collect
                    when (event) {
                        MotionEvent.Started -> mutable.value = state.value.copy(
                            motion = MotionStatus.ACTIVE
                        )
                        MotionEvent.Unavailable -> fail(MotionStatus.UNAVAILABLE)
                        MotionEvent.Failure -> fail(MotionStatus.ERROR)
                        is MotionEvent.Sample -> {
                            val reading = gesture.sample(event.value, ready)
                            mutable.value = state.value.copy(
                                calibrated = reading.calibrated,
                                canCalibrate = reading.canCalibrate,
                                armed = reading.armed, direction = reading.direction
                            )
                            reading.zone?.let(::emit)
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                fail(MotionStatus.ERROR)
            } finally {
                if (lease == token) {
                    motionJob = null
                    if (!blocked) {
                        blocked = true
                        mutable.value = state.value.copy(motion = MotionStatus.ERROR)
                    }
                }
            }
        }
    }

    private fun emit(zone: BoxCategory) {
        events.tryEmit(TiltPlacement(box, zone, "tilt:$run:${++sequence}:$box", lease))
    }

    private fun fail(status: MotionStatus) {
        blocked = true
        motionJob?.cancel()
        mutable.value = state.value.copy(motion = status, armed = false, calibrated = false)
    }

    private fun stopMotion() {
        lease++
        motionJob?.cancel()
        motionJob = null
        gesture.reset()
        mutable.value = state.value.copy(
            motion = MotionStatus.STOPPED, armed = false, calibrated = false, canCalibrate = false
        )
    }
}
