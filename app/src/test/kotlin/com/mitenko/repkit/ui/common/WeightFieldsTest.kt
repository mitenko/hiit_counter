package com.mitenko.repkit.ui.common

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.repkit.domain.model.ProgressMode
import com.mitenko.repkit.domain.model.WeightUnit
import com.mitenko.repkit.ui.theme.HiitTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class WeightFieldsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `a picker steps along its list and picks from a list of weights`() {
        val steps = mutableListOf<Boolean>()
        val picks = mutableListOf<Int>()
        compose.setContent {
            HiitTheme {
                WeightPickerField(
                    "Starting weight (kg)", listOf(800, 1200, 1600), 1200, WeightUnit.KG,
                    onStep = { steps += it }, onPick = { picks += it }, a11yLabel = "Starting weight",
                )
            }
        }
        compose.onNodeWithTag("value_Starting weight").assertTextEquals("12")
        compose.onNodeWithContentDescription("Increase Starting weight").performClick()
        compose.onNodeWithContentDescription("Decrease Starting weight").performClick()
        assertEquals(listOf(true, false), steps)
        compose.onNodeWithTag("value_Starting weight").performClick()
        compose.onNodeWithText("16 kg").assertExists()
        compose.onNodeWithTag("option_Starting weight_1200").assertIsSelected()
        compose.onNodeWithTag("option_Starting weight_1600").performClick()
        assertEquals(listOf(1600), picks)
        compose.onNodeWithText("16 kg").assertDoesNotExist()
    }

    @Test
    fun `a picker with nothing to pick shows a dash and opens nothing`() {
        compose.setContent { HiitTheme { WeightPickerField("Step", emptyList(), null, WeightUnit.KG, onStep = {}, onPick = {}) } }
        compose.onNodeWithTag("value_Step").assertTextEquals("—").performClick()
        compose.onNodeWithTag("option_Step_250").assertDoesNotExist()
    }

    @Test
    fun `a weight stepper takes a comma and refuses three decimals`() {
        val values = mutableListOf<Int>()
        compose.setContent {
            HiitTheme { WeightStepperField("Start (kg)", 2000, onStep = {}, onDialogValue = { values += it }, a11yLabel = "Start") }
        }
        compose.onNodeWithTag("value_Start").assertTextEquals("20").performClick()
        compose.onNodeWithTag("edit_field").performTextReplacement("20.125")
        compose.onNodeWithText("Enter a number with up to 2 decimals").assertExists()
        compose.onNodeWithTag("edit_ok").assertIsNotEnabled()
        compose.onNodeWithTag("edit_field").performTextReplacement("22,5")
        compose.onNodeWithTag("edit_ok").performClick()
        assertEquals(listOf(2250), values)
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp")
    fun `three choices fit at 320 dp with 48 dp segments`() {
        val chosen = mutableListOf<ProgressMode>()
        compose.setContent {
            HiitTheme {
                ChoiceRow(
                    "Progress by", ProgressMode.entries, ProgressMode.REPS,
                    optionLabel = { stringResource(it.label) }, key = { it.name }, tag = "mode", onSelect = { chosen += it },
                )
            }
        }
        ProgressMode.entries.forEach { compose.onNodeWithTag("mode_${it.name}").assertHeightIsAtLeast(48.dp) }
        compose.onNodeWithTag("mode_REPS").assertIsSelected()
        compose.onNodeWithText("Reps then weight").assertExists()
        compose.onNodeWithTag("mode_REPS_THEN_WEIGHT").performClick()
        assertEquals(listOf(ProgressMode.REPS_THEN_WEIGHT), chosen)
    }

    @Test
    fun `the page status is the worse of two`() {
        assertEquals(SaveStatus.INVALID, SaveStatus.worst(SaveStatus.FAILED, SaveStatus.INVALID))
        assertEquals(SaveStatus.FAILED, SaveStatus.worst(SaveStatus.SAVED, SaveStatus.FAILED))
        assertEquals(SaveStatus.SAVED, SaveStatus.worst(SaveStatus.SAVED, SaveStatus.SAVED))
        assertEquals(SaveStatus.INVALID, SaveStatus.of(valid = false, failed = true))
    }
}
