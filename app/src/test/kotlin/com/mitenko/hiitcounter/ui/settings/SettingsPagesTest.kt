package com.mitenko.hiitcounter.ui.settings

import com.mitenko.hiitcounter.domain.model.EntryType
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsPagesTest {
    @Test
    fun `every entry, Workout or Timer only, shows all four pages`() {
        assertEquals(SettingsPage.entries.toList(), SettingsPage.visibleFor(EntryType.WORKOUT))
        assertEquals(SettingsPage.entries.toList(), SettingsPage.visibleFor(EntryType.CHECK_IN))
    }

    @Test
    fun `a page argument maps onto its own tab for every type`() {
        val pages = SettingsPage.visibleFor(EntryType.CHECK_IN)
        assertEquals(0, SettingsPage.TIMING.tabIndex(pages))
        assertEquals(1, SettingsPage.PROGRESSION.tabIndex(pages))
        assertEquals(2, SettingsPage.CURRENT.tabIndex(pages))
        assertEquals(3, SettingsPage.CUES.tabIndex(pages))
    }
}
