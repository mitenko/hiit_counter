package com.mitenko.hiitcounter.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** Starts [TimerService]. Returns failure if the platform refuses (e.g. background-start restrictions). */
interface WorkoutServiceStarter {
    fun start(): Result<Unit>
}

class AndroidWorkoutServiceStarter(private val context: Context) : WorkoutServiceStarter {
    override fun start(): Result<Unit> = runCatching {
        ContextCompat.startForegroundService(context, Intent(context, TimerService::class.java))
    }
}
