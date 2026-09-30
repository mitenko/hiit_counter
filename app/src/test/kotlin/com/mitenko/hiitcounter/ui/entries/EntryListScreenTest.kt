package com.mitenko.hiitcounter.ui.entries

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.hiitcounter.domain.model.EntryType
import com.mitenko.hiitcounter.ui.theme.HiitTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class EntryListScreenTest {
    @get:Rule val compose = createComposeRule()

    private val rows = listOf(
        EntryRow(1, "Burpees", 65, checkedInToday = true),
        EntryRow(2, "Lunges", 48, checkedInToday = false),
        EntryRow(3, "Squats", 50, checkedInToday = false),
    )

    private fun show(
        state: EntryListUiState,
        reorder: Boolean = false,
        onOpen: (Long) -> Unit = {},
        onUp: (Long) -> Unit = {},
        onDown: (Long) -> Unit = {},
        onCreate: (String, EntryType) -> Unit = { _, _ -> },
    ) {
        compose.setContent {
            HiitTheme {
                EntryListScreen(
                    state, reorder, onOpenEntry = onOpen, onToggleReorder = {},
                    onMoveUp = onUp, onMoveDown = onDown, onCreate = onCreate,
                )
            }
        }
    }

    @Test
    fun `loading shows progress and the add button is disabled`() {
        show(EntryListUiState.Loading)
        compose.onNodeWithTag("loading").assertExists()
        compose.onNodeWithTag("add").assertIsNotEnabled().performClick()
        compose.onNodeWithTag("name_field").assertDoesNotExist()
    }

    @Test
    fun `the empty state offers the first workout`() {
        show(EntryListUiState.Empty)
        compose.onNodeWithText("No workouts yet").assertExists()
        compose.onNodeWithTag("add_first").performClick()
        compose.onNodeWithTag("name_field").assertExists()
    }

    @Test
    fun `rows show the name, reps and today's marker and open on tap`() {
        val opened = mutableListOf<Long>()
        show(EntryListUiState.Items(rows), onOpen = { opened += it })
        compose.onNodeWithText("Reps 65").assertExists()
        compose.onNodeWithText("Reps 48").assertExists()
        compose.onAllNodesWithContentDescription("Checked in today", useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithText("Lunges").performClick()
        assertEquals(listOf(2L), opened)
    }

    @Test
    fun `reorder buttons move rows and disable the edges`() {
        val moves = mutableListOf<String>()
        show(EntryListUiState.Items(rows), reorder = true, onUp = { moves += "up $it" }, onDown = { moves += "down $it" })
        compose.onNodeWithContentDescription("Move Burpees up").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Move Squats down").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Move Burpees down").assertIsEnabled()
        compose.onNodeWithContentDescription("Move Lunges up").performClick()
        compose.onNodeWithContentDescription("Move Lunges down").performClick()
        assertEquals(listOf("up 2", "down 2"), moves)
        compose.onNodeWithTag("add").assertDoesNotExist()
    }

    @Test
    fun `reorder buttons are 48 dp`() {
        show(EntryListUiState.Items(rows), reorder = true)
        compose.onNodeWithContentDescription("Move Burpees up")
            .assertWidthIsEqualTo(48.dp)
            .assertHeightIsEqualTo(48.dp)
    }

    @Test
    fun `a moved row stays scrolled into view`() {
        var list by mutableStateOf((1L..12L).map { EntryRow(it, "Entry $it", 48, false) })
        compose.setContent {
            HiitTheme {
                EntryListScreen(
                    EntryListUiState.Items(list), reorderMode = true, onOpenEntry = {}, onToggleReorder = {}, onMoveUp = {},
                    onMoveDown = { id ->
                        val i = list.indexOfFirst { it.id == id }
                        if (i < list.lastIndex) list = list.toMutableList().apply { add(i + 1, removeAt(i)) }
                    },
                    onCreate = { _, _ -> },
                )
            }
        }
        repeat(10) {
            compose.onNodeWithContentDescription("Move Entry 1 down").performClick()
            compose.waitForIdle()
        }
        assertEquals(10, list.indexOfFirst { it.id == 1L })
        compose.onNodeWithText("Entry 1").assertIsDisplayed()
    }

    @Test
    fun `add creates through the name dialog`() {
        var created: String? = null
        show(EntryListUiState.Items(rows), onCreate = { name, _ -> created = name })
        compose.onNodeWithTag("add").performClick()
        compose.onNodeWithTag("name_field").performTextReplacement("Kettlebell Lunges")
        compose.onNodeWithTag("name_ok").performClick()
        assertEquals("Kettlebell Lunges", created)
        compose.onNodeWithTag("name_field").assertDoesNotExist()
    }

    @Test
    fun `a check-in-only row reads Streak N with today's marker`() {
        show(EntryListUiState.Items(listOf(EntryRow(4, "Stretch", 48, checkedInToday = true, type = EntryType.CHECK_IN, streak = 5))))
        compose.onNodeWithText("Streak 5").assertExists()
        compose.onNodeWithText("Reps 48").assertDoesNotExist()
        compose.onAllNodesWithContentDescription("Checked in today", useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun `the create dialog offers Workout or Check-in only, defaulting to Workout`() {
        val created = mutableListOf<Pair<String, EntryType>>()
        show(EntryListUiState.Items(rows), onCreate = { name, type -> created += name to type })
        compose.onNodeWithTag("add").performClick()
        compose.onNodeWithTag("type_WORKOUT").assertIsSelected()
        compose.onNodeWithTag("type_CHECK_IN").performClick()
        compose.onNodeWithTag("type_CHECK_IN").assertIsSelected()
        compose.onNodeWithTag("name_field").performTextReplacement("Stretch")
        compose.onNodeWithTag("name_ok").performClick()
        assertEquals(listOf("Stretch" to EntryType.CHECK_IN), created)
    }
}
