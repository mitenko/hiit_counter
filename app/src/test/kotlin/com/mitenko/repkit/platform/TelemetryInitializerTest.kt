package com.mitenko.repkit.platform

import com.mitenko.repkit.data.RepairBreadcrumbs
import com.mitenko.repkit.domain.NoOpCrashReporter
import com.mitenko.repkit.testutil.RecordingCrashReporter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** Spec rev 30 §3: the switch reaches Crashlytics and Analytics (one setEnabled call) at once. */
@OptIn(ExperimentalCoroutinesApi::class)
class TelemetryInitializerTest {
    @After
    fun resetBreadcrumbs() {
        RepairBreadcrumbs.reporter = NoOpCrashReporter
    }

    @Test
    fun `start routes EntryMapping's repair breadcrumbs to the reporter`() = runTest {
        val reporter = RecordingCrashReporter()
        TelemetryInitializer(MutableStateFlow(true), debugBuild = false, sendInDebug = false, reporter = reporter, scope = backgroundScope).start()
        assertSame(reporter, RepairBreadcrumbs.reporter)
    }

    @Test
    fun `a release build follows the switch as it changes`() = runTest {
        val switch = MutableStateFlow(true)
        val reporter = RecordingCrashReporter()
        TelemetryInitializer(switch, debugBuild = false, sendInDebug = false, reporter = reporter, scope = backgroundScope).start()
        runCurrent()
        switch.value = false
        runCurrent()
        switch.value = true
        runCurrent()
        assertEquals(listOf(true, false, true), reporter.enabled)
    }

    @Test
    fun `a debug build without the flag stays off whatever the switch`() = runTest {
        val switch = MutableStateFlow(true)
        val reporter = RecordingCrashReporter()
        TelemetryInitializer(switch, debugBuild = true, sendInDebug = false, reporter = reporter, scope = backgroundScope).start()
        runCurrent()
        switch.value = false
        runCurrent()
        switch.value = true
        runCurrent()
        assertEquals(listOf(false), reporter.enabled)
    }

    @Test
    fun `a debug build with the flag follows the switch`() = runTest {
        val switch = MutableStateFlow(false)
        val reporter = RecordingCrashReporter()
        TelemetryInitializer(switch, debugBuild = true, sendInDebug = true, reporter = reporter, scope = backgroundScope).start()
        runCurrent()
        switch.value = true
        runCurrent()
        assertEquals(listOf(false, true), reporter.enabled)
    }
}
