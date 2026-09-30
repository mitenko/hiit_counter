package com.mitenko.hiitcounter.ui.entries

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.hiitcounter.domain.model.EntryType
import com.mitenko.hiitcounter.ui.theme.HiitTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
        onOpen: (Long) -> Unit = {},
        onMove: (Long, Int) -> Unit = { _, _ -> },
        onCreate: (String, EntryType) -> Unit = { _, _ -> },
    ) {
        compose.setContent {
            HiitTheme {
                EntryListScreen(state, onOpenEntry = onOpen, onMove = onMove, onCreate = onCreate)
            }
        }
    }

    private fun customActionLabels(entryId: Long): List<String> =
        compose.onNodeWithTag("entry_$entryId", useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[SemanticsActions.CustomActions]
            .map { it.label }

    private fun performCustomAction(entryId: Long, label: String) {
        compose.onNodeWithTag("entry_$entryId", useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[SemanticsActions.CustomActions]
            .first { it.label == label }
            .action()
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
    fun `rows show the name, the week count and today's marker and open on tap`() {
        val opened = mutableListOf<Long>()
        show(EntryListUiState.Items(rows), onOpen = { opened += it })
        compose.onNodeWithText("Burpees").assertExists()
        compose.onAllNodesWithText("0× this week").assertCountEquals(3)
        compose.onAllNodesWithContentDescription("Checked in today", useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithText("Lunges").performClick()
        assertEquals(listOf(2L), opened)
    }

    @Test
    fun `there is no reorder node`() {
        show(EntryListUiState.Items(rows))
        compose.onNodeWithTag("reorder").assertDoesNotExist()
    }

    @Test
    fun `each row has a drag handle at least 48 dp`() {
        show(EntryListUiState.Items(rows))
        for (row in rows) {
            compose.onNodeWithTag("drag_${row.id}", useUnmergedTree = true)
                .assertWidthIsEqualTo(48.dp)
                .assertHeightIsEqualTo(48.dp)
        }
    }

    @Test
    fun `the drag handle names the row`() {
        show(EntryListUiState.Items(rows))
        compose.onNodeWithContentDescription("Reorder Lunges", useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun `the drag handle has no dead click action`() {
        show(EntryListUiState.Items(rows))
        val hasClickAction = compose.onNodeWithTag("drag_2", useUnmergedTree = true)
            .fetchSemanticsNode()
            .config
            .contains(SemanticsActions.OnClick)
        assertFalse("the handle should not announce as a clickable button that does nothing", hasClickAction)
    }

    @Test
    fun `tapping the drag handle does not open the row`() {
        val opened = mutableListOf<Long>()
        show(EntryListUiState.Items(rows), onOpen = { opened += it })
        compose.onNodeWithTag("drag_2", useUnmergedTree = true).performClick()
        assertTrue(opened.isEmpty())
    }

    @Test
    fun `dragging a handle persists one move`() {
        val moves = mutableListOf<Pair<Long, Int>>()
        show(EntryListUiState.Items(rows), onMove = { id, delta -> moves += id to delta })
        val rowHeight = with(compose.density) { 56.dp.toPx() }
        compose.onNodeWithTag("drag_1", useUnmergedTree = true).performTouchInput {
            down(center)
            moveBy(Offset(0f, rowHeight * 1.5f))
            up()
        }
        compose.waitForIdle()
        assertEquals(listOf(1L to 1), moves)
    }

    @Test
    fun `dragging the row body does not reorder`() {
        val moves = mutableListOf<Pair<Long, Int>>()
        show(EntryListUiState.Items(rows), onMove = { id, delta -> moves += id to delta })
        val rowHeight = with(compose.density) { 56.dp.toPx() }
        // center of "entry_1" lands on the tile graph (spec rev 9 §2), well clear of the handle on the
        // far right edge. The graph has no pointer input, so only the card's plain clickable is in
        // play here, not draggableHandle.
        compose.onNodeWithTag("entry_1").performTouchInput {
            down(center)
            moveBy(Offset(0f, rowHeight * 1.5f))
            up()
        }
        compose.waitForIdle()
        assertTrue(moves.isEmpty())
    }

    @Test
    fun `after a drop the local order stays until the store catches up`() {
        // onMove is a no-op here: the incoming `rows` never changes, simulating a repository
        // that hasn't re-emitted the reordered rows yet. The drag is split across two
        // performTouchInput calls with a waitForIdle in between, so Compose actually recomposes
        // (and a dragging state is genuinely observed) mid-gesture, the way a real drag does --
        // a single down/moveBy/up block completes before Compose ever recomposes, which would
        // mask the bug entirely. The custom actions (derived purely from the row's current
        // logical index, with no animation involved) are the reliable signal that row 1 stayed a
        // middle row instead of snapping back to being first.
        show(EntryListUiState.Items(rows))
        val rowHeight = with(compose.density) { 56.dp.toPx() }
        compose.onNodeWithTag("drag_1", useUnmergedTree = true).performTouchInput {
            down(center)
            moveBy(Offset(0f, rowHeight * 1.5f))
        }
        compose.waitForIdle()
        compose.onNodeWithTag("drag_1", useUnmergedTree = true).performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(listOf("Move up", "Move down"), customActionLabels(1))
    }

    @Test
    fun `a delete of another row mid-drag is applied once the drag ends, with no onMove`() {
        var list by mutableStateOf(rows)
        val moves = mutableListOf<Pair<Long, Int>>()
        compose.setContent {
            HiitTheme {
                EntryListScreen(EntryListUiState.Items(list), onOpenEntry = {}, onMove = { id, delta -> moves += id to delta }, onCreate = { _, _ -> })
            }
        }
        val rowHeight = with(compose.density) { 56.dp.toPx() }
        compose.onNodeWithTag("drag_1", useUnmergedTree = true).performTouchInput {
            down(center)
            moveBy(Offset(0f, rowHeight * 1.5f))
        }
        compose.waitForIdle()
        // While the drag is in progress, the store's list changes underneath it (a delete
        // elsewhere removed Squats: the id set shrank). This must not be dropped, and the
        // in-progress drag (now against a stale id set) must not be persisted.
        list = rows.filter { it.id != 3L }
        compose.waitForIdle()
        compose.onNodeWithTag("drag_1", useUnmergedTree = true).performTouchInput { up() }
        compose.waitForIdle()
        compose.onNodeWithTag("entry_3").assertDoesNotExist()
        assertTrue(moves.isEmpty())
    }

    @Test
    fun `a delete of the dragged row mid-drag resyncs with no onMove`() {
        var list by mutableStateOf(rows)
        val moves = mutableListOf<Pair<Long, Int>>()
        compose.setContent {
            HiitTheme {
                EntryListScreen(EntryListUiState.Items(list), onOpenEntry = {}, onMove = { id, delta -> moves += id to delta }, onCreate = { _, _ -> })
            }
        }
        val rowHeight = with(compose.density) { 56.dp.toPx() }
        compose.onNodeWithTag("drag_1", useUnmergedTree = true).performTouchInput {
            down(center)
            moveBy(Offset(0f, rowHeight * 1.5f))
        }
        compose.waitForIdle()
        // The dragged row itself (Burpees, 1) gets deleted elsewhere mid-drag.
        list = rows.filter { it.id != 1L }
        compose.waitForIdle()
        compose.onNodeWithTag("drag_1", useUnmergedTree = true).performTouchInput { up() }
        compose.waitForIdle()
        compose.onNodeWithTag("entry_1").assertDoesNotExist()
        assertTrue(moves.isEmpty())
    }

    @Test
    fun `an unrelated field change mid-drag does not discard the drag`() {
        var list by mutableStateOf(rows)
        val moves = mutableListOf<Pair<Long, Int>>()
        compose.setContent {
            HiitTheme {
                EntryListScreen(EntryListUiState.Items(list), onOpenEntry = {}, onMove = { id, delta -> moves += id to delta }, onCreate = { _, _ -> })
            }
        }
        val rowHeight = with(compose.density) { 56.dp.toPx() }
        compose.onNodeWithTag("drag_1", useUnmergedTree = true).performTouchInput {
            down(center)
            moveBy(Offset(0f, rowHeight * 1.5f))
        }
        compose.waitForIdle()
        // Same ids, just a total/reps change elsewhere (e.g. a check-in bumped Lunges' total).
        list = list.map { if (it.id == 2L) it.copy(reps = 99) else it }
        compose.waitForIdle()
        compose.onNodeWithTag("drag_1", useUnmergedTree = true).performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(listOf(1L to 1), moves)
    }

    @Test
    fun `a same-id-set re-emission mid-drag - the previous drag's own confirmation - does not discard the second drag`() {
        var list by mutableStateOf(rows)
        val moves = mutableListOf<Pair<Long, Int>>()
        compose.setContent {
            HiitTheme {
                EntryListScreen(EntryListUiState.Items(list), onOpenEntry = {}, onMove = { id, delta -> moves += id to delta }, onCreate = { _, _ -> })
            }
        }
        val rowHeight = with(compose.density) { 56.dp.toPx() }

        // First drag: Burpees (1) down past Lunges (2). `list` (the store) never catches up to
        // this during the test, standing in for the repository not having re-emitted it yet.
        compose.onNodeWithTag("drag_1", useUnmergedTree = true).performTouchInput {
            down(center)
            moveBy(Offset(0f, rowHeight * 1.5f))
            up()
        }
        compose.waitForIdle()
        val confirmedOrder = listOf(rows[1], rows[0], rows[2]) // Lunges, Burpees, Squats

        // Second drag: Squats (3), now at the bottom of the (already locally reordered) list, up
        // by one step.
        compose.onNodeWithTag("drag_3", useUnmergedTree = true).performTouchInput {
            down(center)
            moveBy(Offset(0f, -rowHeight * 1.5f))
        }
        compose.waitForIdle()
        // Mid-drag, the store catches up to the FIRST drag's own confirmed order: the same ids,
        // just reordered. This must not discard the second, still-in-progress drag.
        list = confirmedOrder
        compose.waitForIdle()
        compose.onNodeWithTag("drag_3", useUnmergedTree = true).performTouchInput { up() }
        compose.waitForIdle()

        assertEquals(listOf(1L to 1, 3L to -1), moves)
        // Final order: Lunges, Squats, Burpees.
        assertEquals(listOf("Move down"), customActionLabels(2))
        assertEquals(listOf("Move up", "Move down"), customActionLabels(3))
        assertEquals(listOf("Move up"), customActionLabels(1))
    }

    @Test
    fun `Move up and Move down custom actions exist except at the edges`() {
        show(EntryListUiState.Items(rows))
        assertEquals(listOf("Move down"), customActionLabels(1))
        assertEquals(listOf("Move up", "Move down"), customActionLabels(2))
        assertEquals(listOf("Move up"), customActionLabels(3))
    }

    @Test
    fun `performing Move down on the first row calls through and re-renders the moved row`() {
        var list by mutableStateOf(rows)
        compose.setContent {
            HiitTheme {
                EntryListScreen(
                    EntryListUiState.Items(list),
                    onOpenEntry = {},
                    onMove = { id, delta ->
                        val i = list.indexOfFirst { it.id == id }
                        val to = (i + delta).coerceIn(0, list.lastIndex)
                        list = list.toMutableList().apply { add(to, removeAt(i)) }
                    },
                    onCreate = { _, _ -> },
                )
            }
        }
        performCustomAction(entryId = 1, label = "Move down")
        compose.waitForIdle()
        // Row 1 (Burpees) moved off the top: it's a middle row now, so it offers both directions.
        assertEquals(listOf("Move up", "Move down"), customActionLabels(1))
        // Row 2 (Lunges) took the top spot: only Move down.
        assertEquals(listOf("Move down"), customActionLabels(2))
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
    fun `a Timer only row reads its week count with today's marker`() {
        show(EntryListUiState.Items(listOf(EntryRow(4, "Stretch", 48, checkedInToday = true, type = EntryType.CHECK_IN, streak = 5, weekCount = 2))))
        compose.onNodeWithText("2× this week").assertExists()
        compose.onNodeWithText("Streak", substring = true).assertDoesNotExist()
        compose.onAllNodesWithContentDescription("Checked in today", useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun `the create dialog offers Workout or Timer only, defaulting to Workout`() {
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

    @Test
    fun `the title REPKIT is centred in the top bar`() {
        show(EntryListUiState.Items(rows))
        compose.onNodeWithTag("title").assertTextEquals("REPKIT")
        val title = compose.onNodeWithTag("title").fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertEquals(root.center.x, title.center.x, 0.5f)
    }

    @Test
    fun `a tile shows this week's count and no rep total or streak`() {
        show(EntryListUiState.Items(listOf(EntryRow(1, "Burpees", 65, checkedInToday = false, streak = 4, weekCount = 3))))
        compose.onNodeWithText("3× this week").assertExists()
        compose.onNodeWithText("Reps", substring = true).assertDoesNotExist()
        compose.onNodeWithText("65", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Streak", substring = true).assertDoesNotExist()
    }

    @Test
    fun `each tile has a 96 by 32 dp graph that describes its last 4 weeks`() {
        show(EntryListUiState.Items(listOf(rows[0].copy(tile = TileData(count = 12)), rows[1].copy(tile = TileData(count = 1)), rows[2])))
        compose.onNodeWithTag("tile_1", useUnmergedTree = true).assertWidthIsEqualTo(96.dp).assertHeightIsEqualTo(32.dp)
        compose.onNodeWithContentDescription("12 check-ins in the last 4 weeks", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("1 check-in in the last 4 weeks", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("0 check-ins in the last 4 weeks", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `the graph sits just before the drag handle and adds no touch target`() {
        show(EntryListUiState.Items(rows))
        val gap = with(compose.density) { 8.dp.toPx() }
        for (row in rows) {
            val tile = compose.onNodeWithTag("tile_${row.id}", useUnmergedTree = true).fetchSemanticsNode()
            val handle = compose.onNodeWithTag("drag_${row.id}", useUnmergedTree = true).fetchSemanticsNode()
            assertTrue(tile.boundsInRoot.right <= handle.boundsInRoot.left)
            assertTrue(handle.boundsInRoot.left - tile.boundsInRoot.right <= gap + 0.5f)
            assertFalse(tile.config.contains(SemanticsActions.OnClick))
        }
    }

    @Test
    fun `tiles are at least 72 dp tall and 8 dp apart`() {
        show(EntryListUiState.Items(rows))
        val gap = with(compose.density) { 8.dp.toPx() }
        rows.forEach { compose.onNodeWithTag("entry_${it.id}").assertHeightIsAtLeast(72.dp) }
        rows.zipWithNext { upper, lower ->
            val a = compose.onNodeWithTag("entry_${upper.id}").fetchSemanticsNode().boundsInRoot
            val b = compose.onNodeWithTag("entry_${lower.id}").fetchSemanticsNode().boundsInRoot
            assertEquals(gap, b.top - a.bottom, 0.5f)
        }
    }
}
