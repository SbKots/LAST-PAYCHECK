package com.lastpaycheck.presentation.hardware

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.lastpaycheck.domain.model.ControlMode
import com.lastpaycheck.domain.model.TiltDirection
import com.lastpaycheck.domain.model.WorkShift
import com.lastpaycheck.presentation.R
import com.lastpaycheck.presentation.game.SectionCard
import com.lastpaycheck.presentation.ui.components.LpButton
import com.lastpaycheck.presentation.ui.components.LpButtonStyle
import com.lastpaycheck.presentation.ui.components.LpCaption
import com.lastpaycheck.presentation.ui.theme.LpSpacing

@Composable
internal fun WorkTiltControls(
    model: WorkTiltViewModel,
    shift: WorkShift,
    enabled: Boolean,
    ready: Boolean,
    onPlacement: (TiltPlacement) -> Unit
) {
    val owner = LocalLifecycleOwner.current
    val state by model.state.collectAsStateWithLifecycle()
    val latestShift by rememberUpdatedState(shift)
    val latestEnabled by rememberUpdatedState(enabled)
    val latestReady by rememberUpdatedState(ready)
    val latestPlacement by rememberUpdatedState(onPlacement)
    fun update() = model.scene(
        owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && latestEnabled,
        latestShift.id,
        latestShift.selectedBox.id,
        latestReady && latestEnabled
    )
    SideEffect { update() }
    DisposableEffect(owner, model) {
        val observer = LifecycleEventObserver { _, _ -> update() }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            model.scene(false, latestShift.id, latestShift.selectedBox.id, false)
        }
    }
    LaunchedEffect(owner, model) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            model.placements.collect { if (model.accept(it)) latestPlacement(it) }
        }
    }
    SectionCard(stringResource(R.string.tilt_control_title)) {
        Row(horizontalArrangement = Arrangement.spacedBy(LpSpacing.sm)) {
            ControlMode.entries.forEach { mode ->
                LpButton(
                    stringResource(
                        if (mode == ControlMode.TOUCH) R.string.tilt_touch else R.string.tilt_mode
                    ),
                    { model.mode(mode) },
                    Modifier.weight(1f).testTag("work.mode.${mode.name}"),
                    if (state.mode == mode) LpButtonStyle.Primary else LpButtonStyle.Secondary,
                    !state.saving
                )
            }
        }
        if (state.settingsError) LpCaption(stringResource(R.string.tilt_settings_error))
        if (state.mode == ControlMode.TILT) {
            val status = when {
                state.motion == MotionStatus.UNAVAILABLE -> R.string.tilt_no_sensor
                state.motion == MotionStatus.ERROR -> R.string.tilt_error
                !enabled -> R.string.tilt_inactive
                !state.calibrated -> R.string.tilt_calibrate_hint
                !state.armed -> R.string.tilt_return_neutral
                else -> R.string.tilt_armed
            }
            LpCaption(stringResource(status), Modifier.testTag("work.tilt.status"))
            if (state.motion == MotionStatus.ACTIVE) {
                LpCaption(
                    stringResource(
                        when (state.direction) {
                            TiltDirection.LEFT -> R.string.hardware_left
                            TiltDirection.NEUTRAL -> R.string.hardware_neutral
                            TiltDirection.RIGHT -> R.string.hardware_right
                        }
                    ),
                    Modifier.testTag("work.tilt.direction")
                )
                LpButton(
                    stringResource(R.string.tilt_calibrate),
                    model::calibrate,
                    Modifier.fillMaxWidth().testTag("work.tilt.calibrate"),
                    enabled = state.canCalibrate && !state.saving
                )
            }
            LpCaption(stringResource(R.string.tilt_mapping))
        }
    }
}
