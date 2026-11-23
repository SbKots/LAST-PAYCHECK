package com.lastpaycheck.presentation.hardware

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lastpaycheck.domain.model.CameraResult
import com.lastpaycheck.domain.model.GameSession
import com.lastpaycheck.domain.model.TiltDirection
import com.lastpaycheck.presentation.R
import com.lastpaycheck.presentation.game.GameBackButton
import com.lastpaycheck.presentation.game.SectionCard
import com.lastpaycheck.presentation.game.SectionPage
import com.lastpaycheck.presentation.ui.components.LpBody
import com.lastpaycheck.presentation.ui.components.LpButton
import com.lastpaycheck.presentation.ui.components.LpCaption

@Composable
internal fun HardwareScreen(session: GameSession, model: HardwareViewModel, onBack: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    var requested by rememberSaveable { mutableStateOf(false) }
    val refreshPermission = {
        model.updatePermission(
            context.checkSelfPermission(Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
            activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == true,
            requested
        )
    }
    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshPermission() }
    val latestRefresh by rememberUpdatedState(refreshPermission)
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, model) {
        latestRefresh()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) latestRefresh()
            if (event == Lifecycle.Event.ON_STOP) model.stop()
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            model.stop()
        }
    }
    SectionPage(
        stringResource(R.string.hardware_title),
        stringResource(R.string.hardware_subtitle),
        session,
        onBack,
        { GameBackButton(onBack) },
        screenTag = "screen.hardware"
    ) {
        SectionCard(stringResource(R.string.hardware_motion)) {
            LpCaption(stringResource(R.string.hardware_motion_hint))
            LpBody(
                stringResource(
                    when (state.motionStatus) {
                        MotionStatus.STOPPED -> R.string.hardware_stopped
                        MotionStatus.STARTING -> R.string.hardware_starting
                        MotionStatus.ACTIVE -> R.string.hardware_active
                        MotionStatus.UNAVAILABLE -> R.string.hardware_no_sensor
                        MotionStatus.ERROR -> R.string.hardware_sensor_error
                    }
                ),
                Modifier.testTag("hardware.motion.status")
            )
            state.reading?.let { reading ->
                LpCaption(
                    stringResource(
                        R.string.hardware_axes,
                        reading.sample.x,
                        reading.sample.y,
                        reading.sample.z,
                        reading.sample.rotationDegrees
                    ),
                    Modifier.testTag("hardware.axes")
                )
                LpBody(
                    stringResource(
                        when (reading.direction) {
                            TiltDirection.LEFT -> R.string.hardware_left
                            TiltDirection.NEUTRAL -> R.string.hardware_neutral
                            TiltDirection.RIGHT -> R.string.hardware_right
                        }
                    ),
                    Modifier.testTag("hardware.direction")
                )
                LpCaption(stringResource(R.string.hardware_normalized, reading.horizontal))
            }
            state.prepared?.let {
                LpCaption(stringResource(R.string.hardware_prepared, it.sequence))
            }
            val enabled = state.motionStatus == MotionStatus.ACTIVE ||
                state.motionStatus == MotionStatus.STARTING
            LpButton(
                stringResource(if (enabled) R.string.hardware_stop else R.string.hardware_enable),
                { if (enabled) model.disableMotion() else model.enableMotion() },
                Modifier.fillMaxWidth().testTag("hardware.motion.toggle")
            )
        }
        SectionCard(stringResource(R.string.hardware_camera)) {
            LpCaption(stringResource(R.string.hardware_camera_rationale))
            if (!state.cameraAvailable) {
                LpBody(stringResource(R.string.hardware_no_camera))
            } else if (state.permission != CameraPermission.GRANTED) {
                if (state.permission == CameraPermission.RATIONALE ||
                    state.permission == CameraPermission.DENIED
                ) {
                    LpCaption(stringResource(R.string.hardware_permission_denied))
                }
                LpButton(
                    stringResource(R.string.hardware_permission_request),
                    {
                        requested = true
                        permission.launch(Manifest.permission.CAMERA)
                    },
                    Modifier.fillMaxWidth().testTag("hardware.camera.permission"),
                    enabled = state.permission != CameraPermission.DENIED
                )
            } else {
                LpButton(
                    stringResource(
                        if (state.cameraBusy) {
                            R.string.hardware_capturing
                        } else {
                            R.string.hardware_capture
                        }
                    ),
                    model::capture,
                    Modifier.fillMaxWidth().testTag("hardware.camera.capture"),
                    enabled = !state.cameraBusy
                )
            }
            when (val result = state.cameraResult) {
                null -> Unit
                CameraResult.PermissionDenied -> LpCaption(
                    stringResource(R.string.hardware_permission_denied)
                )
                CameraResult.Unavailable -> LpCaption(stringResource(R.string.hardware_no_camera))
                CameraResult.Failure -> LpCaption(stringResource(R.string.hardware_camera_error))
                is CameraResult.Frame -> CameraImage(result)
            }
        }
    }
}

@Composable
private fun CameraImage(frame: CameraResult.Frame) {
    val bitmap = remember(frame) {
        BitmapFactory.decodeByteArray(frame.jpeg, 0, frame.jpeg.size)?.let { raw ->
            Bitmap.createBitmap(
                raw,
                0,
                0,
                raw.width,
                raw.height,
                Matrix().apply { postRotate(frame.rotationDegrees.toFloat()) },
                true
            ).also { if (it !== raw) raw.recycle() }
        }
    }
    DisposableEffect(bitmap) { onDispose { bitmap?.recycle() } }
    if (bitmap != null) {
        Image(
            bitmap.asImageBitmap(),
            stringResource(R.string.hardware_frame),
            Modifier.fillMaxWidth().testTag("hardware.camera.frame")
        )
        LpCaption(stringResource(R.string.hardware_frame_hint))
    } else {
        LpCaption(stringResource(R.string.hardware_camera_error))
    }
}
