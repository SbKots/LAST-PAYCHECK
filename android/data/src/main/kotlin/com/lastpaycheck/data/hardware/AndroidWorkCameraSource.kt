package com.lastpaycheck.data.hardware

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.view.WindowManager
import com.lastpaycheck.domain.model.CameraResult
import com.lastpaycheck.domain.repository.WorkCameraSource
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class AndroidWorkCameraSource(private val context: Context) : WorkCameraSource {
    private val manager = context.getSystemService(CameraManager::class.java)
    override val available: Boolean
        get() = try {
            manager?.cameraIdList?.isNotEmpty() == true
        } catch (_: Exception) {
            false
        }

    @SuppressLint("MissingPermission")
    override suspend fun capture(): CameraResult = withContext(Dispatchers.IO) {
        if (context.checkSelfPermission(Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return@withContext CameraResult.PermissionDenied
        }
        if (!available) return@withContext CameraResult.Unavailable
        try {
            withTimeoutOrNull(10_000) { captureFrame() } ?: CameraResult.Failure
        } catch (_: SecurityException) {
            CameraResult.PermissionDenied
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            CameraResult.Failure
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private suspend fun captureFrame(): CameraResult = suspendCancellableCoroutine { continuation ->
        val thread = HandlerThread("work-camera").apply { start() }
        val handler = Handler(thread.looper)
        val done = AtomicBoolean(false)
        var device: CameraDevice? = null
        var session: CameraCaptureSession? = null
        var reader: ImageReader? = null
        var opening = false
        fun cleanup() {
            runCatching { session?.close() }
            runCatching { device?.close() }
            runCatching { reader?.close() }
            if (!opening) thread.quitSafely()
        }
        fun finish(result: CameraResult) {
            if (done.compareAndSet(false, true)) {
                cleanup()
                if (continuation.isActive) continuation.resume(result)
            }
        }
        continuation.invokeOnCancellation {
            if (done.compareAndSet(false, true)) handler.post { cleanup() }
        }
        handler.post {
            if (done.get()) return@post
            try {
                val cameras = checkNotNull(manager)
                val id = cameras.cameraIdList.firstOrNull {
                    cameras.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) ==
                        CameraCharacteristics.LENS_FACING_BACK
                } ?: cameras.cameraIdList.first()
                val characteristics = cameras.getCameraCharacteristics(id)
                val sizes = characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
                )?.getOutputSizes(ImageFormat.JPEG).orEmpty()
                val size = sizes.filter { it.width <= 1280 && it.height <= 1280 }
                    .maxByOrNull { it.width * it.height } ?: sizes.minBy { it.width * it.height }
                val windows = context.getSystemService(WindowManager::class.java)
                val displayDegrees = (windows?.defaultDisplay?.rotation ?: 0) * 90
                val sensorDegrees = characteristics.get(
                    CameraCharacteristics.SENSOR_ORIENTATION
                ) ?: 0
                val front = characteristics.get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_FRONT
                val rotation = (
                    sensorDegrees + (if (front) displayDegrees else -displayDegrees) +
                        360
                    ) % 360
                val output = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 2)
                reader = output
                output.setOnImageAvailableListener({ images ->
                    try {
                        val image = images.acquireLatestImage()
                            ?: return@setOnImageAvailableListener
                        val bytes = image.use {
                            val buffer = it.planes[0].buffer
                            ByteArray(buffer.remaining()).also(buffer::get)
                        }
                        finish(CameraResult.Frame(bytes, rotation))
                    } catch (_: Exception) {
                        finish(CameraResult.Failure)
                    }
                }, handler)
                val captureCallback = object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureFailed(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        failure: CaptureFailure
                    ) {
                        finish(CameraResult.Failure)
                    }
                }
                opening = true
                cameras.openCamera(
                    id,
                    object : CameraDevice.StateCallback() {
                        override fun onOpened(camera: CameraDevice) {
                            opening = false
                            if (done.get()) {
                                camera.close()
                                thread.quitSafely()
                                return
                            }
                            device = camera
                            try {
                                camera.createCaptureSession(
                                    listOf(output.surface),
                                    object : CameraCaptureSession.StateCallback() {
                                        override fun onConfigured(
                                            configured: CameraCaptureSession
                                        ) {
                                            if (done.get()) {
                                                configured.close()
                                                return
                                            }
                                            session = configured
                                            try {
                                                val request = camera.createCaptureRequest(
                                                    CameraDevice.TEMPLATE_STILL_CAPTURE
                                                ).apply {
                                                    addTarget(output.surface)
                                                    set(CaptureRequest.JPEG_ORIENTATION, 0)
                                                }.build()
                                                configured.capture(
                                                    request,
                                                    captureCallback,
                                                    handler
                                                )
                                            } catch (_: Exception) {
                                                finish(CameraResult.Failure)
                                            }
                                        }

                                        override fun onConfigureFailed(
                                            session: CameraCaptureSession
                                        ) {
                                            session.close()
                                            finish(CameraResult.Failure)
                                        }
                                    },
                                    handler
                                )
                            } catch (_: Exception) {
                                finish(CameraResult.Failure)
                            }
                        }

                        override fun onDisconnected(camera: CameraDevice) {
                            opening = false
                            camera.close()
                            finish(CameraResult.Failure)
                            thread.quitSafely()
                        }

                        override fun onError(camera: CameraDevice, error: Int) {
                            opening = false
                            camera.close()
                            finish(CameraResult.Failure)
                            thread.quitSafely()
                        }
                    },
                    handler
                )
            } catch (_: SecurityException) {
                opening = false
                finish(CameraResult.PermissionDenied)
            } catch (_: Exception) {
                opening = false
                finish(CameraResult.Failure)
            }
        }
    }
}
