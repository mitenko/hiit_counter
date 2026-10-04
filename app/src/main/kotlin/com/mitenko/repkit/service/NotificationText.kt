package com.mitenko.repkit.service

import android.content.res.Resources
import com.mitenko.repkit.R
import com.mitenko.repkit.domain.TimerText
import com.mitenko.repkit.domain.model.Phase
import com.mitenko.repkit.domain.model.TimerState

/**
 * The workout notification's text (spec §7.6), from resources (spec revision 24). The phase and
 * the numbers come from the timer state; [Resources] is read per call, so a locale change applies
 * to the next update.
 */
class NotificationText(private val res: Resources) {
    fun phaseName(phase: Phase): String = res.getString(
        when (phase) {
            Phase.PREPARE -> R.string.notification_phase_prepare
            Phase.WORK -> R.string.notification_phase_work
            Phase.REST -> R.string.notification_phase_rest
            Phase.COOLDOWN -> R.string.notification_phase_cooldown
            Phase.DONE -> R.string.notification_phase_done
        },
    )

    /** `"<entryName> · <Phase> · Set n/N"` with the name frozen in the run's snapshot (spec §7.6). */
    fun title(entryName: String?, s: TimerState): String {
        val phaseSet = res.getString(R.string.notification_phase_set, phaseName(s.phase), s.set, s.sets)
        return if (entryName != null) res.getString(R.string.notification_named, entryName, phaseSet) else phaseSet
    }

    /** The first notification, before any timer state: `"<entryName> · Starting…"`. */
    fun startingTitle(entryName: String?): String =
        if (entryName != null) res.getString(R.string.notification_starting_named, entryName)
        else res.getString(R.string.notification_starting)

    fun body(s: TimerState): String = when {
        s.phase == Phase.DONE -> res.getString(R.string.notification_complete)
        s.paused -> res.getString(R.string.notification_paused_left, TimerText.formatMmSs(s.phaseSecondsLeft))
        else -> res.getString(R.string.notification_left, TimerText.formatMmSs(s.phaseSecondsLeft))
    }
}
