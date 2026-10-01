package com.mitenko.hiitcounter.ui.settings

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
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
import com.mitenko.hiitcounter.R
import com.mitenko.hiitcounter.domain.model.EntryType
import com.mitenko.hiitcounter.testutil.FakeClock
import com.mitenko.hiitcounter.testutil.FakeEntryRepository
import com.mitenko.hiitcounter.testutil.FakeVoiceAvailability
import com.mitenko.hiitcounter.testutil.testEntry
import com.mitenko.hiitcounter.ui.common.ENTRY_ID_ARG
import com.mitenko.hiitcounter.ui.theme.HiitTheme
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

    private fun checkInRepo() = FakeEntryRepository(listOf(testEntry(1, name = "Stretch", type = EntryType.CHECK_IN)))

    private fun show(initial: SettingsPage = SettingsPage.TIMING, repo: FakeEntryRepository = this.repo) {
        val appScope = MainScope()
        val pagerVm = SettingsPagerViewModel(handle(), repo)
        val timingVm = TimingSettingsViewModel(handle(), repo, appScope)
        val progressionVm = ProgressionSettingsViewModel(handle(), repo, appScope)
        val currentVm = CurrentStateViewModel(handle(), repo, FakeClock(), appScope)
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
    private class InfoRow(@StringRes val label: Int, @StringRes val info: Int, val scrolls: Boolean = true)

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
            InfoRow(R.string.hold_at, R.string.info_hold_at),
            InfoRow(R.string.hold_for, R.string.info_hold_for),
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
        compose.onNodeWithTag("value_Starting total").assertIsDisplayed()
    }

    @Test
    fun `swiping changes the page and the selected tab`() {
        show()
        // Fallback if Robolectric's default swipe is too short or too fast to settle on the next page:
        // performTouchInput { swipeLeft(startX = right * 0.9f, endX = left, durationMillis = 400) }
        compose.onNodeWithTag("settings_pager").performTouchInput { swipeLeft() }
        tab(SettingsPage.PROGRESSION).assertIsSelected()
        compose.onNodeWithTag("value_Starting total").assertIsDisplayed()
    }

    @Test
    fun `page 2 opens on Current`() {
        show(initial = SettingsPage.CURRENT)
        tab(SettingsPage.CURRENT).assertIsSelected()
        compose.onNodeWithTag("value_Current total").assertIsDisplayed()
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
        assertEquals(1, backs)
        assertEquals(9, repo.find(1).timing.sets)
        compose.onNodeWithContentDescription("Increase SETS").performScrollTo().performClick()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals(2, backs)
        assertEquals(10, repo.find(1).timing.sets)
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
                val tag = compose.onNodeWithContentDescription("About $label")
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
                compose.onNodeWithContentDescription("About ${text(row.label)}")
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
        compose.onNodeWithTag("value_Current total").assertDoesNotExist()
        tab(SettingsPage.PROGRESSION).performClick()
        tab(SettingsPage.PROGRESSION).assertIsSelected()
        compose.onNodeWithTag("value_Check-in window (hours)").assertIsDisplayed()
        compose.onNodeWithTag("value_Starting total").assertDoesNotExist()
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
