package com.mitenko.repkit.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.repkit.domain.Move
import com.mitenko.repkit.domain.ProgressionField
import com.mitenko.repkit.domain.RangeChange
import com.mitenko.repkit.domain.SettingsValidator
import com.mitenko.repkit.domain.model.Hold
import com.mitenko.repkit.domain.model.HoldKind
import com.mitenko.repkit.domain.model.ProgressionConfig
import com.mitenko.repkit.ui.common.SaveStatus
import com.mitenko.repkit.ui.theme.HiitTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ProgressionSettingsScreenTest {
    @get:Rule val compose = createComposeRule()

    private var draft by mutableStateOf(ProgressionDraft.from(ProgressionConfig()))
    private var resets = 0
    private var nowEdits = 0

    /** The field each edit named (spec revision 28), in order. */
    private val fields = mutableListOf<ProgressionField?>()
    private var note by mutableStateOf<ProgressionNote?>(null)

    private fun show(initial: ProgressionConfig, windowOnly: Boolean = false) {
        draft = ProgressionDraft.from(initial)
        compose.setContent {
            HiitTheme {
                val validation = SettingsValidator.progression(draft.toConfig())
                ProgressionPageContent(
                    draft, validation, SaveStatus.of(validation, failed = false),
                    onChange = { field, f -> fields += field; draft = f(draft) },
                    onChangeNow = { field, f -> fields += field; nowEdits++; draft = f(draft) },
                    onReset = { resets++ },
                    windowOnly = windowOnly, note = note,
                )
            }
        }
    }

    @Test
    fun `an unresolved cross-field error still shows inline with Not saved, and the hold hints show`() {
        // Edits resolve these (spec revision 28); a draft that arrives out of order, e.g. restored, still shows the error.
        show(ProgressionConfig(startingTotal = 40))
        compose.onNodeWithTag("support_Starting reps").assertTextEquals("Must be ≥ floor")
        compose.onNodeWithTag("save_status").assertTextEquals("Not saved: fix the highlighted field")
        draft = ProgressionDraft.from(ProgressionConfig(holds = listOf(Hold(64, 0))))
        compose.onNodeWithTag("support_Hold 1 at").performScrollTo().assertTextEquals("Hold disabled")
        compose.onNodeWithTag("save_status").assertTextEquals("Saved")
        // Spec revision 28 rule 6: an outside hold says which bound.
        draft = ProgressionDraft.from(ProgressionConfig(cap = 60, holds = listOf(Hold(64, 4))))
        compose.onNodeWithTag("support_Hold 1 at").performScrollTo().assertTextEquals("Hold at 64 is above the maximum (60)")
        draft = ProgressionDraft.from(ProgressionConfig(holds = listOf(Hold(40, 4))))
        compose.onNodeWithTag("support_Hold 1 at").performScrollTo().assertTextEquals("Hold at 40 is below the minimum (48)")
        compose.onNodeWithTag("save_status").assertTextEquals("Saved")
    }

    @Test
    fun `the reps steppers name their field and the other rows name none`() {
        show(ProgressionConfig())
        listOf("Starting reps", "Minimum reps", "Maximum reps", "Hold 1 at", "On-time window (hours)").forEach {
            compose.onNodeWithContentDescription("Increase $it").performScrollTo().performClick()
        }
        assertEquals(listOf(ProgressionField.STARTING_TOTAL, ProgressionField.FLOOR, ProgressionField.CAP, null, null), fields)
    }

    @Test
    fun `the note shows under the edited field, every move on one line, as a polite live region`() {
        note = ProgressionNote(ProgressionField.FLOOR, listOf(Move.StartingRaised(50), Move.CurrentRaised(50)))
        show(ProgressionConfig(startingTotal = 50, floor = 50))
        compose.onNodeWithTag("progression_note").performScrollTo()
            .assertTextEquals("Starting reps raised to 50 · Current reps raised to 50")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        // Under Minimum reps: below its row, above Maximum reps.
        val floorRow = compose.onNodeWithTag("value_Minimum reps").fetchSemanticsNode().boundsInRoot
        val noteBox = compose.onNodeWithTag("progression_note").fetchSemanticsNode().boundsInRoot
        val capRow = compose.onNodeWithTag("value_Maximum reps").fetchSemanticsNode().boundsInRoot
        assertTrue(noteBox.top >= floorRow.bottom && noteBox.bottom <= capRow.top)
    }

    @Test
    fun `each move has its own text`() {
        show(ProgressionConfig())
        mapOf(
            RangeChange.RaisedMax(80) to "Maximum reps raised to 80",
            RangeChange.LoweredMin(40) to "Minimum reps lowered to 40",
            Move.StartingRaised(50) to "Starting reps raised to 50",
            Move.StartingLowered(45) to "Starting reps lowered to 45",
            Move.CurrentRaised(52) to "Current reps raised to 52",
            Move.CurrentLowered(60) to "Current reps lowered to 60",
        ).forEach { (move, text) ->
            note = ProgressionNote(ProgressionField.CAP, listOf(move))
            compose.onNodeWithTag("progression_note").performScrollTo().assertTextEquals(text)
        }
        note = ProgressionNote(null, listOf(Move.CurrentLowered(72))) // Reset to defaults: under its button
        compose.onNodeWithTag("progression_note").performScrollTo().assertTextEquals("Current reps lowered to 72")
        note = null
        compose.onNodeWithTag("progression_note").assertDoesNotExist()
    }

    @Test
    fun `the hold switch hides and restores the rows and their values`() {
        show(ProgressionConfig(holds = listOf(Hold(66, 3))))
        compose.onNodeWithTag("value_Hold 1 at").performScrollTo().assertTextEquals("66")
        compose.onNodeWithTag("switch_Hold").performScrollTo().performClick()
        compose.onNodeWithTag("value_Hold 1 at").assertDoesNotExist()
        compose.onNodeWithTag("value_Hold 1 for").assertDoesNotExist()
        assertFalse(draft.hold)
        assertEquals(listOf(Hold(66, 3)), draft.holds)
        compose.onNodeWithTag("switch_Hold").performScrollTo().performClick()
        compose.onNodeWithTag("value_Hold 1 at").performScrollTo().assertTextEquals("66")
        compose.onNodeWithTag("value_Hold 1 for").performScrollTo().assertTextEquals("3")
    }

    @Test
    fun `reset to defaults asks for confirmation first`() {
        show(ProgressionConfig(cap = 90))
        compose.onNodeWithTag("reset_defaults").performScrollTo().performClick()
        compose.onNodeWithText("Reset progression to defaults?").assertIsDisplayed()
        assertEquals(0, resets)
        compose.onNodeWithTag("confirm_reset_defaults").performClick()
        assertEquals(1, resets)
    }

    @Test
    fun `window only shows just the check-in window and keeps the other values`() {
        show(ProgressionConfig(cap = 80, holds = listOf(Hold(66, 4))), windowOnly = true)
        compose.onNodeWithTag("value_On-time window (hours)").assertIsDisplayed()
        listOf("Starting reps", "Minimum reps", "Maximum reps", "Hold 1 at", "Hold 1 for", "Missed-day adjustment (hours per rep)")
            .forEach { compose.onNodeWithTag("value_$it").assertDoesNotExist() }
        compose.onNodeWithTag("switch_Hold").assertDoesNotExist()
        compose.onNodeWithTag("reset_defaults").assertDoesNotExist()
        compose.onNodeWithContentDescription("Increase On-time window (hours)").performClick()
        assertEquals(37, draft.windowHours)
        assertEquals(80, draft.cap)
        assertEquals(listOf(Hold(66, 4)), draft.holds)
        compose.onNodeWithTag("save_status").assertTextEquals("Saved")
    }
    private val two = ProgressionConfig(holds = listOf(Hold(56, 3), Hold(64, 4)))

    /** Hold n's (1-based) Hold at and Hold for values, by their per-hold labels. */
    private fun assertHold(n: Int, at: Int, forCount: Int) {
        compose.onNodeWithTag("value_Hold $n at").performScrollTo().assertTextEquals("$at")
        compose.onNodeWithTag("value_Hold $n for").performScrollTo().assertTextEquals("$forCount")
    }

    @Test
    fun `two holds render with their headers and values`() {
        show(two)
        compose.onNodeWithText("Hold 1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Hold 2").performScrollTo().assertIsDisplayed()
        assertHold(1, 56, 3)
        assertHold(2, 64, 4)
        compose.onNodeWithContentDescription("Remove hold 2").assertExists()
        // TalkBack tells the rows apart; the visible labels stay the same.
        compose.onNodeWithContentDescription("Increase Hold 2 at").assertExists()
        compose.onNodeWithContentDescription("Decrease Hold 1 for").assertExists()
        compose.onNodeWithContentDescription("About Hold 2 for").assertExists()
        compose.onAllNodesWithText("Hold at").assertCountEquals(2)
    }

    @Test
    fun `add hold appends the default hold and saves at once`() {
        show(two)
        compose.onNodeWithTag("add_hold").performScrollTo().assertIsEnabled().performClick()
        assertEquals(listOf(Hold(56, 3), Hold(64, 4), Hold(68, 4)), draft.holds)
        assertEquals(1, nowEdits)
        compose.onNodeWithText("Hold 3").performScrollTo().assertIsDisplayed()
        assertHold(3, 68, 4)
        compose.onNodeWithTag("save_status").assertTextEquals("Saved")
    }

    @Test
    fun `remove takes out the right hold and saves at once`() {
        show(ProgressionConfig(holds = listOf(Hold(52, 1), Hold(56, 3), Hold(64, 4))))
        compose.onNodeWithContentDescription("Remove hold 2").performScrollTo().performClick()
        assertEquals(listOf(Hold(52, 1), Hold(64, 4)), draft.holds)
        assertEquals(1, nowEdits)
        assertHold(1, 52, 1)
        assertHold(2, 64, 4)
        compose.onNodeWithText("Hold 3").assertDoesNotExist()
    }

    @Test
    fun `removing the last hold leaves just add hold with the switch on`() {
        show(ProgressionConfig())
        compose.onNodeWithContentDescription("Remove hold 1").performScrollTo().performClick()
        assertEquals(emptyList<Hold>(), draft.holds)
        assertTrue(draft.hold)
        compose.onNodeWithText("Hold 1").assertDoesNotExist()
        compose.onNodeWithTag("value_Hold 1 at").assertDoesNotExist()
        compose.onNodeWithTag("add_hold").performScrollTo().assertIsEnabled()
    }

    @Test
    fun `a duplicate hold at shows the error on the later hold and is not saved`() {
        show(ProgressionConfig(holds = listOf(Hold(64, 4), Hold(63, 3))))
        compose.onNodeWithContentDescription("Increase Hold 2 at").performScrollTo().performClick()
        assertEquals(listOf(Hold(64, 4), Hold(64, 3)), draft.holds)
        compose.onNodeWithTag("support_Hold 2 at").performScrollTo().assertTextEquals("Already a hold at 64")
        compose.onNodeWithTag("support_Hold 1 at").assertDoesNotExist()
        compose.onNodeWithTag("save_status").assertTextEquals("Not saved: fix the highlighted field")
    }

    @Test
    fun `add hold has a touch target at least 48 dp tall`() {
        show(two)
        val height = compose.onNodeWithTag("add_hold").performScrollTo().fetchSemanticsNode().boundsInRoot.height
        with(compose.density) { assertTrue("height ${height.toDp()}", height.toDp() >= 48.dp) }
    }

    @Test
    fun `add hold is disabled at eight holds`() {
        show(ProgressionConfig(cap = 90, holds = (0 until 8).map { Hold(50 + 4 * it, 2) }))
        compose.onNodeWithTag("add_hold").performScrollTo().assertIsNotEnabled()
        draft = ProgressionDraft.from(ProgressionConfig(cap = 90, holds = (0 until 7).map { Hold(50 + 4 * it, 2) }))
        compose.onNodeWithTag("add_hold").performScrollTo().assertIsEnabled()
    }

    @Test
    fun `the switch off hides the whole list and keeps the values`() {
        show(two)
        compose.onNodeWithTag("switch_Hold").performScrollTo().performClick()
        listOf("Hold 1", "Hold 2").forEach { compose.onNodeWithText(it).assertDoesNotExist() }
        compose.onNodeWithTag("add_hold").assertDoesNotExist()
        assertEquals(two.holds, draft.holds)
        compose.onNodeWithTag("switch_Hold").performScrollTo().performClick()
        assertHold(1, 56, 3)
        assertHold(2, 64, 4)
    }

    @Test
    fun `the At or From choice switches the label, spoken name and tags, and saves at once`() {
        show(ProgressionConfig(holds = listOf(Hold(64, 4))))
        compose.onNodeWithTag("hold_1_kind_at").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("hold_1_kind_from").assertIsNotSelected()
        compose.onNodeWithTag("value_Hold 1 at").performScrollTo().assertTextEquals("64")
        // Plan note 7: the selected segment does nothing.
        compose.onNodeWithTag("hold_1_kind_at").performClick()
        assertEquals(0, nowEdits)

        compose.onNodeWithTag("hold_1_kind_from").performScrollTo().performClick()
        assertEquals(listOf(Hold(64, 4, HoldKind.FROM)), draft.holds)
        assertEquals(1, nowEdits)
        assertEquals(listOf<ProgressionField?>(null), fields)
        compose.onNodeWithTag("hold_1_kind_from").assertIsSelected()
        compose.onNodeWithTag("value_Hold 1 at").assertDoesNotExist()
        compose.onNodeWithTag("value_Hold 1 from").performScrollTo().assertTextEquals("64")
        compose.onNodeWithText("Hold from").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Increase Hold 1 from").assertExists()
        compose.onNodeWithContentDescription("About Hold 1 from").assertExists()
        compose.onNodeWithTag("value_Hold 1 for").performScrollTo().assertTextEquals("4")

        compose.onNodeWithTag("hold_1_kind_at").performScrollTo().performClick()
        assertEquals(listOf(Hold(64, 4)), draft.holds)
        compose.onNodeWithTag("value_Hold 1 at").performScrollTo().assertTextEquals("64")
    }

    @Test
    fun `a duplicate From hold shows its error on the later hold, and At and From on one value coexist`() {
        show(ProgressionConfig(holds = listOf(Hold(60, 2, HoldKind.FROM), Hold(60, 4), Hold(59, 3, HoldKind.FROM))))
        compose.onNodeWithTag("support_Hold 2 at").assertDoesNotExist()
        compose.onNodeWithContentDescription("Increase Hold 3 from").performScrollTo().performClick()
        assertEquals(Hold(60, 3, HoldKind.FROM), draft.holds[2])
        compose.onNodeWithTag("support_Hold 3 from").performScrollTo().assertTextEquals("Already a hold from 60")
        compose.onNodeWithTag("support_Hold 1 from").assertDoesNotExist()
        compose.onNodeWithTag("support_Hold 2 at").assertDoesNotExist()
        compose.onNodeWithTag("save_status").assertTextEquals("Not saved: fix the highlighted field")
    }

    @Test
    fun `an inactive From hold shows the plain disabled hint`() {
        show(ProgressionConfig(holds = listOf(Hold(80, 2, HoldKind.FROM))))
        compose.onNodeWithTag("support_Hold 1 from").performScrollTo().assertTextEquals("Hold disabled")
    }

    @Test
    fun `the At and From segments are at least 48 dp tall`() {
        show(two)
        listOf("hold_2_kind_at", "hold_2_kind_from").forEach { tag ->
            val height = compose.onNodeWithTag(tag).performScrollTo().fetchSemanticsNode().boundsInRoot.height
            with(compose.density) { assertTrue("$tag ${height.toDp()}", height.toDp() >= 48.dp) }
        }
    }
}
