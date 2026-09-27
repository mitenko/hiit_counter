package com.mitenko.hiitcounter.ui.navigation

import com.mitenko.hiitcounter.ui.common.ENTRY_ID_ARG
import com.mitenko.hiitcounter.ui.settings.SettingsPage

/** Spec §7.2. */
object Routes {
    const val ENTRIES = "entries"
    const val ENTRY = "entry/{$ENTRY_ID_ARG}"
    const val ENTRY_SETTINGS = "entry/{$ENTRY_ID_ARG}/settings"
    const val ENTRY_TIMING = "entry/{$ENTRY_ID_ARG}/settings/timing"
    const val ENTRY_PROGRESSION = "entry/{$ENTRY_ID_ARG}/settings/progression"
    const val ENTRY_CURRENT = "entry/{$ENTRY_ID_ARG}/settings/current"
    const val ENTRY_CUES = "entry/{$ENTRY_ID_ARG}/settings/cues"
    const val TIMER = "timer"

    fun entry(id: Long): String = "entry/$id"

    fun entrySettings(id: Long): String = "entry/$id/settings"

    fun settingsPage(id: Long, page: SettingsPage): String = entrySettings(id) + when (page) {
        SettingsPage.TIMING -> "/timing"
        SettingsPage.PROGRESSION -> "/progression"
        SettingsPage.CURRENT -> "/current"
        SettingsPage.CUES -> "/cues"
    }
}
