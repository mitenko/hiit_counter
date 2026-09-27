package com.mitenko.hiitcounter.ui.navigation

import com.mitenko.hiitcounter.ui.settings.SettingsPage
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutesTest {
    @Test
    fun `routes follow the spec`() {
        assertEquals("entry/{id}", Routes.ENTRY)
        assertEquals("entry/{id}/settings/timing", Routes.ENTRY_TIMING)
        assertEquals("entry/7", Routes.entry(7))
        assertEquals("entry/7/settings", Routes.entrySettings(7))
        assertEquals(
            listOf("entry/7/settings/timing", "entry/7/settings/progression", "entry/7/settings/current", "entry/7/settings/cues"),
            SettingsPage.entries.map { Routes.settingsPage(7, it) },
        )
    }
}
