# REPKIT — Pro purchase and ads (spec revision 41)

**Date:** 2026-10-21
**Status:** approved by the user, 2026-10-21 ("go"); PR 1 (Pro) implemented.
**Builds on:** revision 18 (monetisation seams). Amends revision 36 §8 and revision 39 §10 (which themes are Pro).

## 0. What the user decided (2026-10-21)

- **Approach A:** Google Play Billing for the purchase and AdMob for the ads, with no server of our own.
- **Pro is a one-time unlock.** There is no subscription.
- **Free:** at most **3 workouts** ("limit three exercises on free. unlimited on pro"). Banner ads.
- **Pro:** unlimited workouts, no ads, and the Blossom, Grove and Foundry themes. Light, Dark and Sonar stay free.
- **Existing installs get no special treatment.** Anyone without a purchase is Free from this version on. Testers
  buy with Play license-tester accounts, at no charge.
- The user asked for the spec straight after section 1 ("build the spec"). Sections 2–6 below are Claude's
  recommended defaults and are open to change in review.

## 1. Purchase and Pro status

### 1.1 The product

- Play Console gets one in-app product: `repkit_pro`. It is a one-time, non-consumable purchase.
- The price is set in Play Console (suggested US$3.99) and changes without an app update.
- The app shows Play's formatted price (`ProductDetails`). It never hard-codes one.

### 1.2 Billing

`platform/billing/PlayBilling` wraps the Play Billing Library (`com.android.billingclient:billing-ktx`, current
major version).

- **It connects** when the app starts and reconnects after a disconnect.
- **It queries owned purchases** (`queryPurchasesAsync(INAPP)`) at start, on every return to the foreground, and
  after each purchase update.
- **Acknowledging:** a `PURCHASED` purchase of `repkit_pro` that isn't acknowledged yet is acknowledged at once,
  right after the tier is granted (so a buyer never waits on it). Play refunds purchases left unacknowledged for
  3 days.
- **A `PENDING` purchase** (cash or slow payment methods) leaves the user Free. The Settings row says "Purchase
  pending" until it completes or is cancelled.
- **Errors** (no Play Store, offline, billing unavailable, the user cancelling) never change the tier.
  - The user cancelling is silent.
  - `ITEM_ALREADY_OWNED` (from the launch or a purchase update) isn't a failure: it re-queries Play and applies
    the answer like a restore, showing "Pro restored" when the user is now Pro (else the failure snackbar).
  - Any other failure of Go Pro shows a snackbar: "Couldn't reach Google Play. Try again later."
  - Billing snackbars show over whichever screen is current (one app-level host), so Go Pro from Entry Settings
    › Duplicate reports there.
- **Crash reporting:** billing errors are recorded as value-free non-fatals and breadcrumbs through
  `CrashReporter` (rev 30): the response code only, never order IDs or tokens.

### 1.3 The tier (pure, test-first)

`domain/ProStatus.kt` maps a billing result to a tier:

```kotlin
enum class PurchaseState { PURCHASED, PENDING, OTHER }
data class OwnedPurchase(val productId: String, val state: PurchaseState)
sealed interface PurchaseQuery {
    data class Ok(val owned: List<OwnedPurchase>) : PurchaseQuery
    data object Failed : PurchaseQuery
}
fun tierAfter(query: PurchaseQuery, previous: Tier): Tier
fun isPending(query: PurchaseQuery): Boolean
```

- `Ok` with `repkit_pro` in state `PURCHASED` → PRO.
- `Ok` without it → FREE. This covers a refund and a revoked purchase.
- `Failed` → `previous`. An error never downgrades the user.

### 1.4 Entitlements

`monetisation/BillingEntitlements` (`@Singleton`) replaces `UnlockedEntitlements` in `MonetisationModule`.

- `tier` starts from a cached value in DataStore (`app.preferences_pb`, key `pro_cached`, default FREE).
  - A Pro user therefore opens straight into Pro, offline too, and never sees an ad flash on start.
  - Every `Ok` query writes the cache **immediately**, in both directions: `Ok` without `repkit_pro` (a refund
    or revocation) sets `pro_cached` to FREE at once, and the tier flow, ad slots and theme resolution all switch
    on that same emission. A `Failed` query never writes the cache.
  - **The one rule for "is this user Pro?"** everywhere (themes §3.2, ads §2, the limit §3.1, the Settings row
    §3.3) is `Entitlements.tier`, which is the cache until the first `Ok` and the latest `Ok` after it. Android
    Auto Backup restores `pro_cached` with the app's other preferences, so a reinstall or new phone starts from
    the backed-up tier (FREE when there is none) until its first query answers; an `Ok` answer then corrects it
    either way. A FREE start still shows no ads before that answer (§2.2's ordering) and draws a Pro theme as
    Dark until it arrives.
- **Debug override:** a debug build made with `-Prepkit.tier=FREE|PRO` binds a fixed tier and skips billing.
  This allows Free / Pro checks on a phone without Play. Release builds ignore the property.

### 1.5 Go Pro and restore

- `ProUpgrade.start()` launches Play's purchase sheet for `repkit_pro`.
  - The sheet needs the foreground Activity. `platform/CurrentActivity` (an
    `Application.ActivityLifecycleCallbacks` holder) supplies it, so `domain/` stays pure.
- **Restore is automatic:** a reinstall or a new phone on the same Google account finds the purchase on the
  next query.
- **Settings has a manual "Restore purchase"** (§3.3). It re-runs the query and shows a snackbar:
  - "Pro restored", or
  - "No purchase found on this Google account".

## 2. Ads

### 2.1 SDK and placements

- **SDK:** AdMob (`com.google.android.gms:play-services-ads`).
- **The ad:** one anchored adaptive banner per placement, in the existing `AdSlot` (rev 18 §4): the bottom of the
  workout list and of the workout screen.
  - Never on the timer, in dialogs or in Entry Settings.
  - Never for Pro.
  - No interstitials, rewarded ads or app-open ads.
- **The renderer:** `ads/AdMobRenderer` implements `AdRenderer` and replaces `NoAdRenderer`.
  - Each placement has its own ad unit ID.
  - Until an ad loads, and when one fails, the slot stays zero-height, so nothing jumps or leaves a blank bar.
  - The `AdView` is created per screen and destroyed with it.
- **IDs:**
  - The AdMob app ID goes in the manifest through a `manifestPlaceholders` value.
  - Debug builds always use Google's published **test** app ID and test ad unit IDs.
  - Release builds use the real IDs, held as `buildConfigField`s. They aren't secrets.
- **Initialisation:** `MobileAds.initialize` runs off the main thread after consent (§2.2), and only when the tier
  is FREE. A Pro user never loads the ads SDK's network stack.

### 2.2 Consent

- **The form:** Google's UMP SDK (`user-messaging-platform`) runs at launch on FREE.
  - It shows Google's consent form where the law requires it (EEA, UK, Switzerland).
  - Ads load only when `ConsentInformation.canRequestAds()` is true.
- **Privacy options:** when UMP reports a privacy-options requirement, the Settings dialog gets an
  **"Ad privacy choices"** row that reopens the form.
- **Startup order on FREE** (nothing ad-related happens before each step completes):
  1. The tier is known: from the cache at once, and confirmed by the first query.
  2. UMP's consent information is updated (`requestConsentInfoUpdate`), and the form is shown if required.
  3. Only if `canRequestAds()`: `MobileAds.initialize`, then each visible slot requests its banner.

  Until step 3 has run, `AdSlot` composes the renderer but the renderer draws nothing (zero height), so there is
  no placeholder, no layout shift and no ads network traffic. A user who becomes PRO at any step stops the
  sequence, and the slots compose nothing.
- **Independent of crash reporting:** consent doesn't affect the "Share crash reports and usage" switch (rev 30),
  and that switch doesn't affect ads.
- **The form's wording** is configured in the AdMob console, not in the app.

### 2.3 Rules kept from revision 18

- No timer placement.
- `AdSlot` composes nothing for PRO.
- The slot sits in the Scaffold `bottomBar`, so the buttons and content are never covered.

## 3. What Pro unlocks, and the ways into Go Pro

### 3.1 Workouts

The 3-workout limit and its "Want more workouts?" dialog are unchanged from rev 18 §3. That includes
grandfathering: a free user over the limit keeps every workout but can't add one. **Go Pro** now opens the purchase
sheet.

### 3.2 Themes (amends rev 36 §8 and rev 39 §10)

- **Pure rules:** `isProTheme(mode)` is true for BLOSSOM, GROVE and FOUNDRY. `canUseTheme(tier, mode)` is
  `tier == PRO || !isProTheme(mode)`.
- **In the Appearance list on FREE:** the three Pro themes keep their swatch and add a small lock icon. Its
  content description is "Pro".
  - Tapping a locked theme doesn't apply it. It opens the **Pro dialog**: title "REPKIT Pro"; text "Blossom, Grove
    and Foundry come with Pro, along with unlimited workouts and no ads."; buttons **Go Pro** and **Not now**.
- **A stored Pro theme while FREE** (a refund, or this upgrade on a phone that already chose one) draws as **Dark**.
  The choice is kept, so it comes back the moment Pro returns.
  - The resolution is `effectiveTheme(mode, systemDark, tier)`.
  - The Appearance list shows the stored choice as selected, with its lock.

### 3.3 Settings: the REPKIT Pro row

At the top of the ⚙ Settings dialog, above Appearance:

| State | Row |
| --- | --- |
| FREE | **Go Pro · $3.99** (Play's price), with the subtitle "Unlimited workouts, no ads, every theme"; tapping it starts the purchase. Under it, a text button **Restore purchase**. |
| FREE, pending | "Purchase pending", no button |
| PRO | **REPKIT Pro ✓**, with the subtitle "Thanks for your support" |
| Price not loaded | **Go Pro** without a price, still tappable |

### 3.4 Not now

- No "Remove ads" link next to the banner.
- No upsell on launch.
- Nothing on the timer.

## 4. Strings

New translatable strings, all needing es / zh-rCN / hi through the l10n data file:

- `pro_title` ("REPKIT Pro")
- `go_pro_price` ("Go Pro · %1$s")
- `pro_subtitle_free`
- `pro_subtitle_pro`
- `pro_active`
- `purchase_pending`
- `restore_purchase`
- `restore_ok`
- `restore_none`
- `billing_unavailable`
- `pro_theme_text`
- `pro_lock` ("Pro")
- `ad_privacy`

`go_pro` and `not_now` already exist.

## 5. Testing

- **Pure (domain, test-first):**
  - `tierAfter`: purchased, pending, other, missing, a failed query keeping the previous tier, and a refund
    downgrading;
  - `isPending`;
  - `isProTheme` / `canUseTheme` across tiers;
  - `effectiveTheme` with a tier, where a Pro theme on FREE gives DARK and keeps the choice.
- **Billing adapter (fake `BillingClient` wrapper):**
  - acknowledging an unacknowledged purchase only once;
  - a pending purchase isn't acknowledged;
  - a cancel isn't reported;
  - other errors raise `billing_unavailable`;
  - the cache updates only on `Ok`.
- **Compose (Robolectric):**
  - the Settings Pro row in each state from §3.3;
  - the locked theme rows and the Pro dialog;
  - FREE with a Pro theme stored draws Dark;
  - the ad slot stays zero-height until an ad loads (a fake renderer);
  - no `ad_slot` on the timer (kept).
- **On the phone** (license tester, closed track): buy, then reinstall and restore; pending; refund (via Play
  Console) back to Free; consent form with a debug EEA geography (`ConsentDebugSettings`); test banners on the list
  and workout screen.

## 6. Setup outside the code (the user's tasks)

These are listed here so the build isn't blocked on them. Debug builds work with test IDs and license testers.

1. **Play Console:** create the in-app product `repkit_pro` and set its price. Add license testers (you and the
   12 closed testers).
2. **AdMob:** create the account, add the app, create two banner ad units (list and workout screen), configure
   the UMP consent message, and link AdMob to Play.
3. **Store compliance:**
   - Update the privacy policy: ads, the advertising ID, and AdMob as a processor.
   - Update the Data safety form: advertising ID and device identifiers, collected for ads.
   - Set "Contains ads" to yes.
4. **Real IDs:** put the real AdMob app ID and ad unit IDs into the build (§2.1) before a release build.

## 7. Delivery

Two PRs, each shippable on its own:

1. **Pro:** billing, entitlements, the Settings Pro row, theme locks, the Pro dialog and the debug tier override.
   Until it lands, nothing is Free.
2. **Ads:** AdMob, UMP consent, the renderer and Ad privacy choices.

Per dev mode, no release builds or uploads come with either PR unless the user asks.

## 8. Out of scope

- Subscriptions, trials and discounts.
- Server-side purchase verification.
- Ad formats other than banners.
- A "Remove ads" link at the banner.
- Family sharing (a Play Console setting the user can turn on later; no code needed).
- Grandfathering early installs.
- Gating any other features.

## Review notes (2026-10-21)

A review asked for two clarifications. Both are now in the spec:
1. Refunds and revocations clear `pro_cached` on the same `Ok` query, and one rule (`Entitlements.tier`) decides
   Pro everywhere (§1.4).
2. The FREE startup order: tier, then consent, then ads initialisation, with zero-height slots until then (§2.2).
