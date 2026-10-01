package com.mitenko.repkit.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.repkit.domain.TimerController
import com.mitenko.repkit.domain.model.EntryType
import com.mitenko.repkit.testutil.FakeClock
import com.mitenko.repkit.testutil.FakeEntryRepository
import com.mitenko.repkit.testutil.testEntry
import com.mitenko.repkit.ui.common.ENTRY_ID_ARG
import com.mitenko.repkit.ui.entries.EntryListRoute
import com.mitenko.repkit.ui.entries.EntryListViewModel
import com.mitenko.repkit.ui.theme.HiitTheme
import kotlinx.coroutines.MainScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class EntrySettingsScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun show(
        state: EntrySettingsUiState = EntrySettingsUiState(name = "Burpees"),
        onOpen: (SettingsPage) -> Unit = {},
        onRename: (String) -> Unit = {},
        onDelete: () -> Unit = {},
        onSetType: (EntryType) -> Unit = {},
    ) {
        compose.setContent {
            HiitTheme {
                EntrySettingsScreen(
                    state, onBack = {}, onOpen = onOpen, onRename = onRename, onDuplicate = {}, onDelete = onDelete,
                    onSetType = onSetType,
                )
            }
        }
    }

    @Test
    fun `pages open their settings`() {
        val opened = mutableListOf<SettingsPage>()
        show(onOpen = { opened += it })
        SettingsPage.entries.forEach { compose.onNodeWithTag("page_${it.name}").performScrollTo().performClick() }
        assertEquals(SettingsPage.entries.toList(), opened)
    }

    @Test
    fun `rename opens a prefilled name dialog`() {
        var renamed: String? = null
        show(onRename = { renamed = it })
        compose.onNodeWithTag("rename").performScrollTo().performClick()
        compose.onNodeWithTag("name_field").assertTextContains("Burpees")
        compose.onNodeWithTag("name_field").performTextReplacement("Kettlebell Lunges")
        compose.onNodeWithTag("name_ok").performClick()
        assertEquals("Kettlebell Lunges", renamed)
    }

    @Test
    fun `delete asks for confirmation with the name and the warning`() {
        var deletes = 0
        show(onDelete = { deletes++ })
        compose.onNodeWithTag("delete").performScrollTo().performClick()
        compose.onNodeWithText("Delete Burpees?").assertExists()
        compose.onNodeWithText("Its rep total, streaks and settings will be lost.").assertExists()
        compose.onNodeWithTag("confirm_delete").performClick()
        assertEquals(1, deletes)
    }

    @Test
    fun `delete is disabled with its hint while the entry is busy`() {
        show(EntrySettingsUiState(name = "Burpees", busy = true))
        compose.onNodeWithTag("delete").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("busy_hint").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Stop the workout first").assertExists()
    }

    @Test
    fun `duplicate shows the suffixed copy in the list`() {
        val repo = FakeEntryRepository(listOf(testEntry(1, "Burpees")))
        val controller = TimerController(MainScope()) { 0L }
        val settingsVm = EntrySettingsViewModel(SavedStateHandle(mapOf(ENTRY_ID_ARG to 1L)), repo, controller)
        val listVm = EntryListViewModel(repo, FakeClock())
        var copied by mutableStateOf(false)
        compose.setContent {
            HiitTheme {
                if (copied) {
                    EntryListRoute(onOpenEntry = {}, onCreated = {}, vm = listVm)
                } else {
                    EntrySettingsRoute(
                        onBack = {}, onOpen = {}, onDuplicated = { copied = true }, onDeleted = {}, onEntryGone = {},
                        vm = settingsVm,
                    )
                }
            }
        }
        compose.onNodeWithTag("duplicate").performScrollTo().performClick()
        compose.onNodeWithText("Burpees copy").assertIsDisplayed()
        compose.onNodeWithText("Burpees").assertIsDisplayed()
    }

    @Test
    fun `the type row opens a radio dialog and only OK with a new type saves it`() {
        val chosen = mutableListOf<EntryType>()
        show(onSetType = { chosen += it })
        compose.onNodeWithTag("type_value", useUnmergedTree = true).assertTextEquals("Counter")
        compose.onNodeWithContentDescription("About Type").assertExists()
        compose.onNodeWithTag("type").performClick()
        compose.onNodeWithTag("type_option_CHECK_IN").performClick()
        compose.onNodeWithTag("type_cancel").performClick()
        assertTrue(chosen.isEmpty())
        compose.onNodeWithTag("type").performClick()
        compose.onNodeWithTag("type_ok").performClick() // OK with the current type (Workout) writes nothing
        assertTrue(chosen.isEmpty())
        compose.onNodeWithTag("type").performClick()
        compose.onNodeWithTag("type_option_WORKOUT").assertIsSelected() // Cancel discarded the pick
        compose.onNodeWithTag("type_option_CHECK_IN").performClick()
        compose.onNodeWithTag("type_ok").performClick()
        assertEquals(listOf(EntryType.CHECK_IN), chosen)
    }

    @Test
    fun `a Timer only entry shows the Timing and Cues rows again`() {
        show(EntrySettingsUiState(name = "Stretch", type = EntryType.CHECK_IN))
        compose.onNodeWithTag("type_value", useUnmergedTree = true).assertTextEquals("Timer Only")
        compose.onNodeWithTag("page_TIMING").assertExists()
        compose.onNodeWithTag("page_CUES").assertExists()
        compose.onNodeWithTag("page_PROGRESSION").assertExists()
        compose.onNodeWithTag("page_CURRENT").assertExists()
    }

    /** Every Entry Settings option, top to bottom (spec rev 9 §4). */
    private val optionTags = listOf("card_type") + SettingsPage.entries.map { "page_${it.name}" } + listOf("rename", "duplicate", "delete")

    @Test
    fun `every option is a full-width card at least 56 dp tall`() {
        show()
        val rootWidth = compose.onRoot().fetchSemanticsNode().boundsInRoot.width
        val inset = with(compose.density) { 32.dp.toPx() } // SettingsScaffold's 16 dp either side
        optionTags.forEach { tag ->
            // The bottom rows sit below the fold, and Robolectric reports a zero rect for a node's
            // bounds until it's scrolled into view (pre-approved test-only deviation).
            val node = compose.onNodeWithTag(tag).performScrollTo()
            node.assertHeightIsAtLeast(56.dp)
            assertEquals(tag, rootWidth - inset, node.fetchSemanticsNode().boundsInRoot.width, 0.5f)
        }
    }

    @Test
    fun `the option cards sit 8 dp apart, with Delete last`() {
        show()
        val gap = with(compose.density) { 8.dp.toPx() }
        optionTags.zipWithNext { upper, lower ->
            // Scrolling the lower (later) tag into view keeps the pair in the same scroll snapshot,
            // since the gap is tiny next to the viewport (pre-approved test-only deviation).
            compose.onNodeWithTag(lower).performScrollTo()
            val a = compose.onNodeWithTag(upper).fetchSemanticsNode().boundsInRoot
            val b = compose.onNodeWithTag(lower).fetchSemanticsNode().boundsInRoot
            assertEquals("$upper → $lower", gap, b.top - a.bottom, 0.5f)
        }
    }
}
