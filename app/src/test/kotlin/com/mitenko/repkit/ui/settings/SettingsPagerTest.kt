package com.mitenko.repkit.ui.settings

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.repkit.R
import com.mitenko.repkit.domain.model.CounterState
import com.mitenko.repkit.domain.model.EntryType
import com.mitenko.repkit.testutil.FakeClock
import com.mitenko.repkit.testutil.FakeEntryRepository
import com.mitenko.repkit.testutil.FakeVoiceAvailability
import com.mitenko.repkit.testutil.testEntry
import com.mitenko.repkit.ui.common.ENTRY_ID_ARG
import com.mitenko.repkit.ui.theme.HiitTheme
import kotlinx.coroutines.MainScope
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class SettingsPagerTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val repo = FakeEntryRepository(listOf(testEntry(1, name = "Burpees")))
    private var backs = 0
    private var gone = 0

    private fun handle() = SavedStateHandle(mapOf(ENTRY_ID_ARG to 1L))

    /** The last [show]'s Current page ViewModel, so a test can flush its pending save. */
    private lateinit var currentVm: CurrentStateViewModel

    /** The last [show]'s Progression page ViewModel, so a test can flush its pending save. */
    private lateinit var progressionVm: ProgressionSettingsViewModel

    private fun checkInRepo() = FakeEntryRepository(listOf(testEntry(1, name = "Stretch", type = EntryType.CHECK_IN)))

    private fun show(initial: SettingsPage = SettingsPage.TIMING, repo: FakeEntryRepository = this.repo) {
        val appScope = MainScope()
        val pagerVm = SettingsPagerViewModel(handle(), repo)
        val timingVm = TimingSettingsViewModel(handle(), repo, appScope)
        val progressionVm = ProgressionSettingsViewModel(handle(), repo, appScope).also { this.progressionVm = it }
        val currentVm = CurrentStateViewModel(handle(), repo, FakeClock(), appScope).also { this.currentVm = it }
        val cuesVm = CuesSettingsViewModel(handle(), repo, FakeVoiceAvailability())
        compose.setContent {
            HiitTheme {
                SettingsPagerRoute(
                    initialPage = initial, onBack = { backs++ }, onEntryGone = { gone++ },
                    pagerVm = pagerVm, timingVm = timingVm, progressionVm = progressionVm,
                    currentVm = currentVm, cuesVm = cuesVm,
                )
            }
        }
    }

    /** Spec rev 9 §4: the tabs are icons, so tests find them by content description (their names). */
    private fun tab(page: SettingsPage) = compose.onNodeWithContentDescription(text(page.tab))

    private fun text(@StringRes id: Int): String = ApplicationProvider.getApplicationContext<Context>().getString(id)

    /** A labelled row and its info text (spec R3 §7.2). [scrolls] is false for the TOTAL footer. */
    private class InfoRow(
        @StringRes val label: Int,
        @StringRes val info: Int,
        val scrolls: Boolean = true,
        /** The spoken name when it differs from the visible label (each hold's rows, rev 16 §6). */
        val describedAs: String? = null,
    )

    private val rows = mapOf(
        SettingsPage.TIMING to listOf(
            InfoRow(R.string.prepare, R.string.info_prepare),
            InfoRow(R.string.sets, R.string.info_sets),
            InfoRow(R.string.work, R.string.info_work),
            InfoRow(R.string.rest, R.string.info_rest),
            InfoRow(R.string.cooldown, R.string.info_cooldown),
            InfoRow(R.string.total_label, R.string.info_total, scrolls = false),
        ),
        SettingsPage.PROGRESSION to listOf(
            InfoRow(R.string.starting_total, R.string.info_starting_total),
            InfoRow(R.string.floor, R.string.info_floor),
            InfoRow(R.string.cap, R.string.info_cap),
            InfoRow(R.string.hold, R.string.info_hold),
            InfoRow(R.string.hold_at, R.string.info_hold_at, describedAs = "Hold 1 at"),
            InfoRow(R.string.hold_for, R.string.info_hold_for, describedAs = "Hold 1 for"),
            InfoRow(R.string.window_hours, R.string.info_window),
            InfoRow(R.string.penalty_rate, R.string.info_penalty_rate),
        ),
        SettingsPage.CURRENT to listOf(
            InfoRow(R.string.current_total, R.string.info_total_reps),
            InfoRow(R.string.best_streak_field, R.string.info_best_streak),
            InfoRow(R.string.current_streak_field, R.string.info_current_streak),
            InfoRow(R.string.last_check_in_field, R.string.info_last_check_in),
        ),
        SettingsPage.CUES to listOf(
            InfoRow(R.string.sound, R.string.info_sound),
            InfoRow(R.string.vibration, R.string.info_vibration),
            InfoRow(R.string.voice, R.string.info_voice),
        ),
    )

    @Test
    fun `the title shows the entry name`() {
        show()
        compose.onNodeWithText("Burpees settings").assertIsDisplayed()
    }

    @Test
    fun `tapping a tab changes the page`() {
        show()
        tab(SettingsPage.TIMING).assertIsSelected()
        tab(SettingsPage.PROGRESSION).performClick()
        tab(SettingsPage.PROGRESSION).assertIsSelected()
        compose.onNodeWithTag("value_Starting reps").assertIsDisplayed()
    }

    @Test
    fun `swiping changes the page and the selected tab`() {
        show()
        // Fallback if Robolectric's default swipe is too short or too fast to settle on the next page:
        // performTouchInput { swipeLeft(startX = right * 0.9f, endX = left, durationMillis = 400) }
        compose.onNodeWithTag("settings_pager").performTouchInput { swipeLeft() }
        tab(SettingsPage.PROGRESSION).assertIsSelected()
        compose.onNodeWithTag("value_Starting reps").assertIsDisplayed()
    }

    @Test
    fun `page 2 opens on Current`() {
        show(initial = SettingsPage.CURRENT)
        tab(SettingsPage.CURRENT).assertIsSelected()
        compose.onNodeWithTag("value_Current reps").assertIsDisplayed()
    }

    @Test
    fun `changing page flushes a pending save`() {
        show()
        compose.onNodeWithContentDescription("Increase SETS").performScrollTo().performClick()
        tab(SettingsPage.CUES).performClick()
        compose.runOnIdle {
            assertEquals(1, repo.timingWrites)
            assertEquals(9, repo.find(1).timing.sets)
        }
    }

    @Test
    fun `Current raising the cap shows on the already loaded Progression page, which never writes its stale cap back`() {
        val repo = FakeEntryRepository(listOf(testEntry(1, counter = CounterState(total = 79))))
        show(initial = SettingsPage.PROGRESSION, repo = repo)
        compose.onNodeWithTag("value_Maximum reps").performScrollTo().assertTextEquals("72")
        tab(SettingsPage.CURRENT).performClick()
        compose.onNodeWithContentDescription("Increase Current reps").performScrollTo().performClick()
        compose.runOnIdle { currentVm.flush() }
        compose.onNodeWithTag("range_note").assertTextEquals("Maximum reps raised to 80")
        compose.runOnIdle { assertEquals(80, repo.find(1).progression.cap) }

        tab(SettingsPage.PROGRESSION).performClick()
        compose.onNodeWithTag("value_Maximum reps").performScrollTo().assertTextEquals("80")
        compose.runOnIdle { assertEquals(0, repo.progressionWrites) }
        // A later Progression edit saves on top of the new cap, not the stale one.
        compose.onNodeWithContentDescription("Increase Starting reps").performScrollTo().performClick()
        tab(SettingsPage.CUES).performClick()
        compose.runOnIdle {
            assertEquals(1, repo.progressionWrites)
            assertEquals(80, repo.find(1).progression.cap)
            assertEquals(49, repo.find(1).progression.startingTotal)
        }
    }

    @Test
    fun `raising the minimum moves starting reps at once and the current total on save, with one note, gone after a page change`() {
        show(initial = SettingsPage.PROGRESSION)
        compose.onNodeWithContentDescription("Increase Minimum reps").performScrollTo().performClick()
        compose.onNodeWithTag("value_Starting reps").performScrollTo().assertTextEquals("49")
        compose.onNodeWithTag("progression_note").performScrollTo().assertTextEquals("Starting reps raised to 49")
        compose.runOnIdle { progressionVm.flush() }
        compose.onNodeWithTag("progression_note").performScrollTo()
            .assertTextEquals("Starting reps raised to 49 · Current reps raised to 49")
        compose.runOnIdle {
            assertEquals(49, repo.find(1).progression.startingTotal)
            assertEquals(49, repo.find(1).counter.total)
        }
        tab(SettingsPage.CURRENT).performClick()
        compose.onNodeWithTag("value_Current reps").performScrollTo().assertTextEquals("49")
        tab(SettingsPage.PROGRESSION).performClick()
        compose.onNodeWithTag("value_Minimum reps").performScrollTo().assertTextEquals("49")
        compose.onNodeWithTag("progression_note").assertDoesNotExist()
    }

    @Test
    fun `raising the current streak past the best raises the best at once with a note`() {
        show(initial = SettingsPage.CURRENT)
        compose.onNodeWithContentDescription("Increase Current streak").performScrollTo().performClick()
        compose.onNodeWithTag("value_Best streak").performScrollTo().assertTextEquals("1")
        compose.onNodeWithTag("streak_note").performScrollTo().assertTextEquals("Best streak raised to 1")
        tab(SettingsPage.CUES).performClick()
        compose.runOnIdle { assertEquals(1, repo.find(1).counter.bestStreak) }
        tab(SettingsPage.CURRENT).performClick()
        compose.onNodeWithTag("value_Current streak").assertIsDisplayed()
        compose.onNodeWithTag("streak_note").assertDoesNotExist()
    }

    @Test
    fun `leaving the Current page hides its range note`() {
        show(initial = SettingsPage.CURRENT)
        compose.onNodeWithContentDescription("Decrease Current reps").performScrollTo().performClick()
        compose.runOnIdle { currentVm.flush() }
        compose.onNodeWithTag("range_note").assertTextEquals("Minimum reps lowered to 47")
        tab(SettingsPage.CUES).performClick()
        tab(SettingsPage.CURRENT).performClick()
        compose.onNodeWithTag("value_Current reps").assertIsDisplayed()
        compose.onNodeWithTag("range_note").assertDoesNotExist()
    }

    @Test
    fun `the app going to the background flushes a pending save`() {
        show()
        compose.onNodeWithContentDescription("Increase SETS").performScrollTo().performClick()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED) // ON_STOP
        assertEquals(9, repo.find(1).timing.sets)
        assertEquals(0, backs)
    }

    @Test
    fun `the back arrow and system back both flush a pending save before leaving`() {
        show()
        compose.onNodeWithContentDescription("Increase SETS").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitForIdle()
        assertEquals(9, repo.find(1).timing.sets)
        // Spec revision 32: a Sets change asks first; Keep progress lets the exit continue.
        compose.onNodeWithTag("keep_progress").performClick()
        compose.waitForIdle()
        assertEquals(1, backs)
        compose.onNodeWithContentDescription("Increase SETS").performScrollTo().performClick()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals(10, repo.find(1).timing.sets)
        compose.onNodeWithTag("keep_progress").performClick()
        compose.waitForIdle()
        assertEquals(2, backs)
    }

    @Test
    fun `back without a Sets change leaves at once`() {
        show()
        compose.onNodeWithContentDescription("Increase WORK").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitForIdle()
        assertEquals(1, backs)
        compose.onNodeWithText(text(R.string.sets_changed_title)).assertDoesNotExist()
    }

    @Test
    fun `changing Sets then switching tabs asks whether to reset progress`() {
        show()
        compose.onNodeWithContentDescription("Increase SETS").performScrollTo().performClick()
        tab(SettingsPage.CUES).performClick()
        compose.onNodeWithText("Sets changed").assertIsDisplayed()
        compose.onNodeWithText("You changed sets from 8 to 9. Reset progress so your reps start again from the starting total?")
            .assertIsDisplayed()
        compose.onNodeWithTag("clear_history").assertIsOff()
        tab(SettingsPage.CUES).assertIsSelected()
    }

    @Test
    fun `Reset progress in the Sets dialog resets the total`() {
        val repo = FakeEntryRepository(listOf(testEntry(1, counter = CounterState(total = 60))))
        show(repo = repo)
        compose.onNodeWithContentDescription("Increase SETS").performScrollTo().performClick()
        tab(SettingsPage.CURRENT).performClick()
        compose.onNodeWithTag("clear_history").performClick()
        compose.onNodeWithTag("confirm_sets_reset").performClick()
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.sets_changed_title)).assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(listOf(1L to true), repo.resets)
            assertEquals(48, repo.find(1).counter.total)
            assertEquals(9, repo.find(1).timing.sets)
        }
        compose.onNodeWithTag("value_Current reps").performScrollTo().assertTextEquals("48")
    }

    @Test
    fun `back with a changed Sets shows the dialog first and leaves after Keep progress`() {
        val repo = FakeEntryRepository(listOf(testEntry(1, counter = CounterState(total = 60))))
        show(repo = repo)
        compose.onNodeWithContentDescription("Increase SETS").performScrollTo().performClick()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithText("Sets changed").assertIsDisplayed()
        assertEquals(0, backs)
        compose.onNodeWithText("Keep progress").performClick()
        compose.waitForIdle()
        assertEquals(1, backs)
        compose.runOnIdle {
            assertEquals(60, repo.find(1).counter.total)
            assertEquals(emptyList<Pair<Long, Boolean>>(), repo.resets)
        }
    }

    @Test
    fun `a Timer only entry is never asked about Sets`() {
        show(repo = checkInRepo())
        compose.onNodeWithContentDescription("Increase SETS").performScrollTo().performClick()
        tab(SettingsPage.CUES).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.sets_changed_title)).assertDoesNotExist()
    }

    @Test
    fun `there is no Save button on any page`() {
        show()
        SettingsPage.entries.forEach { page ->
            tab(page).performClick()
            compose.onNodeWithText("Save").assertDoesNotExist()
        }
    }

    @Test
    fun `each info tag opens its own title and text`() {
        show()
        rows.forEach { (page, list) ->
            tab(page).performClick()
            list.forEach { row ->
                val label = text(row.label)
                val tag = compose.onNodeWithContentDescription("About ${row.describedAs ?: label}")
                (if (row.scrolls) tag.performScrollTo() else tag).performClick()
                compose.onNodeWithTag("info_title").assertTextEquals(label)
                compose.onNodeWithTag("info_text").assertTextEquals(text(row.info))
                compose.onNodeWithTag("info_ok").performClick()
            }
        }
    }

    @Test
    fun `every row has an info tag of at least 48 dp`() {
        show()
        rows.forEach { (page, list) ->
            tab(page).performClick()
            list.forEach { row ->
                compose.onNodeWithContentDescription("About ${row.describedAs ?: text(row.label)}")
                    .assertWidthIsAtLeast(48.dp)
                    .assertHeightIsAtLeast(48.dp)
            }
        }
    }

    @Test
    fun `the pager pops to the list when the entry loads as missing`() {
        show(repo = FakeEntryRepository())
        compose.waitForIdle()
        assertEquals(1, gone)
    }

    @Test
    fun `a Timer only entry shows all four tabs, with Progression window-only and Current total-less`() {
        show(initial = SettingsPage.CURRENT, repo = checkInRepo())
        tab(SettingsPage.TIMING).assertIsDisplayed()
        tab(SettingsPage.CUES).assertIsDisplayed()
        tab(SettingsPage.CURRENT).assertIsSelected()
        compose.onNodeWithTag("value_Best streak").assertIsDisplayed()
        compose.onNodeWithTag("value_Current reps").assertDoesNotExist()
        tab(SettingsPage.PROGRESSION).performClick()
        tab(SettingsPage.PROGRESSION).assertIsSelected()
        compose.onNodeWithTag("value_On-time window (hours)").assertIsDisplayed()
        compose.onNodeWithTag("value_Starting reps").assertDoesNotExist()
    }

    @Test
    fun `a Timer only entry opens directly on the Timing tab too`() {
        show(initial = SettingsPage.TIMING, repo = checkInRepo())
        tab(SettingsPage.TIMING).assertIsSelected()
        compose.onNodeWithTag("value_SETS").assertIsDisplayed()
    }

    @Test
    fun `the tabs are icons named Timing, Progression, Current and Cues, with no text`() {
        show()
        assertEquals(listOf("Timing", "Progression", "Current", "Cues"), SettingsPage.entries.map { text(it.tab) })
        SettingsPage.entries.forEach { page ->
            tab(page).assertIsDisplayed()
            compose.onNodeWithText(text(page.tab)).assertDoesNotExist()
        }
    }

    @Test
    fun `each tab is at least 48 dp and the selected one follows the page`() {
        show(initial = SettingsPage.CUES)
        SettingsPage.entries.forEach { tab(it).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp) }
        tab(SettingsPage.CUES).assertIsSelected()
        tab(SettingsPage.TIMING).performClick()
        tab(SettingsPage.TIMING).assertIsSelected()
    }
}
