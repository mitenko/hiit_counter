package com.mitenko.repkit.domain

import kotlinx.coroutines.flow.StateFlow

/** The user's tier (spec revision 18 §2). v1 is always [PRO]: everything unlocked, no ads. */
enum class Tier { FREE, PRO }

/** What the user is entitled to (spec revision 18 §2). The v1 binding always says PRO. */
interface Entitlements {
    val tier: StateFlow<Tier>
}

/** Starts the upgrade to Pro (spec revision 18 §3). v1's binding does nothing; billing implements it later. */
interface ProUpgrade {
    fun start()
}

/** The free tier's limits (spec revision 18 §3). */
data class FreeLimits(val maxEntries: Int = 3)

/**
 * Spec revision 18 §3: PRO can always add; FREE only below [FreeLimits.maxEntries]. A free user
 * already over the limit keeps every entry but can't add another.
 */
fun canAddEntry(tier: Tier, entryCount: Int, limits: FreeLimits): Boolean =
    tier == Tier.PRO || entryCount < limits.maxEntries

/** Spec revision 18 §4: ads are for the free tier only. Where they go is the UI's decision. */
fun showAds(tier: Tier): Boolean = tier == Tier.FREE
