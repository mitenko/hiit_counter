package com.mitenko.repkit.monetisation

import com.mitenko.repkit.domain.Entitlements
import com.mitenko.repkit.domain.ProUpgrade
import com.mitenko.repkit.domain.Tier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The v1 binding (spec revision 18 §2): always PRO, so the entry limit and ads never apply.
 * A billing-backed [Entitlements] replaces it later in [com.mitenko.repkit.di.MonetisationModule].
 */
@Singleton
class UnlockedEntitlements @Inject constructor() : Entitlements {
    override val tier: StateFlow<Tier> = MutableStateFlow(Tier.PRO).asStateFlow()
}

/** The v1 binding (spec revision 18 §3): Go Pro does nothing yet. Billing implements [ProUpgrade] later. */
class NoOpProUpgrade @Inject constructor() : ProUpgrade {
    override fun start() = Unit
}
