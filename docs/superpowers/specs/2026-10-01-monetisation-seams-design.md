# REPKIT — Monetisation seams (spec revision 18)

**Date:** 2026-10-01
**Status:** Approved by the user in chat (2026-10-01)
**Amends:** nothing visible. Adds seams for a later free tier, Pro upgrade and ads.

## Purpose

This is groundwork only. **v1 behaves exactly as today:** everything is unlocked, the limit dialog never shows and no ads show. No SDK is added (no AdMob, no Play Billing).

## 1. Later: free vs Pro

- **Free:** at most 3 entries, with a banner ad at the bottom of the entry list and the entry screen.
- **Pro:** unlimited entries and no ads.

## 2. Entitlements (domain, pure Kotlin)

`domain/Entitlements.kt`:

- `enum class Tier { FREE, PRO }` and `interface Entitlements { val tier: StateFlow<Tier> }`.
- `data class FreeLimits(val maxEntries: Int = 3)`.
- `canAddEntry(tier, entryCount, limits)`: always true for PRO, and `entryCount < maxEntries` for FREE.
- `showAds(tier) = tier == FREE`. Where ads go is decided in the UI (§4).
- `interface ProUpgrade { fun start() }`: starts the upgrade. Billing implements it later.

**v1 bindings** (`di/MonetisationModule`): `UnlockedEntitlements`, a `@Singleton` that is always PRO; `NoOpProUpgrade`, which does nothing; `FreeLimits()`; and `NoAdRenderer` (§4). The billing- and ads-backed bindings replace them there later.

## 3. The entry limit

- The check runs in the ViewModels, not the repository, so the data paths are unchanged.
  - **+ New entry** (the FAB, and the empty state's button): `EntryListViewModel.requestAdd()` runs before the New dialog opens. `create` checks again against the repository's count, so nothing is created at the limit.
  - **Duplicate** (Entry Settings): `EntrySettingsViewModel.duplicate` checks before it copies.
- When `canAddEntry` is false, nothing is created or copied. The ViewModel raises the **limit dialog** instead:
  - an AlertDialog titled "Want more entries?";
  - the text "The free version keeps up to 3 entries. Go Pro for unlimited entries." (the number comes from `FreeLimits`; it's a plural resource);
  - **Go Pro** calls `ProUpgrade.start()` and closes the dialog. It doesn't navigate anywhere.
  - **Not now** (or dismissing the dialog) just closes it.
- **Grandfathering:** a free user who already has more than 3 entries keeps them all. Nothing is hidden or deleted; they just can't add another until they're under the limit.

## 4. Ad slots

- `ui/ads/AdSlot(placement, modifier)`, with `enum AdPlacement { ENTRY_LIST, ENTRY_SCREEN }`.
  - There is deliberately **no timer placement**: ads never show on the timer screen.
  - The slot sits in the Scaffold's `bottomBar` of the entry list and of the entry screen.
- It composes nothing unless `showAds(tier)`, so **Pro never sees an ad**. Otherwise it holds what the `AdRenderer` draws, in a box tagged `ad_slot`.
- **Plumbing:** two CompositionLocals, provided in `MainActivity` from the injected `Entitlements` and `AdRenderer`:
  - `LocalTier`, which defaults to PRO;
  - `LocalAdRenderer`, which defaults to `NoAdRenderer`.
- **v1:** the tier is PRO, so the slot composes nothing. Even on FREE, `NoAdRenderer` draws nothing, so the slot has zero size: it reserves no space and shifts no layout.

## 5. Tests

- **Pure:** `canAddEntry`, which covers PRO at any count, FREE at 2, 3 and 4, and the grandfathered FREE user at 5; and `showAds`.
- **ViewModels** (with a fake Entitlements):
  - FREE at 2 creates;
  - FREE at 3 shows the dialog and creates or duplicates nothing;
  - PRO at 10 does both;
  - Go Pro starts the upgrade and closes the dialog;
  - Not now closes it.
- **Compose (Robolectric):**
  - the dialog's title, its text with 3 and both buttons;
  - the v1 renderer's zero height;
  - with a fake renderer and FREE, the slot appears on the list and the entry screen;
  - there is no `ad_slot` on the timer screen.
