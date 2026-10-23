package com.mitenko.repkit.domain

import java.util.Locale

/**
 * Times as digits. These deliberately use [Locale.ENGLISH], not the device locale (spec revision
 * 24): the timer must always show the digits 0–9, whatever the language, so a locale with its own
 * digits never changes the countdown. Words around the times come from resources.
 */
object TimerText {
    fun formatMmSs(sec: Int): String = String.format(Locale.ENGLISH, "%02d:%02d", sec / 60, sec % 60)

    /**
     * The timer's Elapsed circle (spec revision 37): mm:ss with no hours; past 59:59 the minutes keep
     * counting ("75:12"), so the value stays five characters wide for any realistic run.
     */
    fun formatElapsed(sec: Int): String = formatMmSs(sec)

    /** Spec rev 42 §3: the prepare countdown as the big number: whole seconds under a minute ("7"), else m:ss ("1:30"). */
    fun formatCountdown(sec: Int): String =
        if (sec < 60) "$sec" else String.format(Locale.ENGLISH, "%d:%02d", sec / 60, sec % 60)

    fun formatDuration(sec: Int): String =
        if (sec >= 3600) String.format(Locale.ENGLISH, "%d:%02d:%02d", sec / 3600, (sec % 3600) / 60, sec % 60)
        else formatMmSs(sec)
}
