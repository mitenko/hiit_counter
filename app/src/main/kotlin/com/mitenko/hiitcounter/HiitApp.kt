package com.mitenko.hiitcounter

import android.app.Application
import com.mitenko.hiitcounter.data.v1.V1Migrator
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class HiitApp : Application() {
    @Inject lateinit var migrator: V1Migrator

    override fun onCreate() {
        super.onCreate()
        // Spec §6: import any v1 data on Dispatchers.IO, never with runBlocking. Every repository call awaits it.
        migrator.start()
    }
}
