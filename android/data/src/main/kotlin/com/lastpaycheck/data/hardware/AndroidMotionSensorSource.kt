package com.lastpaycheck.data.hardware

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.WindowManager
import com.lastpaycheck.domain.model.AccelerationSample
import com.lastpaycheck.domain.model.MotionEvent
import com.lastpaycheck.domain.repository.MotionSensorSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class AndroidMotionSensorSource(context: Context) : MotionSensorSource {
    private val manager = context.getSystemService(SensorManager::class.java)
    private val windows = context.getSystemService(WindowManager::class.java)
    override val available: Boolean
        get() = runCatching {
            manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
        }.getOrDefault(false)

    override fun observe(): Flow<MotionEvent> = callbackFlow {
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensor == null) {
            trySend(MotionEvent.Unavailable)
            close()
            return@callbackFlow
        }
        var lastTimestamp = -1L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.values.size < 3 || event.timestamp - lastTimestamp < 100_000_000) return
                lastTimestamp = event.timestamp
                @Suppress("DEPRECATION")
                val rotation = (windows?.defaultDisplay?.rotation ?: 0) * 90
                trySend(
                    MotionEvent.Sample(
                        AccelerationSample(
                            event.values[0].toDouble(),
                            event.values[1].toDouble(),
                            event.values[2].toDouble(),
                            event.timestamp,
                            rotation
                        )
                    )
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        try {
            if (manager.registerListener(listener, sensor, 50_000)) {
                trySend(MotionEvent.Started)
            } else {
                trySend(MotionEvent.Failure)
                close()
            }
        } catch (_: RuntimeException) {
            trySend(MotionEvent.Failure)
            close()
        }
        awaitClose { manager.unregisterListener(listener) }
    }
}
