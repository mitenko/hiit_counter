package com.mitenko.repkit.domain

/**
 * Crash reporting and analytics (spec revision 30). The production binding wraps Firebase
 * Crashlytics and Analytics; JVM tests use [NoOpCrashReporter] or a fake, so Firebase never loads.
 * Nothing passed here may carry workout data, entry names or history (spec rev 30 §4).
 */
interface CrashReporter {
    /** Turns Crashlytics and Analytics collection on or off together, at once. */
    fun setEnabled(enabled: Boolean)

    /** A failure the app caught and carried on from, reported without crashing. */
    fun recordNonFatal(t: Throwable, context: String)

    /** A breadcrumb attached to the next crash or non-fatal report. */
    fun log(message: String)
}

/** Does nothing: the default in tests and in code built without the Hilt graph. */
object NoOpCrashReporter : CrashReporter {
    override fun setEnabled(enabled: Boolean) = Unit

    override fun recordNonFatal(t: Throwable, context: String) = Unit

    override fun log(message: String) = Unit
}

/**
 * Spec rev 30 §2: whether Crashlytics and Analytics may collect. Debug builds send nothing unless
 * built with `-PcrashlyticsInDebug=true` ([sendInDebug]); in every case the user's switch must be on.
 */
fun telemetryEnabled(debugBuild: Boolean, sendInDebug: Boolean, switchOn: Boolean): Boolean =
    (!debugBuild || sendInDebug) && switchOn
