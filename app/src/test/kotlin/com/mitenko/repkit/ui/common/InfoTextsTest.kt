package com.mitenko.repkit.ui.common

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.repkit.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class InfoTextsTest {
    @Test
    fun `every settings row has a non-blank info text`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fields = R.string::class.java.fields.filter { it.name.startsWith("info_") }
        assertEquals(ROWS.sorted(), fields.map { it.name }.sorted())
        fields.forEach { assertTrue(it.name, context.getString(it.getInt(null)).isNotBlank()) }
    }

    private companion object {
        /** The 20 labelled rows of spec R3 §7.2 in page order, then R4 §6's Type (Entry Settings) and Voice (Cues), then rev 30's crash reports switch (the ⚙ dialog). */
        val ROWS = listOf(
            "info_prepare", "info_sets", "info_work", "info_rest", "info_cooldown", "info_total",
            "info_starting_total", "info_floor", "info_cap", "info_hold", "info_hold_at", "info_hold_for",
            "info_window", "info_penalty_rate",
            "info_total_reps", "info_best_streak", "info_current_streak", "info_last_check_in",
            "info_sound", "info_vibration",
            "info_type", "info_voice",
            "info_crash_reports",
        )
    }
}
