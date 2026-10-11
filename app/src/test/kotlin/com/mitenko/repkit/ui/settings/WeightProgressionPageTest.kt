package com.mitenko.repkit.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.repkit.domain.SettingsValidator
import com.mitenko.repkit.domain.WeightField
import com.mitenko.repkit.domain.WeightMove
import com.mitenko.repkit.domain.model.ProgressMode
import com.mitenko.repkit.domain.model.ProgressionConfig
import com.mitenko.repkit.domain.model.WeightConfig
import com.mitenko.repkit.domain.model.WeightHold
import com.mitenko.repkit.domain.model.WeightSteps
import com.mitenko.repkit.domain.model.WeightUnit
import com.mitenko.repkit.domain.model.WeightsKind
import com.mitenko.repkit.domain.weightValidation
import com.mitenko.repkit.ui.common.SaveStatus
import com.mitenko.repkit.ui.theme.HiitTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class WeightProgressionPageTest {
    @get:Rule val compose = createComposeRule()

    private var draft by mutableStateOf(ProgressionDraft.from(ProgressionConfig()))
    private var weight by mutableStateOf(WeightConfig(unit = WeightUnit.KG))
    private var note by mutableStateOf<WeightNote?>(null)
    private val fields = mutableListOf<WeightField?>()
    private var nowEdits = 0
    private val modes = mutableListOf<ProgressMode>()
    private val units = mutableListOf<WeightUnit>()

    private fun show(mode: ProgressMode, initial: WeightConfig = WeightConfig(unit = WeightUnit.KG), windowOnly: Boolean = false) {
        weight = initial
        compose.setContent {
            HiitTheme {
                val validation = SettingsValidator.progression(draft.toConfig())
                val wv = weightValidation(weight, mode)
                ProgressionPageContent(
                    draft, validation, SaveStatus.of(validation, failed = false),
                    onChange = { _, f -> draft = f(draft) },
                    onChangeNow = { _, f -> draft = f(draft) },
                    onReset = {},
                    windowOnly = windowOnly,
                    weight = WeightPage(
                        mode, weight, wv, SaveStatus.of(wv.isValid, failed = false), note,
                        onChange = { field, f -> fields += field; weight = f(weight) },
                        onChangeNow = { field, f -> fields += field; nowEdits++; weight = f(weight) },
                        onRequestMode = { modes += it },
                        onRequestUnit = { units += it },
                    ),
                )
            }
        }
    }

    private fun card(label: String) = compose.onNodeWithTag("card_$label")

    @Test
    fun `Reps mode shows Progress by over today's rows`() {
        show(ProgressMode.REPS)
        compose.onNodeWithTag("mode_REPS").assertIsSelected()
        card("Starting reps").assertExists()
        card("Unit").assertDoesNotExist()
        card("Missed-day adjustment (hours per rep)").assertExists()
    }

    @Test
    fun `Weight mode shows the weight rows and hides the Reps rows`() {
        show(ProgressMode.WEIGHT)
        listOf("Unit", "Weights", "Start", "Step", "Top", "Reps per set", "Starting weight", "Hold", "Missed-day adjustment (hours per step)").forEach {
            card(it).assertExists()
        }
        listOf("Starting reps", "Minimum reps", "Minimum reps per set", "Starting reps per set").forEach { card(it).assertDoesNotExist() }
        compose.onNodeWithText("Start (kg)").assertExists()
        compose.onNodeWithTag("unit_KG").assertIsSelected()
    }

    @Test
    fun `Reps then weight shows the rep range and starting reps per set instead of reps per set`() {
        show(ProgressMode.REPS_THEN_WEIGHT)
        listOf("Minimum reps per set", "Maximum reps per set", "Starting reps per set").forEach { card(it).assertExists() }
        card("Reps per set").assertDoesNotExist()
    }

    @Test
    fun `Timer only shows neither Progress by nor the weight rows`() {
        show(ProgressMode.WEIGHT, windowOnly = true)
        card("Progress by").assertDoesNotExist()
        card("Unit").assertDoesNotExist()
        card("On-time window (hours)").assertExists()
    }

    @Test
    fun `another mode or unit is requested, the current one isn't`() {
        show(ProgressMode.WEIGHT)
        compose.onNodeWithTag("mode_WEIGHT").performClick()
        compose.onNodeWithTag("mode_REPS_THEN_WEIGHT").performClick()
        assertEquals(listOf(ProgressMode.REPS_THEN_WEIGHT), modes)
        compose.onNodeWithTag("unit_KG").performScrollTo().performClick()
        compose.onNodeWithTag("unit_LB").performClick()
        assertEquals(listOf(WeightUnit.LB), units)
    }

    @Test
    fun `the Step stepper cycles the step choices`() {
        show(ProgressMode.WEIGHT)
        compose.onNodeWithContentDescription("Increase Step").performScrollTo().performClick()
        assertEquals(500, weight.steps.step)
        assertEquals(WeightField.STEPS_STEP, fields.last())
    }

    @Test
    fun `the starting weight is picked from the list and saved at once`() {
        show(ProgressMode.WEIGHT)
        compose.onNodeWithTag("value_Starting weight").performScrollTo().assertTextEquals("20").performClick()
        compose.onNodeWithTag("option_Starting weight_2250").performClick()
        assertEquals(2250, weight.startWeight)
        assertEquals(WeightField.START_WEIGHT, fields.last())
        assertEquals(1, nowEdits)
    }

    @Test
    fun `My weights lists each weight with remove and add`() {
        show(ProgressMode.WEIGHT, WeightConfig(unit = WeightUnit.KG, kind = WeightsKind.LIST, list = listOf(800, 1200, 1600)))
        compose.onNodeWithTag("weight_value_0").performScrollTo().assertTextEquals("8 kg")
        compose.onNodeWithTag("remove_weight_1").performScrollTo().performClick()
        assertEquals(listOf(800, 1600), weight.list)
        compose.onNodeWithTag("add_weight").performScrollTo().performClick()
        assertEquals(listOf(800, 1600, 2400), weight.list)
        assertEquals(listOf<WeightField?>(WeightField.LIST, WeightField.LIST), fields)
    }

    @Test
    fun `a My weights row edited with a comma keeps the list sorted`() {
        show(ProgressMode.WEIGHT, WeightConfig(unit = WeightUnit.KG, kind = WeightsKind.LIST, list = listOf(800, 1200, 1600)))
        compose.onNodeWithTag("weight_value_0").performScrollTo().performClick()
        compose.onNodeWithTag("edit_field").performTextReplacement("20,5")
        compose.onNodeWithTag("edit_ok").performClick()
        assertEquals(listOf(1200, 1600, 2050), weight.list)
    }

    @Test
    fun `a duplicate weight shows on its row and the page isn't saved`() {
        show(ProgressMode.WEIGHT, WeightConfig(unit = WeightUnit.KG, kind = WeightsKind.LIST, list = listOf(800, 800)))
        compose.onNodeWithTag("support_weight_1").performScrollTo().assertTextEquals("Already in the list")
        compose.onNodeWithTag("save_status").assertTextEquals("Not saved: fix the highlighted field")
    }

    @Test
    fun `weight holds pick a weight, reps in Reps then weight, and say Hold disabled on the top`() {
        show(ProgressMode.REPS_THEN_WEIGHT, WeightConfig(unit = WeightUnit.KG, steps = WeightSteps(2000, 250, 2500), holds = listOf(WeightHold(2500, 12, 3))))
        compose.onNodeWithTag("support_Hold 1 weight").performScrollTo().assertTextEquals("Hold disabled")
        card("Hold 1 reps").assertExists()
        card("Hold 1 for").assertExists()
        compose.onNodeWithTag("add_hold").performScrollTo().performClick()
        assertEquals(2, weight.holds.size)
        assertEquals(WeightField.HOLDS, fields.last())
    }

    @Test
    fun `Weight mode holds have no reps row`() {
        show(ProgressMode.WEIGHT, WeightConfig(unit = WeightUnit.KG, holds = listOf(WeightHold(2500, 8, 3))))
        card("Hold 1 weight").assertExists()
        card("Hold 1 reps").assertDoesNotExist()
        // Weight holds are always At (rev 34 §10): no At | From choice like the Reps holds have.
        compose.onNodeWithTag("hold_1_kind_at").assertDoesNotExist()
        compose.onNodeWithTag("hold_1_kind_from").assertDoesNotExist()
    }

    @Test
    fun `Reps mode still shows the At or From choice when a weight page is also passed`() {
        show(ProgressMode.REPS)
        compose.onNodeWithTag("hold_1_kind_from").assertExists()
    }

    @Test
    fun `a note shows under the field that caused it`() {
        note = WeightNote(WeightField.STEPS_TOP, listOf(WeightMove.TopLowered(2500)))
        show(ProgressMode.WEIGHT)
        compose.onNodeWithTag("weight_note").performScrollTo().assertTextEquals("Top lowered to 25 kg")
    }

    @Test
    fun `the Start fresh dialog names the starting weight, or the starting reps when going back to Reps`() {
        var confirmed = 0
        var to by mutableStateOf(ProgressMode.WEIGHT)
        compose.setContent { HiitTheme { StartFreshDialog(to, onConfirm = { confirmed++ }, onDismiss = {}) } }
        compose.onNodeWithText("This workout restarts at its starting weight. Your streaks and history are kept.").assertExists()
        to = ProgressMode.REPS
        compose.onNodeWithText("This workout restarts at its starting reps. Your streaks and history are kept.").assertExists()
        compose.onNodeWithTag("confirm_start_fresh").performClick()
        assertEquals(1, confirmed)
    }

    @Test
    fun `the unit dialog names the new unit`() {
        var converted = false
        compose.setContent { HiitTheme { ChangeUnitDialog(WeightUnit.LB, onConfirm = { converted = true }, onDismiss = {}) } }
        compose.onNodeWithText("Switch to lb?").assertExists()
        compose.onNodeWithTag("confirm_change_unit").performClick()
        assertTrue(converted)
    }
}
