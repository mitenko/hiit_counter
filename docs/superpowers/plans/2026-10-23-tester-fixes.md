# Closed-test fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Five fixes from the Phase 1 testers: the name error waits for an edit, the timer controls get captions, prepare shows the countdown as the big number with "Next: …" under it, the reps column gets a "Reps" heading, and the Spanish type name fits on one line.

**Architecture:**
- **Name dialog:** `NameDialog` gains a `touched` flag.
- **Timer captions:** a small caption composable is added under each timer control.
- **Prepare:** `TimerUiMapper` gives PREPARE a `centerCountdown` and a `nextText` (pure, test-first, with
  `TimerText.formatCountdown`), and `TimerScreen` renders them in the existing `RingCentre` slots.
- **Reps heading:** the reps column's row is bottom-aligned, with a heading over the column.
- **Spanish:** the type name changes through the l10n generator.

**Tech Stack:** Kotlin, Jetpack Compose / Material 3, Robolectric Compose tests (`@Config(sdk = [34])`), JUnit.

**Spec:** `docs/superpowers/specs/2026-10-23-tester-fixes-design.md` (rev 42, including its review notes).

## Global Constraints

- **Name dialog:**
  - The error shows only when `touched` and the name is invalid. Before the first edit the supporting text is the
    counter (`0/40`).
  - OK stays disabled while the name is invalid.
  - Any `onValueChange` sets `touched`, typing and paste/IME commits alike.
- **Captions:**
  - Cue toggles: **Sound / Vibrate / Voice**. Transport: **Back / Pause or Resume / Skip**.
  - `labelSmall`, `TimerColors.text` at 70% alpha, 4 dp under the control, `maxLines = 1` with an ellipsis.
  - Visible, but `clearAndSetSemantics {}` on the caption only, never on the control.
- **Prepare:**
  - The big number is the countdown: whole seconds under 60 (`"7"`), else `m:ss` (`"1:30"`).
  - It's sized at 0.3 of the ring width for whole seconds and 0.22 for `m:ss`, neutral colour, not dimmed.
  - Under it: **Next: 42** for Counter (the first set's reps) or **Next: 8 sets** for Timer Only (sets to go), in
    `titleMedium`, `TimerColors.text` at 70%.
  - The label is **GET READY**, plus the weight on the same line 8 dp after it in a weight run (`GET READY  16 kg`).
  - WORK, REST, COOLDOWN and DONE are unchanged, and the merged ring description is unchanged.
- **Reps heading:**
  - **Reps**, `labelSmall`, `onSurfaceVariant`, centred over the 56 dp column, tagged `reps_heading`, hidden from
    the accessibility tree.
  - The chart's top moves down by the heading's height (Counter in Reps mode only).
- **Spanish:**
  - `type_check_in` becomes **"Temporizador"**, and `info_type` / `info_voice` drop "Solo".
  - The New workout segmented-button labels are `maxLines = 1` with an ellipsis.
- **New strings:**

  | Key | English |
  | --- | --- |
  | `cue_label_sound` | Sound |
  | `cue_label_vibration` | Vibrate |
  | `cue_label_voice` | Voice |
  | `skip_label` | Skip |
  | `timer_next_reps` (plural) | Next: %1$d |
  | `timer_next_sets` (plural) | Next: %1$d sets |
  | `reps_heading` | Reps |

  All need es / zh-rCN / hi through `\\DEVMONSTER\ethor\claude_share\repkit-l10n\claude_l10n_data.py` (back it up
  first) and `claude_l10n_gen.py`. Never hand-edit `values-xx`.
- **Project rules:**
  - All user text comes from resources. `HardCodedTextGuardTest` guards `ui/` and `domain/`, and `domain/` stays
    pure Kotlin.
  - Build and test through rgradle only (`remote-gradle` skill). The full gate is
    `assembleDebug testDebugUnitTest lintDebug`.
  - Commit as `mitenko <mitenko@gmail.com>`, with no attribution lines.
  - Kotlin files are UTF-8 with CRLF in the working copy.

## Review Focus

1. **A prepare of exactly 60 s, or longer:** the countdown flips from `1:00` to `59` at the boundary (not
   `0:59`), with no clipping inside the ring at 0.22 (Task 4: `formatCountdown(60)` and `(59)`, plus a 90 s
   Compose case).
2. **A weight run in prepare:** the label row reads "GET READY" with the weight after it, and Next shows the reps
   (Task 4).
3. **The timer on a small screen (320 dp wide) with captions:** nothing overlaps, and the ring and controls still
   show (Task 3: extend the existing 320 dp test to assert the captions are displayed).
4. **Rename opening with an existing name, then clearing it:** the error appears and OK is disabled (Task 1).
5. **Spanish with "Temporizador" in Entry Settings' Type row and Type dialog:** no layout break (Task 2 changes
   the string; Task 6 runs a single-line check on the dialog button).

---

### Task 1: NameDialog waits for an edit before showing an error

**Files:**
- Modify: `app/src/main/kotlin/com/mitenko/repkit/ui/common/NameDialog.kt`
- Test: `app/src/test/kotlin/com/mitenko/repkit/ui/common/NameDialogTest.kt` (create; Robolectric Compose)

**Interfaces:** none new; `NameDialog`'s signature is unchanged.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.mitenko.repkit.ui.common

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.repkit.ui.theme.HiitTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Spec rev 42 §1: the empty-name error waits for the first edit. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class NameDialogTest {
    @get:Rule val compose = createComposeRule()

    private fun show(initial: String) = compose.setContent {
        HiitTheme { NameDialog(title = "New workout", initial = initial, onConfirm = {}, onDismiss = {}) }
    }

    @Test fun `a new empty dialog shows the counter, not the error, and OK is disabled`() {
        show("")
        compose.onNodeWithText("0/40").assertExists()
        compose.onNodeWithText("Enter a name").assertDoesNotExist()
        compose.onNodeWithTag("name_ok").assertIsNotEnabled()
    }

    @Test fun `typing then clearing shows the error`() {
        show("")
        compose.onNodeWithTag("name_field").performTextInput("Push")
        compose.onNodeWithTag("name_field").performTextClearance()
        compose.onNodeWithText("Enter a name").assertExists()
        compose.onNodeWithTag("name_ok").assertIsNotEnabled()
    }

    @Test fun `a value replaced in one action counts as an edit`() {
        show("")
        compose.onNodeWithTag("name_field").performTextReplacement("   ")
        compose.onNodeWithText("Enter a name").assertExists()
        compose.onNodeWithTag("name_ok").assertIsNotEnabled()
    }

    @Test fun `rename opens without an error and shows it once cleared`() {
        show("Squats")
        compose.onNodeWithText("Enter a name").assertDoesNotExist()
        compose.onNodeWithTag("name_field").performTextClearance()
        compose.onNodeWithText("Enter a name").assertExists()
        compose.onNodeWithTag("name_ok").assertIsNotEnabled()
    }
}
```

  If `EntryNames.validate("   ")` isn't `Empty` (trimmed), use `""` via `performTextReplacement("")` after a
  `performTextInput("a")`. The point is a single-action edit.

- [ ] **Step 2: Run them and confirm they fail.** Run `rgradle.ps1 testDebugUnitTest --tests *NameDialogTest*`.
  Expected: the first test fails, because "Enter a name" shows on open.

- [ ] **Step 3: Implement.** In `NameDialog`:

```kotlin
    // Spec rev 42 §1: no error until the first edit (typing, paste or an IME commit), so a new dialog opens calm.
    var touched by rememberSaveable { mutableStateOf(false) }
    val check = EntryNames.validate(text.text)
    val error = check.uiText()?.resolve()?.takeIf { touched }
```

  Change `onValueChange = { text = it }` to `onValueChange = { text = it; touched = true }`. Keep `enabled = check is NameCheck.Ok` as it is. Fix up the KDoc ("an inline message says why otherwise" becomes "…once the name has been edited").

- [ ] **Step 4: Run the tests and confirm they pass,** plus `--tests *EntryListScreenTest* --tests *EntrySettings*`,
  which use the dialog. Any existing test asserting the error right after opening must be updated to type first;
  say so in the report.
- [ ] **Step 5: Commit:** `"Name dialog: show the empty-name error only after an edit (spec rev 42 §1)"`.

---

### Task 2: Strings and translations

**Files:**
- Modify: `app/src/main/res/values/strings.xml`; generated `values-es`, `values-zh-rCN` and `values-hi`
  `strings.xml`; the external l10n data file
- Test: lint (MissingTranslation) in the full gate

**Interfaces:**
- Produces:
  - `R.string.cue_label_sound`, `R.string.cue_label_vibration`, `R.string.cue_label_voice`;
  - `R.string.skip_label`, `R.string.reps_heading`;
  - `R.plurals.timer_next_reps`, `R.plurals.timer_next_sets`.

- [ ] **Step 1: English.** Add after `cue_voice_off` in `values/strings.xml`:

```xml
    <!-- Spec rev 42 §2: captions under the timer controls (screen readers skip them; the controls say their state) -->
    <string name="cue_label_sound">Sound</string>
    <string name="cue_label_vibration">Vibrate</string>
    <string name="cue_label_voice">Voice</string>
    <string name="skip_label">Skip</string>
    <!-- Spec rev 42 §3: under the prepare countdown -->
    <plurals name="timer_next_reps">
        <item quantity="one">Next: %1$d</item>
        <item quantity="other">Next: %1$d</item>
    </plurals>
    <plurals name="timer_next_sets">
        <item quantity="one">Next: %1$d set</item>
        <item quantity="other">Next: %1$d sets</item>
    </plurals>
    <!-- Spec rev 42 §4: over the entry screen's reps column -->
    <string name="reps_heading">Reps</string>
```

- [ ] **Step 2: Translations.** Back up `claude_l10n_data.py` and `repkit-translations-review.csv` with a
  `.before-testerfixes` suffix. Append the block below to the data file, then regenerate from the repo root with
  `python //DEVMONSTER/ethor/claude_share/repkit-l10n/claude_l10n_gen.py //DEVMONSTER/ethor/claude_share/repkit-l10n //DEVMONSTER/ethor/claude_share/repkit-l10n/repkit-translations-review.csv`.
  The data file's `T` values are `(es, zh, hi)` for strings and `P(es_one, es_other, zh, hi_one, hi_other)` for
  plurals.

```python
# --- Closed-test fixes (spec rev 42, feat/tester-fixes, 2026-10-23) ---
T.update({
 "cue_label_sound": ("Sonido", "声音", "ध्वनि"),
 "cue_label_vibration": ("Vibrar", "振动", "कंपन"),
 "cue_label_voice": ("Voz", "语音", "आवाज़"),
 "skip_label": ("Saltar", "跳过", "छोड़ें"),
 "timer_next_reps": P("Siguiente: %1$d", "Siguiente: %1$d", "下一组：%1$d", "अगला: %1$d", "अगला: %1$d"),
 "timer_next_sets": P("Siguiente: %1$d serie", "Siguiente: %1$d series", "下一组：%1$d 组", "अगला: %1$d सेट", "अगला: %1$d सेट"),
 "reps_heading": ("Reps", "次数", "रेप्स"),
})
# Spec rev 42 §5: the Spanish Timer Only name is "Temporizador" (pairs with "Contador").
_set("type_check_in", 0, "Temporizador")
```

  Before using `_set`, check its signature in the data file (it exists: `def _set(key, idx, value)`). Then update
  `info_type` and `info_voice` for Spanish the same way, using `_set(key, 0, …)` with the current Spanish text and
  "Solo temporizador" replaced by "Temporizador":
  - `info_type`: "…Los de Temporizador usan el mismo temporizador sin contar repeticiones…"
  - `info_voice`: "…En los entrenamientos de Temporizador, dice el número de serie."

  Read the current values from `values-es/strings.xml` and keep everything else identical.

- [ ] **Step 3: Check the diff.** `git diff --stat app/src/main/res` should show only the 7 new keys (×4 files) and
  the 3 changed Spanish strings. `grep -c "Solo temporizador" app/src/main/res/values-es/strings.xml` should be `0`.
- [ ] **Step 4: Run the full gate** (lint must pass MissingTranslation).
- [ ] **Step 5: Commit:** `"Strings for the closed-test fixes; Spanish Timer Only is Temporizador (spec rev 42 §5, §6)"`.

---

### Task 3: Captions under the timer controls

**Files:**
- Modify: `app/src/main/kotlin/com/mitenko/repkit/ui/timer/TimerScreen.kt` (`CueToggleRow`, the transport `Row`)
- Test: `app/src/test/kotlin/com/mitenko/repkit/ui/timer/TimerScreenTest.kt`

**Interfaces:** consumes Task 2's `cue_label_*` and `skip_label`, and the existing `skip_back`, `pause` and `resume`.

- [ ] **Step 1: Write the failing tests** in `TimerScreenTest`, using its existing helpers to show a WORK state and a
  paused state. Use the same helper and `CueConfig` the file already uses.

```kotlin
    @Test fun `the controls have visible captions`() {
        showWork() // the file's existing helper for a running WORK state
        listOf("Sound", "Vibrate", "Voice", "Back", "Pause", "Skip").forEach {
            compose.onNodeWithText(it, useUnmergedTree = true).assertIsDisplayed()
        }
    }

    @Test fun `the pause caption follows the state`() {
        showWork(paused = true)
        compose.onNodeWithText("Resume", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun `captions are not in the accessibility tree`() {
        showWork()
        // Merged tree: the control is found by its description, and no node is labelled by the bare caption text.
        compose.onNodeWithContentDescription("Sound on").assertExists()
        compose.onAllNodesWithText("Vibrate").assertCountEquals(0)
        compose.onAllNodesWithText("Skip").assertCountEquals(0)
    }
```

  In the existing 320 dp layout test, also assert `onNodeWithText("Skip", useUnmergedTree = true).assertIsDisplayed()`.
  Adapt the helper names to the ones in the file; if there's no `paused` parameter, build the state the way the
  file's other paused test does.

- [ ] **Step 2: Run them and confirm they fail** (`--tests *TimerScreenTest*`).

- [ ] **Step 3: Implement.** Add a private caption composable in `TimerScreen.kt`:

```kotlin
/** Spec rev 42 §2: a visible caption under a timer control; screen readers skip it (the control says its state). */
@Composable
private fun ControlCaption(text: String, color: Color) {
    Text(
        text,
        color = color.copy(alpha = 0.7f),
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 4.dp).clearAndSetSemantics {},
    )
}
```

  - **Cue toggles:** in `CueToggleRow`, wrap each `CueToggleButton` in
    `Column(horizontalAlignment = Alignment.CenterHorizontally) { CueToggleButton(...); ControlCaption(stringResource(R.string.cue_label_sound), tc.text) }`,
    and the same for vibration and voice. `CueToggleRow` needs `tc`: pass `LocalTimerColors.current.text` as a
    `captionColor: Color` parameter.
  - **Transport:** in the transport `Row`, wrap each button in the same kind of `Column` with its caption:
    - `stringResource(R.string.skip_back)` under skip back;
    - `stringResource(if (ui.paused) R.string.resume else R.string.pause)` under pause;
    - `stringResource(R.string.skip_label)` under skip forward.

    Keep the Row's `spacedBy(56.dp)` and `CenterVertically`. The Columns give the 48 dp and 72 dp buttons their
    captions without moving the buttons' centres apart.
  - **Imports:** `androidx.compose.ui.semantics.clearAndSetSemantics` and the layout imports as needed.

- [ ] **Step 4: Run the tests and confirm they pass,** including the 320 dp test and the existing tag-based tests
  (`cue_sound`, `skip_back` and the rest must still resolve).
- [ ] **Step 5: Run the full gate, then commit:** `"Timer: captions under the cue toggles and transport controls (spec rev 42 §2)"`.

---

### Task 4: Prepare shows the countdown as the big number

**Files:**
- Modify: `app/src/main/kotlin/com/mitenko/repkit/domain/TimerText.kt`,
  `app/src/main/kotlin/com/mitenko/repkit/ui/timer/TimerUiMapper.kt`,
  `app/src/main/kotlin/com/mitenko/repkit/ui/timer/TimerScreen.kt`
- Test: `app/src/test/kotlin/com/mitenko/repkit/domain/TimerTextTest.kt`,
  `app/src/test/kotlin/com/mitenko/repkit/ui/timer/TimerUiMapperTest.kt`, `TimerScreenTest.kt`

**Interfaces:**
- Consumes: Task 2's `R.plurals.timer_next_reps` and `R.plurals.timer_next_sets`.
- Produces:
  - `TimerText.formatCountdown(sec: Int): String`;
  - `TimerUiState.centerCountdown: String? = null`;
  - `TimerUiState.nextText: UiText? = null`.

- [ ] **Step 1: Write the failing tests.**

  `TimerTextTest`:

```kotlin
    @Test fun `formatCountdown is whole seconds under a minute, m colon ss from a minute`() {
        assertEquals("0", TimerText.formatCountdown(0))
        assertEquals("7", TimerText.formatCountdown(7))
        assertEquals("59", TimerText.formatCountdown(59))
        assertEquals("1:00", TimerText.formatCountdown(60))
        assertEquals("1:30", TimerText.formatCountdown(90))
        assertEquals("10:00", TimerText.formatCountdown(600))
    }
```

  `TimerUiMapperTest`: use the file's existing builders for a `TimerState` in PREPARE.

```kotlin
    @Test fun `prepare puts the countdown in the centre and the first set's reps under it`() {
        val ui = TimerUiMapper.map(prepareState(secondsLeft = 7, repsThisSet = 42), "Push-ups")
        assertEquals("7", ui.centerCountdown)
        assertNull(ui.centerNumber)
        assertEquals("", ui.countdownText)
        assertEquals(UiText.Plural(R.plurals.timer_next_reps, 42, listOf(42)), ui.nextText)
        assertEquals(UiText.Res(R.string.timer_label_get_ready), ui.label)
    }

    @Test fun `Timer Only prepare says the sets to go`() {
        val ui = TimerUiMapper.map(prepareState(secondsLeft = 90, sets = 8, set = 1), "Plank", countsReps = false)
        assertEquals("1:30", ui.centerCountdown)
        assertEquals(UiText.Plural(R.plurals.timer_next_sets, 8, listOf(8)), ui.nextText)
    }

    @Test fun `a weight run's prepare shows the weight beside GET READY`() {
        val ui = TimerUiMapper.map(prepareState(secondsLeft = 10, repsThisSet = 10), "Squat", weight = SetWeight(160, WeightUnit.KG))
        assertNotNull(ui.weight)
        assertEquals(UiText.Plural(R.plurals.timer_next_reps, 10, listOf(10)), ui.nextText)
    }

    @Test fun `work keeps the rep number and no centre countdown`() {
        val ui = TimerUiMapper.map(workState(repsThisSet = 42), "Push-ups")
        assertEquals(42, ui.centerNumber)
        assertNull(ui.centerCountdown)
        assertNull(ui.nextText)
    }
```

  Adapt `prepareState` / `workState` to the file's existing helpers. Check `SetWeight`'s real constructor and units
  (weight in tenths of a kg or similar) by reading `domain/Workload.kt` / `SetWeight` first. Also keep the existing
  PREPARE description assertions unchanged.

  `TimerScreenTest`:

```kotlin
    @Test fun `prepare shows the countdown big and Next under it`() {
        showPhase(Phase.PREPARE, secondsLeft = 7, repsThisSet = 42) // adapt to the file's helper
        compose.onNodeWithTag("center_countdown", useUnmergedTree = true).assertTextEquals("7")
        compose.onNodeWithTag("next_text", useUnmergedTree = true).assertTextEquals("Next: 42")
        compose.onNodeWithTag("center_number", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("countdown", useUnmergedTree = true).assertDoesNotExist()
    }
```

- [ ] **Step 2: Run them and confirm they fail**
  (`--tests *TimerTextTest* --tests *TimerUiMapperTest* --tests *TimerScreenTest*`).

- [ ] **Step 3: Implement.**

  `TimerText` (domain, pure):

```kotlin
    /** Spec rev 42 §3: the prepare countdown as the big number — whole seconds under a minute ("7"), else m:ss ("1:30"). */
    fun formatCountdown(sec: Int): String =
        if (sec < 60) "$sec" else String.format(Locale.ENGLISH, "%d:%02d", sec / 60, sec % 60)
```

  In `TimerUiState`, add after `countdownText`:

```kotlin
    /** Spec rev 42 §3: PREPARE's countdown, drawn as the big number; null in every other phase. */
    val centerCountdown: String? = null,
    /** Spec rev 42 §3: "Next: 42" / "Next: 8 sets" under the prepare countdown; null otherwise. */
    val nextText: UiText? = null,
```

  In `TimerUiMapper.map`, change the PREPARE branch:

```kotlin
            Phase.PREPARE -> base.copy(
                label = UiText.Res(R.string.timer_label_get_ready),
                centerNumber = null,
                centerCountdown = TimerText.formatCountdown(s.phaseSecondsLeft),
                countdownText = "",
                nextText = if (countsReps) {
                    UiText.Plural(R.plurals.timer_next_reps, s.repsThisSet, listOf(s.repsThisSet))
                } else {
                    UiText.Plural(R.plurals.timer_next_sets, setsToGo, listOf(setsToGo))
                },
                description = if (countsReps) { // unchanged from today
                    UiText.Plural(R.plurals.timer_desc_prepare_reps, s.repsThisSet)
                } else {
                    UiText.Plural(R.plurals.timer_desc_prepare_sets, setsToGo)
                },
            )
```

  Then widen the weight line to include prepare:
  `s.phase == Phase.WORK || s.phase == Phase.REST || s.phase == Phase.PREPARE`. Update its comment: "in WORK, the
  rest preview, and beside GET READY (spec rev 42 §3)". Check `UiText.Plural`'s exact constructor (resource,
  quantity, args) in `ui/common/UiText.kt` and match it.

  In `TimerScreen`'s `RingCentre` call:
  - **`number`:** when `ui.centerCountdown != null`, render it as the number, with tag `center_countdown`, in the
    phase colour, not dimmed. The size is `tightTextStyle(if (':' in it) maxWidth * 0.22f converted to sp else numberSize, FontWeight.Bold)`.
    Compute the 0.22 size the same way as `numberSize` (Dp.toSp with `LocalDensity`). Otherwise keep the existing
    `centerNumber` text.
  - **`bottom`:** when `ui.nextText != null`, render
    `Text(it.resolve(), color = tc.text.copy(alpha = 0.7f), style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("next_text"))`.
    Otherwise keep the existing countdown text. `countdownText` is `""` in PREPARE, so the old branch yields null.

- [ ] **Step 4: Run the tests and confirm they pass.** All existing timer tests must still pass; PREPARE tests that
  asserted `center_number` or `countdown` during prepare need updating to the new tags. List them in the report.
- [ ] **Step 5: Run the full gate, then commit:** `"Timer: prepare shows the countdown as the big number with Next under it (spec rev 42 §3)"`.

---

### Task 5: A "Reps" heading over the reps column

**Files:**
- Modify: `app/src/main/kotlin/com/mitenko/repkit/ui/entry/EntryScreen.kt` (`CentreRow`'s non-weight `Row`,
  `RepsColumn`)
- Test: `app/src/test/kotlin/com/mitenko/repkit/ui/entry/EntryScreenTest.kt`

**Interfaces:** consumes Task 2's `R.string.reps_heading`.

- [ ] **Step 1: Write the failing tests** in `EntryScreenTest`, using its existing `state` (a Counter in Reps mode)
  and its weight-mode state, if there is one.

```kotlin
    @Test fun `a Counter's reps column has a Reps heading`() {
        show(state)
        compose.onNodeWithTag("reps_heading", useUnmergedTree = true).assertTextEquals("Reps").assertIsDisplayed()
    }

    @Test fun `the heading is not in the accessibility tree`() {
        show(state)
        compose.onAllNodesWithText("Reps").assertCountEquals(0)
    }

    @Test fun `the chart and the reps column end level`() {
        show(state)
        val chart = compose.onNodeWithTag("chart").getBoundsInRoot()
        val column = compose.onNodeWithTag("reps_column").getBoundsInRoot()
        assertEquals(column.bottom.value, chart.bottom.value, 0.5f)
    }
```

  For a weight-mode state (as the file builds for the prescription card tests), assert `reps_heading` does not
  exist. For a Timer Only state, assert it does not exist either.

- [ ] **Step 2: Run them and confirm they fail** (`--tests *EntryScreenTest*`).

- [ ] **Step 3: Implement.** In `CentreRow`'s else branch:

```kotlin
        // Spec rev 42 §4: bottom-aligned, so the chart drops by the heading's height and its rows stay level with the column.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            ChartArea(state, range, view, shown, Modifier.weight(1f))
            if (state.type == EntryType.WORKOUT) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.reps_heading),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.width(REPS_COLUMN_WIDTH).padding(bottom = 4.dp).clearAndSetSemantics {}.testTag("reps_heading"),
                        textAlign = TextAlign.Center,
                    )
                    RepsColumn(state.reps, highlight, onHighlightShown)
                }
            }
        }
```

  With a Timer Only workout there is no column, so the bottom alignment changes nothing there.

- [ ] **Step 4: Run the tests and confirm they pass,** plus the existing entry screen layout tests.
- [ ] **Step 5: Run the full gate, then commit:** `"Entry screen: a Reps heading over the reps column (spec rev 42 §4)"`.

---

### Task 6: Single-line type buttons, and docs

**Files:**
- Modify: `app/src/main/kotlin/com/mitenko/repkit/ui/entries/EntryListScreen.kt` (`EntryTypeChoice`), `claude.md`,
  the spec's status line
- Test: `app/src/test/kotlin/com/mitenko/repkit/ui/entries/EntryListScreenTest.kt`

- [ ] **Step 1: Write the failing test** in `EntryListScreenTest`. Open the New workout dialog the way the existing
  name-dialog tests do (FAB → `add`), in the Spanish locale (`@Config(qualifiers = "es")` on this test method, or
  however the file already switches locale):

```kotlin
    @Test @Config(qualifiers = "es")
    fun `the type buttons stay one line in Spanish`() {
        show(EntryListUiState.Items(rows))
        compose.onNodeWithTag("add").performClick()
        val counter = compose.onNodeWithTag("type_WORKOUT").getBoundsInRoot()
        val timer = compose.onNodeWithTag("type_CHECK_IN").getBoundsInRoot()
        assertEquals(counter.height.value, timer.height.value, 0.5f)
        compose.onNodeWithText("Temporizador", useUnmergedTree = true).assertExists()
    }
```

- [ ] **Step 2: Run it.** It may already pass, since Task 2 shortened the string. Then temporarily point the test at a
  long label to see it fail, or accept a pass and note that the `maxLines` guard is defensive. Say which in the report.

- [ ] **Step 3: Implement.** In `EntryTypeChoice`:
  `Text(stringResource(type.label), maxLines = 1, overflow = TextOverflow.Ellipsis)`. Import `TextOverflow` if needed.

- [ ] **Step 4: Docs.**
  - **CLAUDE.md:** add a source-of-truth line after the rev 41 (Pro purchase) line:

```markdown
- **Closed-test fixes (approved, rev 42, amends R2/rev 24, rev 22/37/38, rev 9 §3, rev 25):** `docs/superpowers/specs/2026-10-23-tester-fixes-design.md` — from the Phase 1 testers (`docs/tester-feedback-phase1.md`): the name dialog shows its error only after an edit (`touched`); captions under the timer's cue and transport controls (visible, hidden from TalkBack); PREPARE shows the countdown as the big number (`TimerText.formatCountdown`: "7", "1:30") with "Next: 42" / "Next: 8 sets" under it and the weight beside GET READY; a "Reps" heading over the reps column (row bottom-aligned); Spanish Timer Only is "Temporizador" and the type buttons are single-line.
```

  - **The spec's status line:** `**Status:** approved by the user (2026-10-23); implemented.`
- [ ] **Step 5: Run the full gate, then commit:** `"Single-line type buttons; docs (spec rev 42 §5)"`.
