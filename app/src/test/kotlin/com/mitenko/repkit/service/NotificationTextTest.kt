package com.mitenko.repkit.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.repkit.domain.model.Phase
import com.mitenko.repkit.domain.model.TimerState
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The notification's English text is unchanged by the move to resources (spec revision 24). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class NotificationTextTest {
    private val text = NotificationText(ApplicationProvider.getApplicationContext<Context>().resources)
    private val work = TimerState(Phase.WORK, set = 2, sets = 8, phaseSecondsLeft = 15, phaseDurationSec = 20,
        elapsedSec = 50, totalDurationSec = 240, repsThisSet = 8, totalReps = 65, paused = false)

    @Test
    fun `notification text`() {
        assertEquals("Burpees · Work · Set 2/8", text.title("Burpees", work))
        assertEquals("Work · Set 2/8", text.title(null, work))
        assertEquals("00:15 left", text.body(work))
        assertEquals("Paused · 00:15 left", text.body(work.copy(paused = true)))
        assertEquals("Workout complete", text.body(work.copy(phase = Phase.DONE)))
        assertEquals(
            listOf("Get ready", "Work", "Rest", "Cooldown", "Done"),
            Phase.entries.map(text::phaseName),
        )
    }

    @Test
    fun `starting title uses the frozen entry name`() {
        assertEquals("Burpees · Starting…", text.startingTitle("Burpees"))
        assertEquals("Starting workout…", text.startingTitle(null))
    }
}
