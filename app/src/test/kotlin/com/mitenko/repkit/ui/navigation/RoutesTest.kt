package com.mitenko.repkit.ui.navigation

import com.mitenko.repkit.ui.settings.SettingsPage
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutesTest {
    @Test
    fun `routes follow the spec`() {
        assertEquals("entry/{id}", Routes.ENTRY)
        assertEquals("entry/{id}/settings/pages?page={page}", Routes.SETTINGS_PAGES)
        assertEquals("entry/7", Routes.entry(7))
        assertEquals("entry/7/settings", Routes.entrySettings(7))
        assertEquals(
            listOf(
                "entry/7/settings/pages?page=0",
                "entry/7/settings/pages?page=1",
                "entry/7/settings/pages?page=2",
                "entry/7/settings/pages?page=3",
            ),
            SettingsPage.entries.map { Routes.settingsPage(7, it) },
        )
    }
}
