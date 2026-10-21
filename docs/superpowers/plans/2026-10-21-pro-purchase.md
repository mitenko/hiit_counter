# Pro purchase (PR 1 of 2) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A one-time `repkit_pro` purchase through Google Play Billing that turns the rev 18 seams on: FREE keeps 3 workouts and Light/Dark/Sonar, PRO gets unlimited workouts and Blossom/Grove/Foundry, with a REPKIT Pro row (Go Pro · price, Restore purchase) in ⚙ Settings.

**Architecture:**
- **Domain:** `domain/ProStatus.kt` maps billing query results to a `Tier`, purely and test-first.
- **Billing:** `platform/billing/BillingGateway` is a thin interface over Play Billing, with `PlayBillingGateway`
  as the real implementation and a fake in tests.
- **Entitlements:** `monetisation/BillingEntitlements` implements `Entitlements` and `ProUpgrade` on top of the
  gateway and a DataStore cache, and `MonetisationModule` binds it in place of `UnlockedEntitlements`.
- **Themes:** theme gating is pure in `ui/theme/ThemeMode.kt` (`isProTheme`, `canUseTheme`, a tier-aware
  `effectiveTheme`).
- **UI:** the UI reads `LocalTier` and the list ViewModel.

**Tech Stack:** Kotlin, Compose/Material 3, Hilt, DataStore, coroutines; Play Billing Library 8 (`com.android.billingclient:billing-ktx`); Robolectric Compose tests.

**Spec:** `docs/superpowers/specs/2026-10-21-pro-and-ads-design.md` (rev 41). §1, §3, §4, §5 and §7 PR 1 are in scope; §2 (ads) is PR 2's plan, `docs/superpowers/plans/2026-10-21-ads.md`.

## Global Constraints

- **Product:** the product ID is exactly `repkit_pro`, a one-time, non-consumable in-app product. Never hard-code
  a price; show Play's `formattedPrice`.
- **Tiers:**
  - FREE: `FreeLimits(maxEntries = 3)` (unchanged). Light, Dark and Sonar are free.
  - PRO: unlimited workouts; Blossom, Grove and Foundry (`isProTheme`).
- **Errors:** a `Failed` query never changes the tier or the cache. An `Ok` query writes `pro_cached` at once,
  in both directions (§1.4).
- **The one Pro rule:** `Entitlements.tier` decides Pro everywhere: themes, limit, Settings row (§1.4).
- **Debug override:** a debug build with `-Prepkit.tier=FREE|PRO` binds a fixed tier and never touches billing.
  Release ignores it.
- **Crash reporting:** billing errors go to `CrashReporter.recordNonFatal` / `log` with the response code only.
  Never purchase tokens or order IDs (rev 30).
- **Project rules:**
  - `domain/` stays pure Kotlin and test-first.
  - All user text comes from resources. `HardCodedTextGuardTest` guards `ui/` and `domain/`.
  - New translatable strings need es / zh-rCN / hi via `\\DEVMONSTER\ethor\claude_share\repkit-l10n\claude_l10n_data.py`
    (back it up first), then regenerate with `claude_l10n_gen.py`. Lint MissingTranslation is an error.
- **Builds:** build and test through rgradle only (`remote-gradle` skill), never local `gradlew`.
  The full gate is `assembleDebug testDebugUnitTest lintDebug`.
- **Commits and PRs:** commit as `mitenko <mitenko@gmail.com>`, with no Claude attribution lines. One squashed
  commit per PR.
- **Never commit:** `google-services.json`, `docs/feedback.md`, `docs/language_feedback.md` or `docs/blurb.md`.

## Review Focus

1. **A refund while the app is open:** the next foreground query returns `Ok([])`. The tier flips to FREE, a
   stored Blossom draws as Dark, the cache reads FREE, and the stored theme choice survives (Task 1, Task 3).
2. **Offline cold start for a buyer:** the cache says PRO and the query fails. The tier stays PRO and never
   flashes FREE (Task 3).
3. **Pending, then completed:** pending shows "Purchase pending" and stays FREE. The later
   `onPurchasesUpdated(PURCHASED)` acknowledges once and flips to PRO (Task 3).
4. **Go Pro with no Play Store / billing unavailable:** a snackbar shows the `billing_unavailable` text, there's
   no crash, and the tier is unchanged. A user cancel shows nothing (Task 3, Task 5).
5. **Tapping a locked theme on FREE:** it doesn't apply the theme (the setter isn't called), and the Pro dialog
   opens. Go Pro starts the purchase (Task 5).

---

## File map

| File | Role |
| --- | --- |
| `domain/ProStatus.kt` (new) | `PurchaseState`, `OwnedPurchase`, `PurchaseQuery`, `tierAfter`, `isPending`, `PRO_PRODUCT_ID` |
| `ui/theme/ThemeMode.kt` | `isProTheme`, `canUseTheme`, `effectiveTheme(mode, systemDark, tier)` |
| `data/AppPreferences.kt` | `proCached: Flow<Tier>`, `setProCached(Tier)` (key `pro_cached`) |
| `platform/billing/BillingGateway.kt` (new) | the gateway interface, `PurchaseUpdate`, `LaunchResult` |
| `platform/billing/PlayBillingGateway.kt` (new) | the Play Billing 8 implementation |
| `platform/CurrentActivity.kt` (new) | the foreground Activity holder, registered in `HiitApp` |
| `monetisation/BillingEntitlements.kt` (new) | `Entitlements` + `ProUpgrade` + `ProStore` (pending, price, restore, events) |
| `monetisation/Unlocked.kt` | adds `FixedEntitlements(tier)` for the debug override |
| `di/MonetisationModule.kt` | binds the billing or the fixed implementation |
| `ui/entries/EntryListViewModel.kt` / `EntryListScreen.kt` | the Pro row, Restore, theme locks, Pro dialog, snackbar |
| `ui/common/ProDialog.kt` (new) | the "REPKIT Pro" dialog |
| `MainActivity.kt` | passes the tier into `effectiveTheme` |
| `app/build.gradle.kts`, `gradle/libs.versions.toml` | the billing dependency, `FORCED_TIER` BuildConfig |
| `res/values/strings.xml` + l10n | the new strings |

All Kotlin paths are under `app/src/main/kotlin/com/mitenko/repkit/`; tests mirror them under
`app/src/test/kotlin/com/mitenko/repkit/`.

---

### Task 1: Pure tier and theme rules

**Files:**
- Create: `domain/ProStatus.kt`
- Modify: `ui/theme/ThemeMode.kt`
- Test: `domain/ProStatusTest.kt` (new), `ui/theme/ThemeModeTest.kt`

**Interfaces:**
- Produces:
  - `const val PRO_PRODUCT_ID = "repkit_pro"`;
  - `enum class PurchaseState { PURCHASED, PENDING, OTHER }`;
  - `data class OwnedPurchase(val productId: String, val state: PurchaseState, val acknowledged: Boolean, val token: String)`;
  - `sealed interface PurchaseQuery { data class Ok(val owned: List<OwnedPurchase>); data object Failed }`;
  - `fun tierAfter(query: PurchaseQuery, previous: Tier): Tier`;
  - `fun isPending(query: PurchaseQuery): Boolean`;
  - `fun isProTheme(mode: ThemeMode): Boolean`;
  - `fun canUseTheme(tier: Tier, mode: ThemeMode): Boolean`;
  - `fun effectiveTheme(mode: ThemeMode, systemDark: Boolean, tier: Tier): AppTheme`.
  - The old two-argument `effectiveTheme` is **removed**; every caller passes a tier.

- [ ] **Step 1: Write the failing tests**

`domain/ProStatusTest.kt`:

```kotlin
package com.mitenko.repkit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec rev 41 §1.3: billing results to a tier; an error never downgrades. */
class ProStatusTest {
    private fun owned(state: PurchaseState, id: String = PRO_PRODUCT_ID) = OwnedPurchase(id, state, acknowledged = true, token = "t")

    @Test fun `a purchased Pro makes PRO`() =
        assertEquals(Tier.PRO, tierAfter(PurchaseQuery.Ok(listOf(owned(PurchaseState.PURCHASED))), Tier.FREE))

    @Test fun `no purchase makes FREE, which is how a refund lands`() =
        assertEquals(Tier.FREE, tierAfter(PurchaseQuery.Ok(emptyList()), Tier.PRO))

    @Test fun `a pending purchase stays FREE`() =
        assertEquals(Tier.FREE, tierAfter(PurchaseQuery.Ok(listOf(owned(PurchaseState.PENDING))), Tier.FREE))

    @Test fun `another product doesn't count`() =
        assertEquals(Tier.FREE, tierAfter(PurchaseQuery.Ok(listOf(owned(PurchaseState.PURCHASED, "other"))), Tier.FREE))

    @Test fun `a failed query keeps the previous tier either way`() {
        assertEquals(Tier.PRO, tierAfter(PurchaseQuery.Failed, Tier.PRO))
        assertEquals(Tier.FREE, tierAfter(PurchaseQuery.Failed, Tier.FREE))
    }

    @Test fun `pending is reported only for a pending Pro`() {
        assertTrue(isPending(PurchaseQuery.Ok(listOf(owned(PurchaseState.PENDING)))))
        assertFalse(isPending(PurchaseQuery.Ok(listOf(owned(PurchaseState.PURCHASED)))))
        assertFalse(isPending(PurchaseQuery.Failed))
    }
}
```

Add to `ui/theme/ThemeModeTest.kt`. Update its existing `effectiveTheme(...)` calls to pass `Tier.PRO` as the
third argument, so they keep testing what they test today, and import `com.mitenko.repkit.domain.Tier`:

```kotlin
    @Test
    fun `only Blossom, Grove and Foundry are Pro themes`() {
        assertEquals(
            setOf(ThemeMode.BLOSSOM, ThemeMode.GROVE, ThemeMode.FOUNDRY),
            ThemeMode.entries.filter(::isProTheme).toSet(),
        )
    }

    @Test
    fun `FREE can use every theme but the Pro ones, PRO can use all`() {
        ThemeMode.entries.forEach { mode ->
            assertTrue(canUseTheme(Tier.PRO, mode))
            assertEquals(!isProTheme(mode), canUseTheme(Tier.FREE, mode))
        }
    }

    @Test
    fun `a Pro theme on FREE draws as Dark, and as itself on PRO`() {
        listOf(ThemeMode.BLOSSOM, ThemeMode.GROVE, ThemeMode.FOUNDRY).forEach { mode ->
            listOf(true, false).forEach { systemDark ->
                assertEquals(AppTheme.DARK, effectiveTheme(mode, systemDark, Tier.FREE))
            }
        }
        assertEquals(AppTheme.BLOSSOM, effectiveTheme(ThemeMode.BLOSSOM, systemDark = false, tier = Tier.PRO))
        assertEquals(AppTheme.SONAR, effectiveTheme(ThemeMode.SONAR, systemDark = false, tier = Tier.FREE))
        assertEquals(AppTheme.LIGHT, effectiveTheme(ThemeMode.SYSTEM, systemDark = false, tier = Tier.FREE))
    }
```

- [ ] **Step 2: Run the tests and confirm they fail.** Run
  `pwsh -NoProfile -File D:\Dev\scripts\rgradle.ps1 testDebugUnitTest --tests *ProStatusTest* --tests *ThemeModeTest*`.
  Expected: compilation fails (unresolved `tierAfter`, `isProTheme`, …).

- [ ] **Step 3: Implement**

`domain/ProStatus.kt`:

```kotlin
package com.mitenko.repkit.domain

/** The Play product that unlocks Pro (spec rev 41 §1.1): a one-time, non-consumable in-app product. */
const val PRO_PRODUCT_ID = "repkit_pro"

/** A purchase's state as the app needs it (spec rev 41 §1.2); Play's UNSPECIFIED and anything new map to OTHER. */
enum class PurchaseState { PURCHASED, PENDING, OTHER }

/** One purchase Play reports as owned. [token] is only for acknowledging and is never logged (rev 30). */
data class OwnedPurchase(val productId: String, val state: PurchaseState, val acknowledged: Boolean, val token: String)

/** The result of asking Play what the user owns (spec rev 41 §1.3). */
sealed interface PurchaseQuery {
    data class Ok(val owned: List<OwnedPurchase>) : PurchaseQuery
    data object Failed : PurchaseQuery
}

/**
 * Spec rev 41 §1.3: PRO when Play reports [PRO_PRODUCT_ID] purchased, FREE when an answer lacks it (a refund or
 * revocation), and [previous] when the query failed: an error never downgrades.
 */
fun tierAfter(query: PurchaseQuery, previous: Tier): Tier = when (query) {
    PurchaseQuery.Failed -> previous
    is PurchaseQuery.Ok ->
        if (query.owned.any { it.productId == PRO_PRODUCT_ID && it.state == PurchaseState.PURCHASED }) Tier.PRO else Tier.FREE
}

/** Spec rev 41 §1.2: a Pro purchase waiting on a slow payment method ("Purchase pending"). */
fun isPending(query: PurchaseQuery): Boolean =
    query is PurchaseQuery.Ok && query.owned.any { it.productId == PRO_PRODUCT_ID && it.state == PurchaseState.PENDING }
```

In `ui/theme/ThemeMode.kt`, replace `effectiveTheme` and add the gating:

```kotlin
/** Spec rev 41 §3.2: the themes that come with Pro. Light, Dark and Sonar stay free. */
fun isProTheme(mode: ThemeMode): Boolean = mode == ThemeMode.BLOSSOM || mode == ThemeMode.GROVE || mode == ThemeMode.FOUNDRY

/** Spec rev 41 §3.2: whether [tier] may draw [mode]. */
fun canUseTheme(tier: Tier, mode: ThemeMode): Boolean = tier == Tier.PRO || !isProTheme(mode)

/**
 * Pure: the theme drawn for the user's [mode], given the phone's [systemDark] setting and the [tier]
 * (spec rev 41 §3.2: a Pro theme on FREE draws as Dark; the stored choice is kept).
 */
fun effectiveTheme(mode: ThemeMode, systemDark: Boolean, tier: Tier): AppTheme {
    if (!canUseTheme(tier, mode)) return AppTheme.DARK
    return when (mode) {
        ThemeMode.SYSTEM -> if (systemDark) AppTheme.DARK else AppTheme.LIGHT
        ThemeMode.LIGHT -> AppTheme.LIGHT
        ThemeMode.DARK -> AppTheme.DARK
        ThemeMode.SONAR -> AppTheme.SONAR
        ThemeMode.BLOSSOM -> AppTheme.BLOSSOM
        ThemeMode.GROVE -> AppTheme.GROVE
        ThemeMode.FOUNDRY -> AppTheme.FOUNDRY
    }
}

/** Pure: whether the effective theme is dark (every glowing theme is dark). Tier-independent: a locked theme draws Dark. */
fun isDark(mode: ThemeMode, systemDark: Boolean): Boolean = effectiveTheme(mode, systemDark, Tier.PRO) != AppTheme.LIGHT
```

Add `import com.mitenko.repkit.domain.Tier`. Then fix the callers:
- `MainActivity.kt`: `effectiveTheme(themeMode, isSystemInDarkTheme(), tier)`. Move the `tier` collection above
  this line.
- `EntryListScreen.kt` swatch: `paletteFor(effectiveTheme(mode, systemDark = true, tier = Tier.PRO))`. The swatch
  always shows the theme's own colours, locked or not.

- [ ] **Step 4: Run the tests and confirm they pass** (same command). Expected: PASS, and the other
  `ThemeModeTest` cases still pass.

- [ ] **Step 5: Commit:** `git commit -am "Pro: pure tier and theme rules (spec rev 41 §1.3, §3.2)"` (add the new
  files first).

---

### Task 2: The Pro cache in AppPreferences

**Files:**
- Modify: `data/AppPreferences.kt`
- Test: `data/AppPreferencesTest.kt`

**Interfaces:**
- Produces: `val proCached: Flow<Tier>` (default FREE; an unknown value reads FREE) and `suspend fun setProCached(tier: Tier)`. The key is `stringPreferencesKey("pro_cached")`.

- [ ] **Step 1: Write the failing tests.** Add to `AppPreferencesTest`, beside the theme tests, using the same
  `store()` helper:

```kotlin
    @Test
    fun `proCached defaults to FREE and round trips`() = runTest {
        val prefs = AppPreferences(store())
        assertEquals(Tier.FREE, prefs.proCached.first())
        prefs.setProCached(Tier.PRO)
        assertEquals(Tier.PRO, prefs.proCached.first())
        prefs.setProCached(Tier.FREE)
        assertEquals(Tier.FREE, prefs.proCached.first())
    }

    @Test
    fun `an unknown stored pro_cached reads as FREE`() = runTest {
        val store = store()
        store.edit { it[stringPreferencesKey("pro_cached")] = "GARBAGE" }
        assertEquals(Tier.FREE, AppPreferences(store).proCached.first())
    }
```

- [ ] **Step 2: Run them and confirm they fail.** Run `rgradle.ps1 testDebugUnitTest --tests *AppPreferencesTest*`.
  Expected: unresolved `proCached`.

- [ ] **Step 3: Implement.** Follow `crashReportsEnabled` exactly: the same `catch` block for `IOException`.

```kotlin
    /** Spec rev 41 §1.4: the last tier Play confirmed, so a buyer opens straight into Pro, offline too. FREE when absent. */
    val proCached: Flow<Tier> = store.data
        .catch { e ->
            if (e is IOException) {
                Log.e(TAG, "App preferences read failed", e)
                emit(emptyPreferences())
            } else {
                throw e
            }
        }
        .map { prefs -> prefs[PRO_CACHED]?.let { runCatching { Tier.valueOf(it) }.getOrNull() } ?: Tier.FREE }

    suspend fun setProCached(tier: Tier) {
        store.edit { it[PRO_CACHED] = tier.name }
    }
```

  In the companion, add `val PRO_CACHED = stringPreferencesKey("pro_cached")`, and import
  `com.mitenko.repkit.domain.Tier`.

- [ ] **Step 4: Run the tests and confirm they pass.**
- [ ] **Step 5: Commit:** `"Pro: cache the confirmed tier in DataStore (spec rev 41 §1.4)"`.

---

### Task 3: The billing gateway and BillingEntitlements

**Files:**
- Create: `platform/billing/BillingGateway.kt`, `monetisation/BillingEntitlements.kt`
- Test: `monetisation/BillingEntitlementsTest.kt` (new), with a `FakeBillingGateway` in the same file

**Interfaces:**
- Consumes: Task 1 (`PurchaseQuery`, `OwnedPurchase`, `tierAfter`, `isPending`, `PRO_PRODUCT_ID`) and Task 2
  (`proCached`, `setProCached`).
- Produces:
  - `interface BillingGateway`:
    - `suspend fun queryOwned(): PurchaseQuery`;
    - `suspend fun acknowledge(token: String): Boolean`;
    - `suspend fun proPrice(): String?`;
    - `suspend fun launchPurchase(activity: Activity): LaunchResult`;
    - `val updates: Flow<PurchaseUpdate>`.
  - `sealed interface PurchaseUpdate { data class Purchases(val owned: List<OwnedPurchase>); data object Cancelled; data class Error(val code: Int) }`.
  - `sealed interface LaunchResult { data object Started; data class Failed(val code: Int) }`.
  - `interface ProStore`:
    - `val pending: StateFlow<Boolean>`;
    - `val price: StateFlow<String?>`;
    - `val events: Flow<ProEvent>`;
    - `val cacheLoaded: StateFlow<Boolean>` (true once the cached tier has been read; PR 2's ads wait on it);
    - `fun refresh()`;
    - `fun restore()`.
  - `enum class ProEvent { BILLING_UNAVAILABLE, RESTORED, NOTHING_TO_RESTORE }`.
  - `class BillingEntitlements : Entitlements, ProUpgrade, ProStore` (`@Singleton`).

- [ ] **Step 1: Write the gateway interface** (no test of its own; the fake implements it).

```kotlin
package com.mitenko.repkit.platform.billing

import android.app.Activity
import com.mitenko.repkit.domain.OwnedPurchase
import com.mitenko.repkit.domain.PurchaseQuery
import kotlinx.coroutines.flow.Flow

/** A thin, testable face on Play Billing (spec rev 41 §1.2). [PlayBillingGateway] is the real one. */
interface BillingGateway {
    /** What the user owns now; [PurchaseQuery.Failed] when Play couldn't answer. */
    suspend fun queryOwned(): PurchaseQuery

    /** Acknowledges a purchase so Play doesn't refund it after 3 days; true on success. */
    suspend fun acknowledge(token: String): Boolean

    /** Play's formatted price for Pro ("$3.99"), or null when it isn't available. */
    suspend fun proPrice(): String?

    /** Opens Play's purchase sheet for Pro on [activity]. */
    suspend fun launchPurchase(activity: Activity): LaunchResult

    /** Purchase results Play pushes after [launchPurchase] (and pending purchases completing later). */
    val updates: Flow<PurchaseUpdate>
}

sealed interface PurchaseUpdate {
    data class Purchases(val owned: List<OwnedPurchase>) : PurchaseUpdate
    data object Cancelled : PurchaseUpdate
    /** Play's BillingResponseCode; only the code is ever logged. */
    data class Error(val code: Int) : PurchaseUpdate
}

sealed interface LaunchResult {
    data object Started : LaunchResult
    data class Failed(val code: Int) : LaunchResult
}
```

- [ ] **Step 2: Write the failing tests.** These are plain JUnit tests with `runTest` and a
  `TestScope`-backed `backgroundScope`, using an in-memory DataStore like `AppPreferencesTest.store()` (copy that
  helper into this test file).

```kotlin
package com.mitenko.repkit.monetisation

import android.app.Activity
import com.mitenko.repkit.data.AppPreferences
import com.mitenko.repkit.domain.CrashReporter
import com.mitenko.repkit.domain.OwnedPurchase
import com.mitenko.repkit.domain.PRO_PRODUCT_ID
import com.mitenko.repkit.domain.PurchaseQuery
import com.mitenko.repkit.domain.PurchaseState
import com.mitenko.repkit.domain.Tier
import com.mitenko.repkit.platform.billing.BillingGateway
import com.mitenko.repkit.platform.billing.LaunchResult
import com.mitenko.repkit.platform.billing.PurchaseUpdate
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
// plus the DataStore / Robolectric imports AppPreferencesTest uses for store()

class FakeBillingGateway : BillingGateway {
    var query: PurchaseQuery = PurchaseQuery.Ok(emptyList())
    var price: String? = "$3.99"
    var launch: LaunchResult = LaunchResult.Started
    val acknowledged = mutableListOf<String>()
    var queries = 0
    override val updates = MutableSharedFlow<PurchaseUpdate>(extraBufferCapacity = 8)
    override suspend fun queryOwned(): PurchaseQuery { queries++; return query }
    override suspend fun acknowledge(token: String): Boolean { acknowledged += token; return true }
    override suspend fun proPrice(): String? = price
    override suspend fun launchPurchase(activity: Activity): LaunchResult = launch
}

private fun pro(state: PurchaseState, acked: Boolean = true, token: String = "tok") =
    OwnedPurchase(PRO_PRODUCT_ID, state, acked, token)
```

Test cases (one `@Test` each). `subject(scope)` builds
`BillingEntitlements(gateway, prefs, crash, currentActivity = { null }, scope)`; after it, call `advanceUntilIdle()`:

1. **`a cached PRO is PRO before the first query answers`**: `prefs.setProCached(PRO)`, set `gateway.query = PurchaseQuery.Failed`, build the subject. `tier.value == PRO`, and the cache still
   reads PRO.
2. **`an Ok purchase makes PRO and writes the cache`**: query `Ok([pro(PURCHASED)])`, so the tier and
   `prefs.proCached.first()` are both PRO.
3. **`a refund (Ok without Pro) makes FREE and writes the cache at once`**: cached PRO, query `Ok([])`, so the
   tier and the cache are both FREE.
4. **`a failed query changes neither the tier nor the cache`**: cached PRO, query `Failed`, so both stay PRO.
   Repeat with cached FREE.
5. **`an unacknowledged purchase is acknowledged once`**: query `Ok([pro(PURCHASED, acked = false, token = "a")])`.
   After `refresh()` twice with the same answer, `gateway.acknowledged == listOf("a")` (keep a
   `Set<String>` of tokens already acknowledged this session).
6. **`a pending purchase isn't acknowledged and sets pending`**: query `Ok([pro(PENDING, acked = false)])`, so
   `acknowledged` is empty, `pending.value == true` and the tier is FREE.
7. **`a pending purchase completing via updates flips to PRO`**: start pending, then
   `gateway.updates.emit(Purchases([pro(PURCHASED, acked = false, token = "p")]))`. The tier is PRO,
   `acknowledged == ["p"]` and `pending.value == false`.
8. **`a cancel emits nothing`**: collect `events` into a list, `emit(Cancelled)`, so the list is empty.
9. **`a purchase error emits BILLING_UNAVAILABLE and records the code only`**: emit `Error(3)`. The events are
   `[BILLING_UNAVAILABLE]`, and the fake `CrashReporter` logged a message that contains `"3"` and no token.
10. **`Go Pro with no activity or a failed launch emits BILLING_UNAVAILABLE`**: `currentActivity = { null }`,
    `start()` gives `[BILLING_UNAVAILABLE]`. Then, with an activity provider returning a Robolectric
    `Activity` (or a `mockk`-free stub: `Robolectric.buildActivity(Activity::class.java).get()`, so this test
    class uses `@RunWith(AndroidJUnit4::class)` + `@Config(sdk = [34])`), set `gateway.launch = Failed(2)`, so
    another BILLING_UNAVAILABLE.
11. **`restore reports RESTORED or NOTHING_TO_RESTORE`**: query `Ok([pro(PURCHASED)])`, `restore()` gives
    `RESTORED`; query `Ok([])` gives `NOTHING_TO_RESTORE`; query `Failed` gives `BILLING_UNAVAILABLE`.
12. **`cacheLoaded turns true after the cache is read`**: `cacheLoaded.value` is true after init, and the tier
    equals the cached value at that point.
13. **`the price comes from the gateway`**: `price.value == "$3.99"` after init, and null when the gateway returns
    null.

The fake `CrashReporter`: `class RecordingCrash : CrashReporter { val logs = mutableListOf<String>(); override fun setEnabled(enabled: Boolean) = Unit; override fun recordNonFatal(t: Throwable, context: String) { logs += context + ":" + t.message }; override fun log(message: String) { logs += message } }`.

- [ ] **Step 3: Run the tests and confirm they fail** (`--tests *BillingEntitlementsTest*`): unresolved `BillingEntitlements`.

- [ ] **Step 4: Implement `monetisation/BillingEntitlements.kt`**

```kotlin
package com.mitenko.repkit.monetisation

import android.app.Activity
import com.mitenko.repkit.data.AppPreferences
import com.mitenko.repkit.di.ApplicationScope
import com.mitenko.repkit.domain.CrashReporter
import com.mitenko.repkit.domain.Entitlements
import com.mitenko.repkit.domain.OwnedPurchase
import com.mitenko.repkit.domain.PRO_PRODUCT_ID
import com.mitenko.repkit.domain.ProUpgrade
import com.mitenko.repkit.domain.PurchaseQuery
import com.mitenko.repkit.domain.PurchaseState
import com.mitenko.repkit.domain.Tier
import com.mitenko.repkit.domain.isPending
import com.mitenko.repkit.domain.tierAfter
import com.mitenko.repkit.platform.billing.BillingGateway
import com.mitenko.repkit.platform.billing.LaunchResult
import com.mitenko.repkit.platform.billing.PurchaseUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** What the Settings row and snackbars need beyond the tier (spec rev 41 §1.2, §1.5, §3.3). */
interface ProStore {
    /** True once the cached tier has been read (spec rev 41 §1.4); before that a Pro user still reads FREE. */
    val cacheLoaded: StateFlow<Boolean>
    val pending: StateFlow<Boolean>
    val price: StateFlow<String?>
    val events: Flow<ProEvent>
    /** Re-asks Play what the user owns (app start and every return to the foreground). */
    fun refresh()
    /** Settings → Restore purchase: like [refresh], then reports the outcome. */
    fun restore()
}

enum class ProEvent { BILLING_UNAVAILABLE, RESTORED, NOTHING_TO_RESTORE }

/**
 * Spec rev 41 §1.4: the tier starts from the DataStore cache and follows Play. Every Ok answer writes the cache at
 * once (a refund included); a failed answer never touches it.
 */
@Singleton
class BillingEntitlements @Inject constructor(
    private val gateway: BillingGateway,
    private val prefs: AppPreferences,
    private val crash: CrashReporter,
    private val currentActivity: CurrentActivityProvider,
    @ApplicationScope private val scope: CoroutineScope,
) : Entitlements, ProUpgrade, ProStore {
    private val _tier = MutableStateFlow(Tier.FREE)
    override val tier: StateFlow<Tier> = _tier.asStateFlow()
    private val _cacheLoaded = MutableStateFlow(false)
    override val cacheLoaded: StateFlow<Boolean> = _cacheLoaded.asStateFlow()
    private val _pending = MutableStateFlow(false)
    override val pending: StateFlow<Boolean> = _pending.asStateFlow()
    private val _price = MutableStateFlow<String?>(null)
    override val price: StateFlow<String?> = _price.asStateFlow()
    private val _events = MutableSharedFlow<ProEvent>(extraBufferCapacity = 4)
    override val events: Flow<ProEvent> = _events.asSharedFlow()

    private val lock = Mutex()
    private val acknowledgedTokens = mutableSetOf<String>()

    init {
        scope.launch {
            _tier.value = prefs.proCached.first()
            _cacheLoaded.value = true
            apply(gateway.queryOwned())
            _price.value = gateway.proPrice()
        }
        scope.launch {
            gateway.updates.collect { update ->
                when (update) {
                    is PurchaseUpdate.Purchases -> apply(PurchaseQuery.Ok(update.owned))
                    PurchaseUpdate.Cancelled -> Unit
                    is PurchaseUpdate.Error -> fail("purchase update", update.code)
                }
            }
        }
    }

    override fun refresh() {
        scope.launch { apply(gateway.queryOwned()) }
    }

    override fun restore() {
        scope.launch {
            when (val q = gateway.queryOwned()) {
                PurchaseQuery.Failed -> fail("restore", null)
                is PurchaseQuery.Ok -> {
                    apply(q)
                    _events.emit(if (_tier.value == Tier.PRO) ProEvent.RESTORED else ProEvent.NOTHING_TO_RESTORE)
                }
            }
        }
    }

    override fun start() {
        scope.launch {
            val activity = currentActivity.get() ?: return@launch fail("launch: no activity", null)
            val result = gateway.launchPurchase(activity)
            if (result is LaunchResult.Failed) fail("launch", result.code)
        }
    }

    /** One answer from Play: acknowledge, then the tier, pending and the cache (spec rev 41 §1.2–§1.4). */
    private suspend fun apply(query: PurchaseQuery) = lock.withLock {
        if (query is PurchaseQuery.Failed) {
            crash.log("billing: query failed")
            return@withLock
        }
        query as PurchaseQuery.Ok
        query.owned
            .filter { it.productId == PRO_PRODUCT_ID && it.state == PurchaseState.PURCHASED && !it.acknowledged }
            .filter { acknowledgedTokens.add(it.token) }
            .forEach { if (!gateway.acknowledge(it.token)) crash.log("billing: acknowledge failed") }
        _pending.value = isPending(query)
        val tier = tierAfter(query, _tier.value)
        _tier.value = tier
        prefs.setProCached(tier)
    }

    private suspend fun fail(where: String, code: Int?) {
        crash.log("billing: $where failed" + (code?.let { " (code $it)" } ?: ""))
        _events.emit(ProEvent.BILLING_UNAVAILABLE)
    }
}

/** The foreground Activity, for Play's purchase sheet (spec rev 41 §1.5); null when none is resumed. */
fun interface CurrentActivityProvider {
    fun get(): Activity?
}
```

  In the tests, pass the provider as a lambda, `CurrentActivityProvider { null }`. Also pass `backgroundScope` as
  the scope, so the `updates` collector is cancelled at the end of each test.

- [ ] **Step 5: Run the tests and confirm all 13 pass.**
- [ ] **Step 6: Commit:** `"Pro: BillingEntitlements over a BillingGateway (spec rev 41 §1.2–§1.5)"`.

---

### Task 4: The Play Billing gateway, the current activity, DI and the debug override

**Files:**
- Create: `platform/billing/PlayBillingGateway.kt`, `platform/CurrentActivity.kt`
- Modify: `HiitApp.kt`, `di/MonetisationModule.kt`, `monetisation/Unlocked.kt`, `app/build.gradle.kts`,
  `gradle/libs.versions.toml`, `MainActivity.kt`
- Test: `di/MonetisationModuleTest.kt` (new, pure: the override parsing)

**Interfaces:**
- Consumes: Task 3 (`BillingGateway`, `BillingEntitlements`, `ProStore`, `CurrentActivityProvider`).
- Produces:
  - `class PlayBillingGateway @Inject constructor(@ApplicationContext context: Context) : BillingGateway`;
  - `@Singleton class CurrentActivity : CurrentActivityProvider, Application.ActivityLifecycleCallbacks`;
  - `class FixedEntitlements(tier: Tier) : Entitlements, ProUpgrade, ProStore`;
  - `fun forcedTier(debug: Boolean, value: String): Tier?` (in `MonetisationModule.kt`);
  - `ProStore` is injectable.

- [ ] **Step 1: Dependency and BuildConfig.** In `libs.versions.toml` add `billing = "8.0.0"` under `[versions]` and
  `billing-ktx = { module = "com.android.billingclient:billing-ktx", version.ref = "billing" }` under
  `[libraries]`. Use the newest **8.x** release available at implementation time, and record the version used in
  the commit message. In `app/build.gradle.kts`:
  - add `implementation(libs.billing.ktx)`;
  - in `defaultConfig`, beside `CRASHLYTICS_IN_DEBUG`, add:

```kotlin
        // Spec rev 41 §1.4: -Prepkit.tier=FREE|PRO fixes the tier in debug builds (no billing); release ignores it.
        buildConfigField("String", "FORCED_TIER", "\"${project.findProperty("repkit.tier") ?: ""}\"")
```

- [ ] **Step 2: Write a failing test for `forcedTier`.**

```kotlin
package com.mitenko.repkit.di

import com.mitenko.repkit.domain.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MonetisationModuleTest {
    @Test fun `debug builds honour FREE and PRO`() {
        assertEquals(Tier.FREE, forcedTier(debug = true, value = "FREE"))
        assertEquals(Tier.PRO, forcedTier(debug = true, value = "pro"))
    }
    @Test fun `release ignores it and blank or junk means billing`() {
        assertNull(forcedTier(debug = false, value = "PRO"))
        assertNull(forcedTier(debug = true, value = ""))
        assertNull(forcedTier(debug = true, value = "gold"))
    }
}
```

- [ ] **Step 3: Implement `PlayBillingGateway`** against Billing 8 (`billing-ktx` suspend extensions). Confirm each
  call compiles against the version added in Step 1. If an API differs, keep the behaviour and adapt the call.

```kotlin
package com.mitenko.repkit.platform.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.mitenko.repkit.domain.OwnedPurchase
import com.mitenko.repkit.domain.PRO_PRODUCT_ID
import com.mitenko.repkit.domain.PurchaseQuery
import com.mitenko.repkit.domain.PurchaseState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** Play Billing 8 behind [BillingGateway] (spec rev 41 §1.2). Connects lazily and reconnects on demand. */
@Singleton
class PlayBillingGateway @Inject constructor(@ApplicationContext context: Context) : BillingGateway {
    private val _updates = MutableSharedFlow<PurchaseUpdate>(extraBufferCapacity = 8)
    override val updates: SharedFlow<PurchaseUpdate> = _updates

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener { result, purchases ->
            val update = when (result.responseCode) {
                BillingResponseCode.OK -> PurchaseUpdate.Purchases(purchases.orEmpty().flatMap(::toOwned))
                BillingResponseCode.USER_CANCELED -> PurchaseUpdate.Cancelled
                else -> PurchaseUpdate.Error(result.responseCode)
            }
            _updates.tryEmit(update)
        }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    private val connectLock = Mutex()

    /** True once connected; a failure or a 10 s wait returns false (the caller reports it as Failed). */
    private suspend fun ready(): Boolean = connectLock.withLock {
        if (client.isReady) return@withLock true
        val done = CompletableDeferred<Boolean>()
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) { done.complete(result.responseCode == BillingResponseCode.OK) }
            override fun onBillingServiceDisconnected() { done.complete(false) }
        })
        withTimeoutOrNull(10_000) { done.await() } ?: false
    }

    override suspend fun queryOwned(): PurchaseQuery {
        if (!ready()) return PurchaseQuery.Failed
        val result = client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(ProductType.INAPP).build())
        if (result.billingResult.responseCode != BillingResponseCode.OK) return PurchaseQuery.Failed
        return PurchaseQuery.Ok(result.purchasesList.flatMap(::toOwned))
    }

    override suspend fun acknowledge(token: String): Boolean {
        if (!ready()) return false
        val result = client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build())
        return result.responseCode == BillingResponseCode.OK
    }

    private suspend fun proDetails(): ProductDetails? {
        if (!ready()) return null
        val params = QueryProductDetailsParams.newBuilder().setProductList(
            listOf(QueryProductDetailsParams.Product.newBuilder().setProductId(PRO_PRODUCT_ID).setProductType(ProductType.INAPP).build()),
        ).build()
        val result = client.queryProductDetails(params)
        if (result.billingResult.responseCode != BillingResponseCode.OK) return null
        return result.productDetailsList?.firstOrNull()
    }

    override suspend fun proPrice(): String? = proDetails()?.oneTimePurchaseOfferDetails?.formattedPrice

    override suspend fun launchPurchase(activity: Activity): LaunchResult {
        val details = proDetails() ?: return LaunchResult.Failed(BillingResponseCode.ITEM_UNAVAILABLE)
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(
            listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).build()),
        ).build()
        val result = client.launchBillingFlow(activity, params)
        return if (result.responseCode == BillingResponseCode.OK) LaunchResult.Started else LaunchResult.Failed(result.responseCode)
    }

    private fun toOwned(p: Purchase): List<OwnedPurchase> = p.products.map { id ->
        OwnedPurchase(
            productId = id,
            state = when (p.purchaseState) {
                Purchase.PurchaseState.PURCHASED -> PurchaseState.PURCHASED
                Purchase.PurchaseState.PENDING -> PurchaseState.PENDING
                else -> PurchaseState.OTHER
            },
            acknowledged = p.isAcknowledged,
            token = p.purchaseToken,
        )
    }
}
```

- [ ] **Step 4: `platform/CurrentActivity.kt`**

```kotlin
package com.mitenko.repkit.platform

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.mitenko.repkit.monetisation.CurrentActivityProvider
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

/** Tracks the resumed Activity for Play's purchase sheet (spec rev 41 §1.5). Weak, so it never leaks one. */
@Singleton
class CurrentActivity @Inject constructor() : CurrentActivityProvider, Application.ActivityLifecycleCallbacks {
    private var resumed: WeakReference<Activity>? = null
    override fun get(): Activity? = resumed?.get()
    override fun onActivityResumed(activity: Activity) { resumed = WeakReference(activity) }
    override fun onActivityPaused(activity: Activity) { if (resumed?.get() === activity) resumed = null }
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
```

  In `HiitApp`, add `@Inject lateinit var currentActivity: CurrentActivity`, and in `onCreate` after
  `super.onCreate()`, `registerActivityLifecycleCallbacks(currentActivity)`.

- [ ] **Step 5: `FixedEntitlements`** in `monetisation/Unlocked.kt`. It replaces nothing; `UnlockedEntitlements`
  and `NoOpProUpgrade` stay for tests that use them.

```kotlin
/** Spec rev 41 §1.4: the debug override (-Prepkit.tier): a fixed tier, no billing, no price, no events. */
class FixedEntitlements(tier: Tier) : Entitlements, ProUpgrade, ProStore {
    override val tier: StateFlow<Tier> = MutableStateFlow(tier).asStateFlow()
    override val cacheLoaded: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()
    override val pending: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()
    override val price: StateFlow<String?> = MutableStateFlow<String?>(null).asStateFlow()
    override val events: Flow<ProEvent> = emptyFlow()
    override fun start() = Unit
    override fun refresh() = Unit
    override fun restore() = Unit
}
```

- [ ] **Step 6: `MonetisationModule`.** Replace the module with an `object`. Hilt can't inject a nullable binding, so the
  debug override goes through a non-null holder. `dagger.Lazy` means an override build never constructs
  `BillingEntitlements` and never connects to Play.

```kotlin
/** Spec rev 41 §1.4: the debug tier override; null means use billing. Release builds always use billing. */
fun forcedTier(debug: Boolean, value: String): Tier? =
    if (!debug) null else runCatching { Tier.valueOf(value.trim().uppercase()) }.getOrNull()

/** The override, if any, as one shared [FixedEntitlements]. */
class ForcedTier(tier: Tier?) {
    val fixed: FixedEntitlements? = tier?.let(::FixedEntitlements)
}

@Module
@InstallIn(SingletonComponent::class)
object MonetisationModule {
    @Provides @Singleton
    fun forced(): ForcedTier = ForcedTier(forcedTier(BuildConfig.DEBUG, BuildConfig.FORCED_TIER))

    @Provides @Singleton
    fun billingGateway(impl: PlayBillingGateway): BillingGateway = impl

    @Provides @Singleton
    fun currentActivityProvider(impl: CurrentActivity): CurrentActivityProvider = impl

    @Provides @Singleton
    fun entitlements(forced: ForcedTier, billing: dagger.Lazy<BillingEntitlements>): Entitlements = forced.fixed ?: billing.get()

    @Provides @Singleton
    fun proUpgrade(forced: ForcedTier, billing: dagger.Lazy<BillingEntitlements>): ProUpgrade = forced.fixed ?: billing.get()

    @Provides @Singleton
    fun proStore(forced: ForcedTier, billing: dagger.Lazy<BillingEntitlements>): ProStore = forced.fixed ?: billing.get()

    @Provides
    fun freeLimits(): FreeLimits = FreeLimits()

    /** Draws nothing (spec revision 18 §4); PR 2 binds the AdMob renderer here. */
    @Provides
    fun adRenderer(): AdRenderer = NoAdRenderer
}
```

- [ ] **Step 7: Refresh on foreground.** In `MainActivity`, inject `ProStore`. In `onResume()` (override it), call
  `proStore.refresh()`. The first refresh at start comes from `BillingEntitlements.init`.

- [ ] **Step 8: Run the full gate.** Run `rgradle.ps1 assembleDebug testDebugUnitTest lintDebug`. Expected: PASS.
  Existing tests that relied on PRO by default still pass, because the Compose tests provide
  `LocalTier` themselves (default PRO) and ViewModel tests use fakes.

- [ ] **Step 9: Commit:** `"Pro: Play Billing 8 gateway, current activity, DI and -Prepkit.tier override (spec rev 41 §1)"`.

---

### Task 5: Settings Pro row, theme locks, Pro dialog and snackbars

**Files:**
- Create: `ui/common/ProDialog.kt`
- Modify: `ui/entries/EntryListViewModel.kt`, `ui/entries/EntryListScreen.kt`, `res/values/strings.xml`
- Test: `ui/entries/EntryListScreenTest.kt`, `ui/entries/EntryListViewModelTest.kt`

**Interfaces:**
- Consumes: `ProStore`, `ProEvent`, `Entitlements`, `ProUpgrade`, `isProTheme`, `canUseTheme`.
- Produces:
  - new `EntryListScreen` parameters, each with a default so existing callers and tests compile:
    - `tier: Tier = Tier.PRO`, `proPending: Boolean = false`, `proPrice: String? = null`;
    - `onRestore: () -> Unit = {}`;
    - `proDialog: Boolean = false`, `onDismissPro: () -> Unit = {}`, `onLockedTheme: () -> Unit = {}`;
    - `message: ProEvent? = null`, `onMessageShown: () -> Unit = {}`.
  - `EntryListViewModel`:
    - `val tier: StateFlow<Tier>`, `val proPending`, `val proPrice`;
    - `val proDialog: StateFlow<Boolean>`, `fun lockedTheme()`, `fun dismissPro()`;
    - `fun restore()`, `val message: StateFlow<ProEvent?>`, `fun messageShown()`;
    - `goPro()` now also closes the Pro dialog.

- [ ] **Step 1: Strings.** Add to `values/strings.xml` after `not_now`:

```xml
    <!-- Spec rev 41 §3: the Pro purchase -->
    <string name="pro_title">REPKIT Pro</string>
    <string name="go_pro_price">Go Pro · %1$s</string>
    <string name="pro_subtitle_free">Unlimited workouts, no ads, every theme</string>
    <string name="pro_active">REPKIT Pro ✓</string>
    <string name="pro_subtitle_pro">Thanks for your support</string>
    <string name="purchase_pending">Purchase pending</string>
    <string name="restore_purchase">Restore purchase</string>
    <string name="restore_ok">Pro restored</string>
    <string name="restore_none">No purchase found on this Google account</string>
    <string name="billing_unavailable">Couldn\'t reach Google Play. Try again later.</string>
    <string name="pro_theme_text">Blossom, Grove and Foundry come with Pro, along with unlimited workouts and no ads.</string>
    <string name="pro_lock">Pro</string>
```

  Then add es / zh-rCN / hi to the l10n data file (back up both the data file and the CSV with a
  `.before-pro` suffix), and regenerate:

```python
# --- Pro purchase (spec rev 41, feat/pro-and-ads, 2026-10-21) ---
T.update({
 "pro_title": ("REPKIT Pro", "REPKIT Pro", "REPKIT Pro"),
 "go_pro_price": ("Hazte Pro · %1$s", "升级 Pro · %1$s", "Pro लें · %1$s"),
 "pro_subtitle_free": ("Entrenamientos ilimitados, sin anuncios, todos los temas", "无限训练、无广告、全部主题", "असीमित वर्कआउट, कोई विज्ञापन नहीं, सभी थीम"),
 "pro_active": ("REPKIT Pro ✓", "REPKIT Pro ✓", "REPKIT Pro ✓"),
 "pro_subtitle_pro": ("Gracias por tu apoyo", "感谢你的支持", "आपके सहयोग के लिए धन्यवाद"),
 "purchase_pending": ("Compra pendiente", "购买处理中", "खरीदारी लंबित है"),
 "restore_purchase": ("Restaurar compra", "恢复购买", "खरीदारी बहाल करें"),
 "restore_ok": ("Pro restaurado", "已恢复 Pro", "Pro बहाल हो गया"),
 "restore_none": ("No se encontró ninguna compra en esta cuenta de Google", "此 Google 账号没有找到购买记录", "इस Google खाते पर कोई खरीदारी नहीं मिली"),
 "billing_unavailable": ("No se pudo conectar con Google Play. Inténtalo más tarde.", "无法连接 Google Play，请稍后再试。", "Google Play से कनेक्ट नहीं हो सका। बाद में फिर कोशिश करें।"),
 "pro_theme_text": ("Blossom, Grove y Foundry vienen con Pro, junto con entrenamientos ilimitados y sin anuncios.", "Blossom、Grove 和 Foundry 随 Pro 提供，还有无限训练和无广告。", "Blossom, Grove और Foundry Pro के साथ आते हैं, साथ में असीमित वर्कआउट और कोई विज्ञापन नहीं।"),
 "pro_lock": ("Pro", "Pro", "Pro"),
})
```

  Run from the repo root:
  `python //DEVMONSTER/ethor/claude_share/repkit-l10n/claude_l10n_gen.py //DEVMONSTER/ethor/claude_share/repkit-l10n //DEVMONSTER/ethor/claude_share/repkit-l10n/repkit-translations-review.csv`.
  `git diff --stat app/src/main/res` should show only these keys added.

- [ ] **Step 2: Write the failing Compose tests** in `EntryListScreenTest`. Extend `show(...)` with the new
  parameters (same defaults), and wrap the content in
  `CompositionLocalProvider(LocalTier provides tier)` so the screen and the dialog see it:

```kotlin
    @Test fun `FREE shows Go Pro with Play's price and Restore`() {
        show(EntryListUiState.Items(rows), tier = Tier.FREE, proPrice = "$3.99")
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithTag("pro_row").assertTextContains("Go Pro · $3.99")
        compose.onNodeWithText("Unlimited workouts, no ads, every theme").assertExists()
        compose.onNodeWithTag("restore_purchase").assertExists()
    }

    @Test fun `FREE without a price still offers Go Pro`() {
        show(EntryListUiState.Items(rows), tier = Tier.FREE, proPrice = null)
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithTag("pro_row").assertTextContains("Go Pro")
    }

    @Test fun `pending shows Purchase pending and no buttons`() {
        show(EntryListUiState.Items(rows), tier = Tier.FREE, proPending = true)
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Purchase pending").assertExists()
        compose.onNodeWithTag("restore_purchase").assertDoesNotExist()
    }

    @Test fun `PRO shows the thank-you row`() {
        show(EntryListUiState.Items(rows), tier = Tier.PRO)
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("REPKIT Pro ✓").assertExists()
        compose.onNodeWithText("Thanks for your support").assertExists()
        compose.onNodeWithTag("restore_purchase").assertDoesNotExist()
    }

    @Test fun `tapping Go Pro starts the purchase and Restore restores`() {
        var goPro = 0; var restore = 0
        show(EntryListUiState.Items(rows), tier = Tier.FREE, onGoPro = { goPro++ }, onRestore = { restore++ })
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithTag("pro_row").performClick()
        assertEquals(1, goPro)
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithTag("restore_purchase").performClick()
        assertEquals(1, restore)
    }

    @Test fun `on FREE the Pro themes are locked and tapping one opens the Pro dialog, not the theme`() {
        var chosen: ThemeMode? = null; var locked = 0
        show(EntryListUiState.Items(rows), tier = Tier.FREE, onSetThemeMode = { chosen = it }, onLockedTheme = { locked++ })
        compose.onNodeWithContentDescription("Settings").performClick()
        listOf("BLOSSOM", "GROVE", "FOUNDRY").forEach { compose.onNodeWithTag("theme_lock_$it", useUnmergedTree = true).assertExists() }
        compose.onNodeWithTag("theme_lock_SONAR", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("theme_GROVE").performScrollTo().performClick()
        assertEquals(null, chosen)
        assertEquals(1, locked)
    }

    @Test fun `on PRO no theme is locked and Grove applies`() {
        var chosen: ThemeMode? = null
        show(EntryListUiState.Items(rows), tier = Tier.PRO, onSetThemeMode = { chosen = it })
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithTag("theme_lock_GROVE", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("theme_GROVE").performScrollTo().performClick()
        assertEquals(ThemeMode.GROVE, chosen)
    }

    @Test fun `the Pro dialog shows its text and Go Pro`() {
        var goPro = 0
        show(EntryListUiState.Items(rows), tier = Tier.FREE, proDialog = true, onGoPro = { goPro++ })
        compose.onNodeWithText("REPKIT Pro").assertExists()
        compose.onNodeWithText("Blossom, Grove and Foundry come with Pro, along with unlimited workouts and no ads.").assertExists()
        compose.onNodeWithText("Go Pro").performClick()
        assertEquals(1, goPro)
    }

    @Test fun `a billing message shows as a snackbar`() {
        show(EntryListUiState.Items(rows), tier = Tier.FREE, message = ProEvent.BILLING_UNAVAILABLE)
        compose.onNodeWithText("Couldn't reach Google Play. Try again later.").assertExists()
    }

    @Test fun `a stored Pro theme on FREE stays selected, with its lock`() {
        show(EntryListUiState.Items(rows), tier = Tier.FREE, themeMode = ThemeMode.BLOSSOM)
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithTag("theme_BLOSSOM").assertIsSelected()
        compose.onNodeWithTag("theme_lock_BLOSSOM", useUnmergedTree = true).assertExists()
    }
```

  `EntryListViewModel` gains a `ProStore` constructor parameter, so update every existing construction in
  `EntryListViewModelTest` to pass a `FakeProStore`. In `EntryListViewModelTest`, using its existing fakes plus a `FakeProStore` (`MutableStateFlow`s and a
  `MutableSharedFlow<ProEvent>`), add:
  - `lockedTheme` opens the Pro dialog, `dismissPro` closes it, and `goPro` starts the purchase and closes both
    the Pro and limit dialogs;
  - `restore` calls `ProStore.restore`;
  - an emitted `ProEvent` lands in `message`, and `messageShown` clears it.

- [ ] **Step 3: Run the tests and confirm they fail** (`--tests *EntryListScreenTest* --tests *EntryListViewModelTest*`).

- [ ] **Step 4: Implement**

- **`ui/common/ProDialog.kt`**: an `AlertDialog` like `EntryLimitDialog`.
  - title `pro_title`, text `pro_theme_text`;
  - confirm `go_pro` (calls `onGoPro`), dismiss `not_now`;
  - tags `pro_dialog`, `pro_go`, `pro_not_now`.

- **`EntryListViewModel`:**
  - inject `ProStore`;
  - expose `tier = entitlements.tier`, `proPending = proStore.pending`, `proPrice = proStore.price`;
  - add `_proDialog` and a `_message: MutableStateFlow<ProEvent?>`, filled by collecting `proStore.events` in
    `init` (via `viewModelScope.launch`);
  - `lockedTheme() { _proDialog.value = true }`; `dismissPro()`;
  - `restore() = proStore.restore()`;
  - `messageShown() { _message.value = null }`;
  - `goPro()` keeps `proUpgrade.start()` and closes both dialogs.

- **`EntryListScreen` (outer, VM-connected):** collect these and pass them down.
  - Add a `SnackbarHost` to the Scaffold, as `EntryScreen.kt` does.
  - When `message` is non-null, `LaunchedEffect(message)` shows the text for that `ProEvent`
    (`billing_unavailable`, `restore_ok`, `restore_none`), then calls `onMessageShown`.

- **`SettingsDialog`:** add parameters
  `tier, proPending, proPrice, onGoPro, onRestore, onLockedTheme`. At the top, before the Appearance heading, add a
  `ProRow` (tagged `pro_row`):
  - FREE, not pending: a clickable row, min height 56 dp, with title
    `go_pro_price` (or `go_pro` when the price is null) and subtitle `pro_subtitle_free`. Clicking closes the
    dialog and calls `onGoPro`. Under it, `TextButton(restore_purchase)` tagged `restore_purchase` closes the
    dialog and calls `onRestore`.
  - Pending: a plain row with the text `purchase_pending`.
  - PRO: a plain row with `pro_active` over `pro_subtitle_pro`.

  Then a `HorizontalDivider`.

- **`ThemeOptionRow`:** gets `tier` and `onLockedTheme`.
  - `val locked = !canUseTheme(tier, mode)`.
  - The row's `onClick` is `if (locked) onLockedTheme() else onSelect(mode)`.
  - After the swatch, when `locked`, show
    `Icon(painterResource(R.drawable.ic_lock), contentDescription = stringResource(R.string.pro_lock), Modifier.size(16.dp).testTag("theme_lock_${mode.name}"))`.
  - Add `res/drawable/ic_lock.xml`, the Material "lock" vector, 24 dp, tinted by the Icon.
  - Selection still uses `mode == current`, so a stored Pro theme shows selected.

- **The Pro dialog:** `EntryListScreen` shows `ProDialog` when `proDialog`, wired to `onGoPro` / `onDismissPro`.

- [ ] **Step 5: Run the tests and confirm they pass.** Then run the full gate. Expected: PASS, lint clean
  (MissingTranslation included).

- [ ] **Step 6: Commit:** `"Pro: Settings Pro row, Restore, locked themes and Pro dialog (spec rev 41 §3)"`.

---

### Task 6: Docs and the whole-branch gate

**Files:**
- Modify: `claude.md`; spec rev 41 (status line)

- [ ] **Step 1: Add a CLAUDE.md source-of-truth line** after the hold-remaining line:

```markdown
- **Pro purchase and ads (approved, rev 41, amends rev 18, rev 36 §8 and rev 39 §10):** `docs/superpowers/specs/2026-10-21-pro-and-ads-design.md` — a one-time `repkit_pro` purchase (Play Billing 8 behind `platform/billing/BillingGateway`; `monetisation/BillingEntitlements` is `Entitlements` + `ProUpgrade` + `ProStore`, cached in `pro_cached`, never downgraded by an error); FREE = 3 workouts + Light/Dark/Sonar, PRO = unlimited + Blossom/Grove/Foundry (`isProTheme`, `canUseTheme`, `effectiveTheme(mode, systemDark, tier)` draws a locked theme as Dark); ⚙ Settings has the REPKIT Pro row and Restore purchase; `-Prepkit.tier=FREE|PRO` fixes the tier in debug builds. Ads are PR 2.
```

  Update the "Monetisation (rev 18)" key-decision bullet. Its sentence "v1 binds `UnlockedEntitlements` (always PRO)"
  becomes "rev 41 binds `BillingEntitlements` (or `FixedEntitlements` with `-Prepkit.tier`)".

- [ ] **Step 2: Change the spec's status line** to `**Status:** approved by the user, 2026-10-21 ("go"); PR 1 (Pro) implemented.`

- [ ] **Step 3: Run the full gate:** `rgradle.ps1 assembleDebug testDebugUnitTest lintDebug`. Expected: all tests
  pass, lint clean.

- [ ] **Step 4: Check by hand.**
  - Build with `-Prepkit.tier=FREE`:
    `pwsh -NoProfile -File D:\Dev\scripts\rgradle.ps1 assembleDebug -Prepkit.tier=FREE`.
  - Install it per the project install rules (back up first, `adb install -r`, Pixel only).
  - Confirm:
    - the 4th-workout limit dialog appears;
    - the Pro themes are locked;
    - a stored Blossom draws Dark;
    - the Settings row reads "Go Pro" (no price without Play).
  - The real purchase flow needs Play Console's `repkit_pro` product and a license tester (spec §6). Note it as
    pending if the product doesn't exist yet.

- [ ] **Step 5: Commit:** `"Pro: docs (spec rev 41, CLAUDE.md)"`. At PR time, squash per the protocol.
