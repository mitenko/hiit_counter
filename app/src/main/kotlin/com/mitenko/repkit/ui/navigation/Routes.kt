package com.mitenko.repkit.ui.navigation

import com.mitenko.repkit.ui.common.ENTRY_ID_ARG
import com.mitenko.repkit.ui.settings.SettingsPage

/** Spec R2 §7.2, with R3 §4's pager route in place of the four settings routes. */
object Routes {
    const val ENTRIES = "entries"
    const val ENTRY = "entry/{$ENTRY_ID_ARG}"
    const val ENTRY_SETTINGS = "entry/{$ENTRY_ID_ARG}/settings"

    /** The pager's optional page, 0–3 (Timing, Progression, Current, Cues), default 0. */
    const val PAGE_ARG = "page"
    const val SETTINGS_PAGES = "entry/{$ENTRY_ID_ARG}/settings/pages?$PAGE_ARG={$PAGE_ARG}"
    const val TIMER = "timer"

    fun entry(id: Long): String = "entry/$id"

    fun entrySettings(id: Long): String = "entry/$id/settings"

    /** Opens the pager at [page]. The signature is unchanged from R2. */
    fun settingsPage(id: Long, page: SettingsPage): String = "${entrySettings(id)}/pages?$PAGE_ARG=${page.ordinal}"
}
