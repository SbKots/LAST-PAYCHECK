package com.lastpaycheck.presentation.hardware

import com.lastpaycheck.domain.model.AccelerationSample
import com.lastpaycheck.domain.model.CameraResult
import com.lastpaycheck.domain.model.MotionEvent
import com.lastpaycheck.domain.repository.MotionSensorSource
import com.lastpaycheck.domain.repository.WorkCameraSource
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeMotionSensorSource(override var available: Boolean = true) : MotionSensorSource {
    var active = false
    var stops = 0
    var throwOnObserve = false
    private val events = Channel<MotionEvent>(Channel.UNLIMITED)

    fun sample(x: Double, y: Double, z: Double, timestamp: Long) {
        events.trySend(MotionEvent.Sample(AccelerationSample(x, y, z, timestamp)))
    }

    fun fail() {
        events.trySend(MotionEvent.Failure)
        events.close()
    }

    override fun observe(): Flow<MotionEvent> = flow {
        if (throwOnObserve) error("Sensor disconnected")
        if (!available) {
            emit(MotionEvent.Unavailable)
            return@flow
        }
        active = true
        try {
            emit(MotionEvent.Started)
            for (event in events) emit(event)
        } finally {
            active = false
            stops++
        }
    }
}

class FakeWorkCameraSource(override var available: Boolean = true) : WorkCameraSource {
    var result: CameraResult = CameraResult.Frame(byteArrayOf(1, 2, 3), 90)
    var captures = 0
    var fail = false
    var waitForCancellation = false
    var active = false
    var released = 0
    override suspend fun capture(): CameraResult {
        captures++
        active = true
        try {
            if (fail) error("Camera disconnected")
            if (waitForCancellation) awaitCancellation()
            return result
        } finally {
            active = false
            released++
        }
    }
}
