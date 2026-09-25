package com.mitenko.hiitcounter.di

import android.content.Context
import com.mitenko.hiitcounter.domain.Clock
import com.mitenko.hiitcounter.domain.TimerController
import com.mitenko.hiitcounter.platform.AndroidClock
import com.mitenko.hiitcounter.service.AndroidWorkoutServiceStarter
import com.mitenko.hiitcounter.service.WorkoutServiceStarter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun clock(): Clock = AndroidClock

    @Provides @Singleton @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Provides @Singleton
    fun timerController(@ApplicationScope scope: CoroutineScope, clock: Clock): TimerController =
        TimerController(scope, clock::elapsedRealtimeMs)

    @Provides @Singleton
    fun workoutServiceStarter(@ApplicationContext context: Context): WorkoutServiceStarter =
        AndroidWorkoutServiceStarter(context)
}
