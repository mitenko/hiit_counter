package com.mitenko.repkit.platform

import com.mitenko.repkit.BuildConfig
import com.mitenko.repkit.data.AppPreferences
import com.mitenko.repkit.data.RepairBreadcrumbs
import com.mitenko.repkit.di.ApplicationScope
import com.mitenko.repkit.domain.CrashReporter
import com.mitenko.repkit.domain.telemetryEnabled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Spec rev 30 §3: follows the "Share crash reports and usage" switch for the life of the process,
 * applying [telemetryEnabled] to Crashlytics and Analytics whenever it changes. HiitApp calls
 * [start] once; DataStore reads off the main thread, so nothing blocks.
 */
@Singleton
class TelemetryInitializer internal constructor(
    private val switchOn: Flow<Boolean>,
    private val debugBuild: Boolean,
    private val sendInDebug: Boolean,
    private val reporter: CrashReporter,
    private val scope: CoroutineScope,
) {
    @Inject constructor(preferences: AppPreferences, reporter: CrashReporter, @ApplicationScope scope: CoroutineScope) :
        this(preferences.crashReportsEnabled, BuildConfig.DEBUG, BuildConfig.CRASHLYTICS_IN_DEBUG, reporter, scope)

    fun start() {
        // Spec rev 30 §4: EntryMapping's read repairs leave breadcrumbs through this reporter.
        RepairBreadcrumbs.reporter = reporter
        scope.launch {
            switchOn
                .map { telemetryEnabled(debugBuild, sendInDebug, it) }
                .distinctUntilChanged()
                .collect { reporter.setEnabled(it) }
        }
    }
}
