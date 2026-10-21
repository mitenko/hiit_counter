package com.mitenko.repkit.monetisation

import com.mitenko.repkit.domain.Entitlements
import com.mitenko.repkit.domain.ProUpgrade
import com.mitenko.repkit.domain.Tier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
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
