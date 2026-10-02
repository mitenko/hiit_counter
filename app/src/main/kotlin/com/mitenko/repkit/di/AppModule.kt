package com.mitenko.repkit.di

import android.content.Context
import com.mitenko.repkit.data.SessionRecorder
import com.mitenko.repkit.domain.Clock
import com.mitenko.repkit.domain.TimerController
import com.mitenko.repkit.platform.AndroidClock
import com.mitenko.repkit.platform.AndroidVoiceAvailability
import com.mitenko.repkit.platform.VoiceAvailability
import com.mitenko.repkit.service.AndroidWorkoutServiceStarter
import com.mitenko.repkit.service.WorkoutServiceStarter
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

    /** The recorder is the controller's RunLog (spec revision 17 §3): every ended run reaches it by a direct call. */
    @Provides @Singleton
    fun timerController(@ApplicationScope scope: CoroutineScope, clock: Clock, recorder: SessionRecorder): TimerController =
        TimerController(scope, wallNow = clock::now, runLog = recorder, nowMs = clock::elapsedRealtimeMs)

    @Provides @Singleton
    fun workoutServiceStarter(@ApplicationContext context: Context): WorkoutServiceStarter =
        AndroidWorkoutServiceStarter(context)

    /** The Cues page's device check (spec R4 §4.7). */
    @Provides @Singleton
    fun voiceAvailability(@ApplicationContext context: Context): VoiceAvailability = AndroidVoiceAvailability(context)
}
