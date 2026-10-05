package com.mitenko.repkit

import android.app.Application
import com.mitenko.repkit.data.v1.V1Migrator
import com.mitenko.repkit.platform.TelemetryInitializer
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class HiitApp : Application() {
    @Inject lateinit var migrator: V1Migrator
    @Inject lateinit var telemetry: TelemetryInitializer

    override fun onCreate() {
        super.onCreate()
        // Spec rev 30 §3: crash reports and analytics follow the switch from here on; off until its first read.
        telemetry.start()
        // Spec §6: import any v1 data on Dispatchers.IO, never with runBlocking. Every repository call awaits it.
        migrator.start()
    }
}
