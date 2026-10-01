package com.mitenko.repkit.ui.settings

import com.mitenko.repkit.R
import com.mitenko.repkit.domain.model.EntryType
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

    @Test
    fun `each page has its own tab name and icon`() {
        assertEquals(
            listOf(R.string.tab_timing, R.string.tab_progression, R.string.tab_current, R.string.tab_cues),
            SettingsPage.entries.map { it.tab },
        )
        assertEquals(
            listOf(R.drawable.ic_tab_timing, R.drawable.ic_tab_progression, R.drawable.ic_tab_current, R.drawable.ic_tab_cues),
            SettingsPage.entries.map { it.icon },
        )
    }
}
