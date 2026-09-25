package com.mitenko.hiitcounter.domain

import com.mitenko.hiitcounter.domain.model.Phase
import com.mitenko.hiitcounter.domain.model.TimerState
import java.util.Locale

object TimerText {
    fun formatMmSs(sec: Int): String = String.format(Locale.ENGLISH, "%02d:%02d", sec / 60, sec % 60)

    fun formatHms(sec: Int): String =
        String.format(Locale.ENGLISH, "%02d:%02d:%02d", sec / 3600, (sec % 3600) / 60, sec % 60)

    fun formatDuration(sec: Int): String =
        if (sec >= 3600) String.format(Locale.ENGLISH, "%d:%02d:%02d", sec / 3600, (sec % 3600) / 60, sec % 60)
        else formatMmSs(sec)

    fun phaseName(phase: Phase): String = when (phase) {
        Phase.PREPARE -> "Get ready"
        Phase.WORK -> "Work"
        Phase.REST -> "Rest"
        Phase.COOLDOWN -> "Cooldown"
        Phase.DONE -> "Done"
    }

    fun notificationTitle(s: TimerState): String = "${phaseName(s.phase)} · Set ${s.set}/${s.sets}"

    fun notificationBody(s: TimerState): String = when {
        s.phase == Phase.DONE -> "Workout complete"
        s.paused -> "Paused · ${formatMmSs(s.phaseSecondsLeft)} left"
        else -> "${formatMmSs(s.phaseSecondsLeft)} left"
    }
}
