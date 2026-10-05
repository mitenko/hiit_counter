package com.mitenko.repkit.di

import android.content.Context
import com.mitenko.repkit.domain.CrashReporter
import com.mitenko.repkit.platform.FirebaseCrashReporter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Crash reporting and analytics (spec revision 30): Firebase in the app; tests pass a fake or NoOpCrashReporter. */
@Module
@InstallIn(SingletonComponent::class)
object TelemetryModule {
    @Provides @Singleton
    fun crashReporter(@ApplicationContext context: Context): CrashReporter = FirebaseCrashReporter(context)
}
