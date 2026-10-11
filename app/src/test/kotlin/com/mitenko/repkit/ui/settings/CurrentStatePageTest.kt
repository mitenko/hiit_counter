package com.mitenko.repkit.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.repkit.domain.Move
import com.mitenko.repkit.domain.ProgressionScale
import com.mitenko.repkit.domain.RangeChange
import com.mitenko.repkit.domain.StreakField
import com.mitenko.repkit.domain.ValidationResult
import com.mitenko.repkit.domain.model.WeightUnit
import com.mitenko.repkit.ui.common.SaveStatus
import com.mitenko.repkit.ui.theme.HiitTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CurrentStatePageTest {
    @get:Rule val compose = createComposeRule()

    private var draft by mutableStateOf(CurrentStateViewModel.Draft(65, 24, 4, Instant.parse("2026-09-23T12:00:00Z")))
    private var immediate = 0

    /** The field each edit named (spec revision 28), in order. */
    private val fields = mutableListOf<StreakField?>()

    /** Each reset's Clear history too value, in order. */
    private val resets = mutableListOf<Boolean>()

    private fun show(showTotal: Boolean = true, rangeNote: RangeChange? = null, streakNote: List<Move> = emptyList()) {
        compose.setContent {
            HiitTheme {
                CurrentStatePageContent(
                    draft, ValidationResult(), SaveStatus.SAVED, ZoneOffset.UTC, now = { Instant.parse("2026-09-24T12:00:00Z") },
                    onChange = { field, f -> fields += field; draft = f(draft) },
                    onChangeNow = { field, f -> fields += field; immediate++; draft = f(draft) },
                    onResetProgress = { resets += it },
                    showTotal = showTotal, rangeNote = rangeNote, streakNote = streakNote,
                )
            }
        }
    }

    @Test
    fun `Clear saves at once`() {
        show()
        compose.onNodeWithTag("clear_last_check_in").performScrollTo().performClick()
        assertEquals(1, immediate)
        assertNull(draft.lastCheckIn)
    }

    @Test
    fun `reset progress asks for confirmation first`() {
        show()
        compose.onNodeWithTag("reset_progress").performScrollTo().performClick()
        compose.onNodeWithText("Reset progress?").assertIsDisplayed()
        assertTrue(resets.isEmpty())
        compose.onNodeWithTag("confirm_reset_progress").performClick()
        assertEquals(listOf(false), resets)
    }

    @Test
    fun `Clear history too starts unchecked under the unchanged body, so a reset keeps the history`() {
        show()
        compose.onNodeWithTag("reset_progress").performScrollTo().performClick()
        compose.onNodeWithText("Your reps will return to the starting total, your streaks will reset to 0, and your last check-in will be cleared.").assertIsDisplayed()
        compose.onNodeWithTag("clear_history").assertIsOff()
        compose.onNodeWithTag("confirm_reset_progress").performClick()
        assertEquals(listOf(false), resets)
    }

    @Test
    fun `checking Clear history too passes it to the reset, and the next dialog starts unchecked again`() {
        show()
        compose.onNodeWithTag("reset_progress").performScrollTo().performClick()
        compose.onNodeWithTag("clear_history").performClick().assertIsOn()
        compose.onNodeWithTag("confirm_reset_progress").performClick()
        assertEquals(listOf(true), resets)
        compose.onNodeWithTag("reset_progress").performScrollTo().performClick()
        compose.onNodeWithTag("clear_history").assertIsOff()
    }

    @Test
    fun `without the total the page shows the streaks, the date and Reset progress`() {
        show(showTotal = false)
        compose.onNodeWithTag("value_Current reps").assertDoesNotExist()
        compose.onNodeWithTag("value_Best streak").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("value_Current streak").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("last_check_in").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("reset_progress").performScrollTo().assertIsDisplayed()
    }

    // Best streak is read-only (spec revision 29): the app keeps it current on its own.

    @Test
    fun `Best streak shows its value`() {
        show()
        compose.onNodeWithTag("value_Best streak").performScrollTo().assertTextEquals("24")
    }

    @Test
    fun `Best streak has no increase or decrease buttons`() {
        show()
        compose.onNodeWithTag("value_Best streak").performScrollTo()
        compose.onNodeWithContentDescription("Increase Best streak").assertDoesNotExist()
        compose.onNodeWithContentDescription("Decrease Best streak").assertDoesNotExist()
    }

    @Test
    fun `tapping Best streak's value opens no edit dialog`() {
        show()
        compose.onNodeWithTag("value_Best streak").performScrollTo().performClick()
        compose.onNodeWithTag("edit_field").assertDoesNotExist()
        assertEquals(24, draft.best) // unchanged: the tap did nothing
    }

    @Test
    fun `Best streak's info tag still works`() {
        show()
        compose.onNodeWithContentDescription("About Best streak").performScrollTo().performClick()
        compose.onNodeWithTag("info_text").assertTextEquals("Your longest run of on-time check-ins. It updates on its own as your streak grows.")
    }

    @Test
    fun `a moved limit shows its note under Current reps as a polite live region`() {
        show(rangeNote = RangeChange.RaisedMax(80))
        compose.onNodeWithTag("range_note").assertTextEquals("Maximum reps raised to 80")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    @Test
    fun `a lowered floor has its own note`() {
        show(rangeNote = RangeChange.LoweredMin(40))
        compose.onNodeWithTag("range_note").assertTextEquals("Minimum reps lowered to 40")
    }

    @Test
    fun `no moved limit, no note`() {
        show()
        compose.onNodeWithTag("range_note").assertDoesNotExist()
    }

    @Test
    fun `the current streak stepper names its field and the others name none`() {
        show()
        compose.onNodeWithContentDescription("Increase Current streak").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Increase Current reps").performScrollTo().performClick()
        assertEquals(listOf(StreakField.CURRENT, null), fields)
    }

    @Test
    fun `a raised best streak shows its note under Current streak as a polite live region`() {
        show(streakNote = listOf(Move.BestStreakRaised(5)))
        compose.onNodeWithTag("streak_note").performScrollTo().assertTextEquals("Best streak raised to 5")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        compose.onNodeWithTag("range_note").assertDoesNotExist()
    }

    @Test
    fun `no streak move, no streak note`() {
        show()
        compose.onNodeWithTag("streak_note").assertDoesNotExist()
    }

    private fun showLadder(ladder: CurrentStateViewModel.LadderView, showTotal: Boolean = true) {
        compose.setContent {
            HiitTheme {
                CurrentStatePageContent(
                    draft, ValidationResult(), SaveStatus.SAVED, ZoneOffset.UTC, now = { Instant.parse("2026-09-24T12:00:00Z") },
                    onChange = { field, f -> fields += field; draft = f(draft) },
                    onChangeNow = { field, f -> fields += field; immediate++; draft = f(draft) },
                    onResetProgress = { resets += it },
                    showTotal = showTotal, ladder = ladder,
                )
            }
        }
    }

    @Test
    fun `a Weight-mode Current page shows Current weight and moves along the ladder`() {
        draft = draft.copy(total = 1)
        showLadder(CurrentStateViewModel.LadderView(ProgressionScale.Weight(listOf(2000, 2250, 2500), 10), WeightUnit.KG))
        compose.onNodeWithText("Current weight (kg)").assertExists()
        compose.onNodeWithTag("value_Current weight").assertTextEquals("22.5")
        compose.onNodeWithTag("card_Current reps").assertDoesNotExist()
        compose.onNodeWithTag("card_Current reps per set").assertDoesNotExist()
        compose.onNodeWithContentDescription("Increase Current weight").performClick()
        assertEquals(2, draft.total)
        compose.onNodeWithTag("value_Current weight").performClick()
        compose.onNodeWithTag("option_Current weight_2000").performClick()
        assertEquals(0, draft.total)
        assertEquals(1, immediate)
    }

    @Test
    fun `Reps then weight shows Current reps per set within the range`() {
        draft = draft.copy(total = 7) // 22.5 × 10 on 20 / 22.5 × 8–12: weight 1 × span 5 + 2
        showLadder(CurrentStateViewModel.LadderView(ProgressionScale.RepsThenWeight(listOf(2000, 2250), 8, 12), WeightUnit.KG))
        compose.onNodeWithTag("value_Current reps per set").assertTextEquals("10")
        compose.onNodeWithContentDescription("Increase Current reps per set").performScrollTo().performClick()
        assertEquals(8, draft.total)
        compose.onNodeWithTag("value_Current weight").performScrollTo().performClick()
        compose.onNodeWithTag("option_Current weight_2000").performClick()
        assertEquals(3, draft.total) // 20 × 11: the reps are kept
    }

    @Test
    fun `the reset dialog talks about weight in a weight mode`() {
        showLadder(CurrentStateViewModel.LadderView(ProgressionScale.Weight(listOf(2000, 2250), 10), WeightUnit.KG))
        compose.onNodeWithTag("reset_progress").performScrollTo().performClick()
        compose.onNodeWithText("Your weight and reps will return to the starting point, your streaks will reset to 0, and your last check-in will be cleared.").assertIsDisplayed()
    }

    @Test
    fun `a Timer only entry stuck in a weight mode gets the normal reset wording, never the weight one`() {
        showLadder(CurrentStateViewModel.LadderView(ProgressionScale.Weight(listOf(2000, 2250), 10), WeightUnit.KG), showTotal = false)
        compose.onNodeWithTag("reset_progress").performScrollTo().performClick()
        compose.onNodeWithText("Your reps will return to the starting total, your streaks will reset to 0, and your last check-in will be cleared.").assertIsDisplayed()
    }
}
