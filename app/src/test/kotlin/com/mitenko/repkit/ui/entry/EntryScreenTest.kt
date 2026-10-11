package com.mitenko.repkit.ui.entry

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.repkit.domain.HistoryRange
import com.mitenko.repkit.domain.Prescription
import com.mitenko.repkit.domain.model.CheckInPoint
import com.mitenko.repkit.domain.model.EntryType
import com.mitenko.repkit.domain.model.ProgressMode
import com.mitenko.repkit.domain.model.WeightUnit
import com.mitenko.repkit.domain.rangeStart
import com.mitenko.repkit.ui.theme.HiitTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class EntryScreenTest {
    @get:Rule val compose = createComposeRule()

    private val la = ZoneId.of("America/Los_Angeles")
    private val now = Instant.parse("2026-09-24T15:00:00Z") // Thu 24 Sep 08:00 PDT; 4 weeks start Fri 28 Aug
    private val a = CheckInPoint(Instant.parse("2026-06-10T16:00:00Z"), 50) // All only
    private val b = CheckInPoint(Instant.parse("2026-08-10T16:00:00Z"), 55) // 3 months
    private val c = CheckInPoint(Instant.parse("2026-09-10T16:00:00Z"), 60) // 4 weeks
    private val d = CheckInPoint(Instant.parse("2026-09-23T16:00:00Z"), 62) // 4 weeks

    private val state = EntryUiState(
        name = "Burpees", reps = listOf(9, 8, 8, 8, 8, 8, 8, 8), total = 65,
        bestStreak = 24, currentStreak = 4, checkedInToday = true, loaded = true,
        points = listOf(a, b, c, d), now = now, zone = la,
    )

    private val w14 = CheckInPoint(Instant.parse("2026-09-10T16:00:00Z"), 3, weight = 1400, reps = 10, unit = WeightUnit.KG)
    private val w16 = CheckInPoint(Instant.parse("2026-09-23T16:00:00Z"), 4, weight = 1600, reps = 10, unit = WeightUnit.KG)

    /** A Weight-mode Counter on 16 kg × 10 for 8 sets, with one rep point (c) from before its switch. */
    private val weightState = state.copy(
        reps = List(8) { 10 }, total = 4, mode = ProgressMode.WEIGHT, unit = WeightUnit.KG,
        load = Prescription.Load(1600, 10), points = listOf(c, w14, w16),
    )

    private fun show(
        s: EntryUiState,
        onBack: () -> Unit = {},
        onStart: () -> Unit = {},
        onSettings: () -> Unit = {},
        onCheckIn: () -> Unit = {},
        highlight: Highlight? = null,
        onHighlightShown: () -> Unit = {},
    ) {
        compose.setContent {
            HiitTheme {
                EntryScreen(
                    s, onBack = onBack, onStart = onStart, onCheckIn = onCheckIn, onOpenSettings = onSettings, onDismissError = {},
                    highlight = highlight, onHighlightShown = onHighlightShown,
                )
            }
        }
    }

    @Test
    fun `a Workout shows the range switch, the chart, the reps column, the streak line and both buttons`() {
        show(state)
        compose.onNodeWithTag("entry_name").assertTextEquals("Burpees")
        compose.onNodeWithTag("range_FOUR_WEEKS").assertIsSelected()
        compose.onNodeWithTag("chart").assertExists()
        compose.onNodeWithContentDescription("2 check-ins, from 60 to 62 reps").assertExists()
        compose.onNodeWithTag("rep_0", useUnmergedTree = true).assertTextEquals("9")
        compose.onNodeWithTag("rep_7", useUnmergedTree = true).assertTextEquals("8")
        compose.onNodeWithTag("streak_line").assertTextEquals("Streak 4 · best 24")
        compose.onNodeWithTag("check_in").assertExists()
        compose.onNodeWithTag("start").assertExists()
        compose.onNodeWithTag("calendar").assertDoesNotExist()
    }

    @Test
    fun `none of the table rows or the checked-in line remain`() {
        show(state) // checkedInToday = true, so the old "Checked in today" line would show
        listOf("Total Reps", "Last Check In", "Best CI Streak", "Curr CI Streak", "Today", "Checked in today").forEach {
            compose.onNodeWithText(it).assertDoesNotExist()
        }
    }

    @Test
    fun `start invokes the callback`() {
        var clicks = 0
        show(state, onStart = { clicks++ })
        compose.onNodeWithTag("start").performScrollTo().performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun `start, back and settings are disabled while starting`() {
        show(state.copy(starting = true))
        compose.onNodeWithTag("start").assertIsNotEnabled()
        compose.onNodeWithTag("back").assertIsNotEnabled()
        compose.onNodeWithTag("settings").assertIsNotEnabled()
    }

    @Test
    fun `settings is also disabled while a check-in is in flight`() {
        show(state.copy(checkingIn = true))
        compose.onNodeWithTag("settings").assertIsNotEnabled()
    }

    @Test
    fun `back and settings invoke their callbacks`() {
        var backs = 0
        var settings = 0
        show(state, onBack = { backs++ }, onSettings = { settings++ })
        compose.onNodeWithTag("back").performClick()
        compose.onNodeWithTag("settings").performClick()
        assertEquals(1, backs)
        assertEquals(1, settings)
    }

    @Test
    fun `check in sits next to start and invokes its callback`() {
        var checks = 0
        show(state.copy(checkedInToday = false), onCheckIn = { checks++ })
        compose.onNodeWithTag("check_in").performScrollTo().assertTextEquals("Check in").performClick()
        assertEquals(1, checks)
        compose.onNodeWithTag("start").assertIsEnabled()
    }

    @Test
    fun `once checked in today it reads Checked in and is disabled`() {
        show(state) // checkedInToday = true
        compose.onNodeWithTag("check_in").assertTextEquals("Checked in ✓").assertIsNotEnabled()
        compose.onNodeWithTag("start").assertIsEnabled()
    }

    @Test
    fun `both buttons are disabled while a check-in is in flight`() {
        show(state.copy(checkedInToday = false, checkingIn = true))
        compose.onNodeWithTag("check_in").assertIsNotEnabled()
        compose.onNodeWithTag("start").assertIsNotEnabled()
    }

    @Test
    fun `a Timer only entry has no reps column and keeps the streak line and both buttons`() {
        show(state.copy(type = EntryType.CHECK_IN, checkedInToday = false, points = listOf(c.copy(total = null))))
        compose.onNodeWithTag("reps_column").assertDoesNotExist()
        compose.onNodeWithTag("rep_0", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("chart").assertDoesNotExist()
        compose.onNodeWithTag("streak_line").assertTextEquals("Streak 4 · best 24")
        compose.onNodeWithTag("check_in").assertIsEnabled()
        compose.onNodeWithTag("start").performScrollTo().assertIsEnabled()
    }

    @Test
    fun `before the entry has loaded there is no switch, chart, reps, streak line or button`() {
        show(EntryUiState())
        listOf("range_FOUR_WEEKS", "chart", "history_empty", "reps_column", "streak_line", "start", "check_in").forEach {
            compose.onNodeWithTag(it).assertDoesNotExist()
        }
        compose.onNodeWithTag("rep_0", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `both buttons are at least 48 dp tall`() {
        show(state.copy(checkedInToday = false))
        compose.onNodeWithTag("check_in").assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("start").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `the range switch changes the point set`() {
        show(state)
        compose.onNodeWithTag("range_THREE_MONTHS").performClick()
        compose.onNodeWithContentDescription("3 check-ins, from 55 to 62 reps").assertExists()
        compose.onNodeWithTag("range_ALL").performClick()
        compose.onNodeWithContentDescription("4 check-ins, from 50 to 62 reps").assertExists()
        compose.onNodeWithTag("range_FOUR_WEEKS").performClick()
        compose.onNodeWithContentDescription("2 check-ins, from 60 to 62 reps").assertExists()
    }

    @Test
    fun `tapping a point shows its date and reps, and tapping elsewhere on the chart clears it`() {
        show(state)
        // Spec rev 21: the axis starts at the first shown point (c, 10 Sep), not the range start.
        val start = c.at
        compose.onNodeWithTag("chart").performTouchInput {
            val x = HistoryLayout.x(d.at, start, now, ChartInsets.left.toPx(), width - ChartInsets.right.toPx())
            click(Offset(x, height / 2f))
        }
        compose.onNodeWithText("23 Sep · 62").assertExists()
        // Midway between c (10 Sep) and d (23 Sep) is days from either point, far outside 24 dp.
        compose.onNodeWithTag("chart").performTouchInput {
            val mid = Instant.ofEpochMilli((c.at.toEpochMilli() + d.at.toEpochMilli()) / 2)
            click(Offset(HistoryLayout.x(mid, start, now, ChartInsets.left.toPx(), width - ChartInsets.right.toPx()), height / 2f))
        }
        compose.onNodeWithTag("point_label").assertDoesNotExist()
    }

    @Test
    fun `the empty states say no check-ins yet, or none in this range`() {
        var s by mutableStateOf(state.copy(points = emptyList()))
        compose.setContent {
            HiitTheme { EntryScreen(s, onBack = {}, onStart = {}, onCheckIn = {}, onOpenSettings = {}, onDismissError = {}) }
        }
        compose.onNodeWithText("No check-ins yet").assertExists()
        compose.onNodeWithTag("chart").assertDoesNotExist()
        compose.onNodeWithTag("reps_column").assertExists() // plan Spec note 17
        s = state.copy(points = listOf(a))
        compose.onNodeWithText("No check-ins in this period").assertExists()
        compose.onNodeWithTag("range_ALL").performClick()
        compose.onNodeWithText("No check-ins in this period").assertDoesNotExist()
        compose.onNodeWithContentDescription("1 check-in, from 50 to 50 reps").assertExists()
    }

    @Test
    fun `the chosen range survives recreation`() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            HiitTheme { EntryScreen(state, onBack = {}, onStart = {}, onCheckIn = {}, onOpenSettings = {}, onDismissError = {}) }
        }
        compose.onNodeWithTag("range_ALL").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("range_ALL").assertIsSelected()
        compose.onNodeWithContentDescription("4 check-ins, from 50 to 62 reps").assertExists()
    }

    @Test
    fun `the reps column lists every set, and scrolls only past ten`() {
        var s by mutableStateOf(state)
        compose.setContent {
            HiitTheme { EntryScreen(s, onBack = {}, onStart = {}, onCheckIn = {}, onOpenSettings = {}, onDismissError = {}) }
        }
        compose.onNodeWithContentDescription("Reps per set: 9, 8, 8, 8, 8, 8, 8, 8").assertExists()
        compose.onNodeWithTag("reps_column").assert(!hasScrollAction())
        s = state.copy(reps = List(12) { 5 })
        compose.onNodeWithTag("rep_11", useUnmergedTree = true).assertTextEquals("5")
        compose.onNodeWithTag("reps_column").assert(hasScrollAction())
    }

    @Test
    fun `a Timer only entry shows the calendar, not the chart or the reps column`() {
        val timerOnly = state.copy(
            type = EntryType.CHECK_IN,
            points = listOf(c.copy(total = null), d.copy(total = null), CheckInPoint(Instant.parse("2026-09-24T14:00:00Z"), null)),
        )
        show(timerOnly)
        compose.onNodeWithTag("calendar").assertExists()
        compose.onNodeWithTag("chart").assertDoesNotExist()
        compose.onNodeWithTag("reps_column").assertDoesNotExist()
        compose.onNodeWithContentDescription("3 check-in days").assertExists()
        // Four weeks run from 28 Aug to today, so two month blocks.
        compose.onNodeWithText("August 2026").assertExists()
        compose.onNodeWithText("September 2026").assertExists()
        compose.onNodeWithTag("check_in").assertExists()
        compose.onNodeWithTag("start").assertExists()
    }

    @Test
    fun `the calendar stays at the newest month across a range change`() {
        // Timer Only points in both ranges, so the calendar (not the empty state) shows under both.
        show(state.copy(type = EntryType.CHECK_IN, points = listOf(a.copy(total = null), d.copy(total = null))))
        compose.onNodeWithTag("calendar").assertExists()
        val fourWeeks = compose.onNodeWithTag("calendar").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertEquals(fourWeeks.maxValue(), fourWeeks.value(), 0.5f)
        compose.onNodeWithTag("range_ALL").performClick()
        compose.onNodeWithText("June 2026").assertExists()
        // Plan Spec note 20 / key(range): a range change gives the calendar a fresh scroll state, so
        // it still opens at its end (today) instead of keeping FOUR_WEEKS's old scroll position.
        val all = compose.onNodeWithTag("calendar").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertEquals(all.maxValue(), all.value(), 0.5f)
    }

    @Test
    fun `after a highlight event, the changed cell reports its state and the others don't`() {
        compose.mainClock.autoAdvance = false
        show(state, highlight = Highlight(id = 1, changes = mapOf(1 to RepsColumnLayout.Change.UP)))
        compose.mainClock.advanceTimeBy(50)
        compose.onNodeWithTag("rep_1", useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "increased"))
        compose.onNodeWithTag("rep_0", useUnmergedTree = true)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
    }

    @Test
    fun `a decreased cell reports its state too`() {
        compose.mainClock.autoAdvance = false
        show(state, highlight = Highlight(id = 1, changes = mapOf(0 to RepsColumnLayout.Change.DOWN, 7 to RepsColumnLayout.Change.DOWN)))
        compose.mainClock.advanceTimeBy(50)
        compose.onNodeWithTag("rep_0", useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "decreased"))
        compose.onNodeWithTag("rep_7", useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "decreased"))
    }

    @Test
    fun `a hold flashes every cell as holding and announces the hold day`() {
        compose.mainClock.autoAdvance = false
        val all = (0 until 8).associateWith { RepsColumnLayout.Change.HOLD }
        show(state, highlight = Highlight(id = 1, changes = all, hold = HoldStatus(at = 64, day = 2, of = 4)))
        compose.mainClock.advanceTimeBy(50)
        for (i in 0 until 8) {
            compose.onNodeWithTag("rep_$i", useUnmergedTree = true)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "holding"))
        }
        compose.onNodeWithText("Holding at 64 · 2 of 4").assertExists()
    }

    @Test
    fun `the TalkBack announcement names the one changed set`() {
        compose.mainClock.autoAdvance = false
        show(state, highlight = Highlight(id = 1, changes = mapOf(1 to RepsColumnLayout.Change.UP)))
        compose.mainClock.advanceTimeBy(50)
        compose.onNodeWithText("Set 2: now 8 reps").assertExists()
    }

    @Test
    fun `the TalkBack announcement counts several changed sets`() {
        compose.mainClock.autoAdvance = false
        show(state, highlight = Highlight(id = 1, changes = mapOf(0 to RepsColumnLayout.Change.DOWN, 7 to RepsColumnLayout.Change.DOWN)))
        compose.mainClock.advanceTimeBy(50)
        compose.onNodeWithText("2 sets updated").assertExists()
    }

    @Test
    fun `a highlight event is consumed once shown`() {
        compose.mainClock.autoAdvance = false
        var shown = 0
        show(state, highlight = Highlight(id = 1, changes = mapOf(1 to RepsColumnLayout.Change.UP)), onHighlightShown = { shown++ })
        compose.mainClock.advanceTimeBy(50)
        assertEquals(1, shown)
    }

    @Test
    fun `a Timer only entry has no column and no highlight`() {
        val timerOnly = state.copy(type = EntryType.CHECK_IN, points = listOf(c.copy(total = null)))
        show(timerOnly, highlight = Highlight(id = 1, changes = mapOf(0 to RepsColumnLayout.Change.UP)))
        compose.onNodeWithTag("reps_column").assertDoesNotExist()
        compose.onNodeWithTag("rep_0", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Set 1: now 9 reps").assertDoesNotExist()
    }

    @Test
    fun `a weight mode charts only its weight points, in the unit`() {
        show(weightState)
        compose.onNodeWithContentDescription("2 check-ins, from 14 kg to 16 kg").assertExists()
    }

    @Test
    fun `a weight-mode entry with only weight points shows its chart, not no check-ins`() {
        // Batch 1 review finding 1: the interim REPS default must not shadow a real weight-mode chart.
        show(weightState.copy(points = listOf(w14, w16)))
        compose.onNodeWithTag("chart").assertExists()
        compose.onNodeWithText("No check-ins yet").assertDoesNotExist()
        compose.onNodeWithText("No check-ins in this mode yet").assertDoesNotExist()
    }

    @Test
    fun `tapping a weight point shows its recorded load`() {
        show(weightState)
        // Spec rev 21: the axis starts at the first shown point (w14), not at c, which isn't shown.
        val start = w14.at
        compose.onNodeWithTag("chart").performTouchInput {
            val x = HistoryLayout.x(w16.at, start, now, ChartInsets.left.toPx(), width - ChartInsets.right.toPx())
            click(Offset(x, height / 2f))
        }
        compose.onNodeWithText("23 Sep · 16 kg × 10").assertExists()
    }

    @Test
    fun `a point recorded in lb shows in the workout's kg`() {
        show(weightState.copy(points = listOf(w16.copy(weight = 3500, unit = WeightUnit.LB))))
        compose.onNodeWithContentDescription("1 check-in, from 16 kg to 16 kg").assertExists()
    }

    @Test
    fun `right after a switch the chart says there are no check-ins in this mode yet`() {
        show(weightState.copy(points = listOf(c, d)))
        compose.onNodeWithText("No check-ins in this mode yet").assertExists()
        compose.onNodeWithTag("chart").assertDoesNotExist()
    }

    @Test
    fun `back in Reps mode the rep line returns without the weight points`() {
        show(state.copy(points = listOf(c, w14, w16, d)))
        compose.onNodeWithContentDescription("2 check-ins, from 60 to 62 reps").assertExists()
    }

    @Test
    fun `a weight mode shows the prescription card instead of the reps column`() {
        show(weightState)
        compose.onNodeWithTag("reps_column").assertDoesNotExist()
        compose.onNodeWithTag("prescription_weight", useUnmergedTree = true).assertTextEquals("16 kg")
        compose.onNodeWithTag("prescription_sets", useUnmergedTree = true).assertTextEquals("8 × 10 reps")
        compose.onNodeWithContentDescription("16 kilograms, 8 sets of 10 reps").assertExists()
    }

    @Test
    fun `the card shows with no history yet`() {
        show(weightState.copy(points = emptyList()))
        compose.onNodeWithText("No check-ins yet").assertExists()
        compose.onNodeWithTag("prescription_card").assertExists()
    }

    @Test
    fun `the card flashes up and announces the new load`() {
        compose.mainClock.autoAdvance = false
        show(weightState, highlight = Highlight(id = 1, changes = emptyMap(), card = RepsColumnLayout.Change.UP))
        compose.mainClock.advanceTimeBy(50)
        compose.onNodeWithTag("prescription_card")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "increased"))
        compose.onNodeWithText("Now 16 kilograms, 8 sets of 10 reps").assertExists()
    }

    @Test
    fun `a drop flashes the card as decreased`() {
        compose.mainClock.autoAdvance = false
        show(weightState, highlight = Highlight(id = 1, changes = emptyMap(), card = RepsColumnLayout.Change.DOWN))
        compose.mainClock.advanceTimeBy(50)
        compose.onNodeWithTag("prescription_card")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "decreased"))
    }

    @Test
    fun `a weight hold flashes the card as holding and names the load`() {
        compose.mainClock.autoAdvance = false
        val hold = Highlight(id = 1, changes = emptyMap(), hold = HoldStatus(at = 4, day = 2, of = 4), card = RepsColumnLayout.Change.HOLD)
        show(weightState, highlight = hold)
        compose.mainClock.advanceTimeBy(50)
        compose.onNodeWithTag("prescription_card")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "holding"))
        compose.onNodeWithText("Holding at 16 kg × 10 · 2 of 4").assertExists()
    }

    @Test
    fun `the card's highlight is consumed once shown`() {
        compose.mainClock.autoAdvance = false
        var shown = 0
        show(weightState, highlight = Highlight(id = 1, changes = emptyMap(), card = RepsColumnLayout.Change.UP), onHighlightShown = { shown++ })
        compose.mainClock.advanceTimeBy(50)
        assertEquals(1, shown)
    }

    @Test
    fun `reduced motion skips the card's pop`() {
        val resolver = ApplicationProvider.getApplicationContext<Context>().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        try {
            compose.mainClock.autoAdvance = false
            show(weightState, highlight = Highlight(id = 1, changes = emptyMap(), card = RepsColumnLayout.Change.UP))
            compose.mainClock.advanceTimeBy(50)
            val before = compose.onNodeWithTag("prescription_card").getBoundsInRoot()
            compose.mainClock.advanceTimeBy(150) // the pop's peak (POP_HALF_MS) with full motion
            val peak = compose.onNodeWithTag("prescription_card").getBoundsInRoot()
            assertEquals(before, peak)
        } finally {
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = "w320dp-h640dp")
    fun `the heaviest weight fits on the card at 320dp`() {
        show(weightState.copy(unit = WeightUnit.LB, load = Prescription.Load(99_975, 100)))
        compose.onNodeWithTag("prescription_weight", useUnmergedTree = true).assertTextEquals("999.75 lb")
        val root = compose.onRoot().getBoundsInRoot()
        val card = compose.onNodeWithTag("prescription_card").getBoundsInRoot()
        val weight = compose.onNodeWithTag("prescription_weight", useUnmergedTree = true).getBoundsInRoot()
        assertTrue(card.left >= root.left && card.right <= root.right)
        assertTrue(weight.left >= card.left && weight.right <= card.right)
    }
}
