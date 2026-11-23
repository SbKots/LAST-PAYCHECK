package com.lastpaycheck.domain.repository

import com.lastpaycheck.domain.model.MotionEvent
import kotlinx.coroutines.flow.Flow

interface MotionSensorSource {
    val available: Boolean
    fun observe(): Flow<MotionEvent>
}
