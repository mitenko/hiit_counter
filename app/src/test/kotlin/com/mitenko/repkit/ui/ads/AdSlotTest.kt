package com.mitenko.repkit.ui.ads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.repkit.domain.Tier
import com.mitenko.repkit.domain.model.CueConfig
import com.mitenko.repkit.domain.model.Phase
import com.mitenko.repkit.domain.model.TimerState
import com.mitenko.repkit.ui.entries.EntryListScreen
import com.mitenko.repkit.ui.entries.EntryListUiState
import com.mitenko.repkit.ui.entries.EntryRow
import com.mitenko.repkit.ui.entry.EntryScreen
import com.mitenko.repkit.ui.entry.EntryUiState
import com.mitenko.repkit.ui.theme.HiitTheme
import com.mitenko.repkit.ui.timer.TimerScreen
import com.mitenko.repkit.ui.timer.TimerUiMapper
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Spec revision 18 §4: ad slots on the entry list and the entry screen only, never the timer, never for Pro. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AdSlotTest {
    @get:Rule val compose = createComposeRule()

    /** Draws a 50 dp banner and records each placement it was asked for. */
    private class FakeAdRenderer : AdRenderer {
        val placements = mutableListOf<AdPlacement>()

        @Composable
        override fun Render(placement: AdPlacement, modifier: Modifier) {
            placements += placement
            Box(modifier.fillMaxWidth().height(50.dp).testTag("fake_ad"))
        }
    }

    private fun setContent(tier: Tier, renderer: AdRenderer, content: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalTier provides tier, LocalAdRenderer provides renderer) {
                HiitTheme { content() }
            }
        }
    }

    @Test
    fun `the v1 renderer takes zero height even on the free tier`() {
        setContent(Tier.FREE, NoAdRenderer) {
            Column(Modifier.testTag("holder")) { AdSlot(AdPlacement.ENTRY_LIST) }
        }
        compose.onNodeWithTag("ad_slot").assertHeightIsEqualTo(0.dp)
        compose.onNodeWithTag("holder").assertHeightIsEqualTo(0.dp)
    }

    @Test
    fun `pro shows no slot even with a real renderer`() {
        val renderer = FakeAdRenderer()
        setContent(Tier.PRO, renderer) { AdSlot(AdPlacement.ENTRY_LIST) }
        compose.onNodeWithTag("ad_slot").assertDoesNotExist()
        compose.onNodeWithTag("fake_ad").assertDoesNotExist()
        assertEquals(emptyList<AdPlacement>(), renderer.placements)
    }

    @Test
    fun `the defaults are v1's - pro and no renderer - so nothing shows`() {
        compose.setContent { HiitTheme { AdSlot(AdPlacement.ENTRY_LIST) } }
        compose.onNodeWithTag("ad_slot").assertDoesNotExist()
    }

    @Test
    fun `free with a renderer shows the slot at the bottom of the entry list`() {
        val renderer = FakeAdRenderer()
        setContent(Tier.FREE, renderer) {
            EntryListScreen(
                EntryListUiState.Items(listOf(EntryRow(1, "Burpees", 65, checkedInToday = false))),
                onOpenEntry = {}, onMove = { _, _ -> }, onCreate = { _, _ -> },
            )
        }
        compose.onNodeWithTag("ad_slot").assertHeightIsEqualTo(50.dp)
        assertEquals(setOf(AdPlacement.ENTRY_LIST), renderer.placements.toSet())
        val root = compose.onNodeWithTag("ad_slot").fetchSemanticsNode().boundsInRoot
        val entry = compose.onNodeWithTag("entry_1").fetchSemanticsNode().boundsInRoot
        assert(root.top >= entry.bottom) { "the slot sits below the list" }
    }

    @Test
    fun `free with a renderer shows the slot on the entry screen`() {
        val renderer = FakeAdRenderer()
        setContent(Tier.FREE, renderer) {
            EntryScreen(
                EntryUiState(name = "Burpees", reps = List(8) { 6 }, loaded = true),
                onBack = {}, onStart = {}, onCheckIn = {}, onOpenSettings = {}, onDismissError = {},
            )
        }
        compose.onNodeWithTag("ad_slot").assertHeightIsEqualTo(50.dp)
        assertEquals(setOf(AdPlacement.ENTRY_SCREEN), renderer.placements.toSet())
    }

    @Test
    fun `the timer screen has no ad slot, even on the free tier`() {
        val renderer = FakeAdRenderer()
        val ui = TimerUiMapper.map(
            TimerState(Phase.WORK, set = 2, sets = 8, phaseSecondsLeft = 15, phaseDurationSec = 20, elapsedSec = 50,
                totalDurationSec = 240, repsThisSet = 8, totalReps = 65, paused = false),
            entryName = "Burpees",
        )
        setContent(Tier.FREE, renderer) {
            TimerScreen(
                ui, CueConfig(),
                onTogglePause = {}, onSkipBack = {}, onSkipForward = {}, onClose = {},
                onToggleSound = {}, onToggleVibration = {}, onToggleVoice = {},
            )
        }
        compose.onNodeWithTag("ad_slot").assertDoesNotExist()
        assertEquals(emptyList<AdPlacement>(), renderer.placements)
    }
}
