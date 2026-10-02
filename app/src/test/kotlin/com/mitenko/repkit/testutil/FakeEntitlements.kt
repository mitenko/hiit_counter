package com.mitenko.repkit.testutil

import com.mitenko.repkit.domain.Entitlements
import com.mitenko.repkit.domain.ProUpgrade
import com.mitenko.repkit.domain.Tier
import kotlinx.coroutines.flow.MutableStateFlow

/** A settable [Entitlements]; PRO by default, matching the v1 binding. */
class FakeEntitlements(tier: Tier = Tier.PRO) : Entitlements {
    override val tier = MutableStateFlow(tier)
}

/** Counts [start] calls. */
class FakeProUpgrade : ProUpgrade {
    var starts = 0
    override fun start() {
        starts++
    }
}
