package com.mitenko.repkit.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Spec rev 30 §2: one rule decides both Crashlytics and Analytics collection; the one
 * [CrashReporter.setEnabled] call applies it to both services.
 */
class CrashReporterTest {
    @Test
    fun `release builds follow the switch`() {
        assertEquals(true, telemetryEnabled(debugBuild = false, sendInDebug = false, switchOn = true))
        assertEquals(false, telemetryEnabled(debugBuild = false, sendInDebug = false, switchOn = false))
        assertEquals(true, telemetryEnabled(debugBuild = false, sendInDebug = true, switchOn = true))
        assertEquals(false, telemetryEnabled(debugBuild = false, sendInDebug = true, switchOn = false))
    }

    @Test
    fun `debug builds send nothing without the build flag`() {
        assertEquals(false, telemetryEnabled(debugBuild = true, sendInDebug = false, switchOn = true))
        assertEquals(false, telemetryEnabled(debugBuild = true, sendInDebug = false, switchOn = false))
    }

    @Test
    fun `debug builds with the build flag follow the switch`() {
        assertEquals(true, telemetryEnabled(debugBuild = true, sendInDebug = true, switchOn = true))
        assertEquals(false, telemetryEnabled(debugBuild = true, sendInDebug = true, switchOn = false))
    }

    @Test
    fun `the no-op reporter accepts every call`() {
        NoOpCrashReporter.setEnabled(true)
        NoOpCrashReporter.log("breadcrumb")
        NoOpCrashReporter.recordNonFatal(IllegalStateException("x"), "context")
    }
}
