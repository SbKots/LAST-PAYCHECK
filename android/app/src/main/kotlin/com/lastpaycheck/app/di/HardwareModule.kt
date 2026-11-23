package com.lastpaycheck.app.di

import android.content.Context
import com.lastpaycheck.data.hardware.AndroidMotionSensorSource
import com.lastpaycheck.data.hardware.AndroidWorkCameraSource
import com.lastpaycheck.domain.repository.MotionSensorSource
import com.lastpaycheck.domain.repository.WorkCameraSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.qualifiers.ApplicationContext

@Module
@InstallIn(ViewModelComponent::class)
object HardwareModule {
    @Provides
    fun motion(@ApplicationContext context: Context): MotionSensorSource =
        AndroidMotionSensorSource(context)

    @Provides
    fun camera(@ApplicationContext context: Context): WorkCameraSource =
        AndroidWorkCameraSource(context)
}
