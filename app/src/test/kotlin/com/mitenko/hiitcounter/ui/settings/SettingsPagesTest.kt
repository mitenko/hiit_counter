package com.mitenko.hiitcounter.ui.settings

import com.mitenko.hiitcounter.domain.model.EntryType
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsPagesTest {
    @Test
    fun `a check-in-only entry keeps only Progression and Current`() {
        assertEquals(SettingsPage.entries.toList(), SettingsPage.visibleFor(EntryType.WORKOUT))
        assertEquals(listOf(SettingsPage.PROGRESSION, SettingsPage.CURRENT), SettingsPage.visibleFor(EntryType.CHECK_IN))
    }

    @Test
    fun `a page argument maps onto the visible tabs, and a hidden page opens the first`() {
        val checkIn = SettingsPage.visibleFor(EntryType.CHECK_IN)
        assertEquals(0, SettingsPage.PROGRESSION.tabIndex(checkIn))
        assertEquals(1, SettingsPage.CURRENT.tabIndex(checkIn))
        assertEquals(0, SettingsPage.TIMING.tabIndex(checkIn))
        assertEquals(0, SettingsPage.CUES.tabIndex(checkIn))
        assertEquals(3, SettingsPage.CUES.tabIndex(SettingsPage.visibleFor(EntryType.WORKOUT)))
    }
}
