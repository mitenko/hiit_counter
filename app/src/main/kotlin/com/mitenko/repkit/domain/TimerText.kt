package com.mitenko.repkit.domain

import java.util.Locale

/**
 * Times as digits. These deliberately use [Locale.ENGLISH], not the device locale (spec revision
 * 24): the timer must always show the digits 0–9, whatever the language, so a locale with its own
 * digits never changes the countdown. Words around the times come from resources.
 */
object TimerText {
    fun formatMmSs(sec: Int): String = String.format(Locale.ENGLISH, "%02d:%02d", sec / 60, sec % 60)

    fun formatHms(sec: Int): String =
        String.format(Locale.ENGLISH, "%02d:%02d:%02d", sec / 3600, (sec % 3600) / 60, sec % 60)

    fun formatDuration(sec: Int): String =
        if (sec >= 3600) String.format(Locale.ENGLISH, "%d:%02d:%02d", sec / 3600, (sec % 3600) / 60, sec % 60)
        else formatMmSs(sec)
}
