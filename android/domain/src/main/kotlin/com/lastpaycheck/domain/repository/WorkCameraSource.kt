package com.lastpaycheck.domain.repository

import com.lastpaycheck.domain.model.CameraResult

interface WorkCameraSource {
    val available: Boolean
    suspend fun capture(): CameraResult
}
