package com.mitenko.repkit.testutil

import com.mitenko.repkit.monetisation.ProEvent
import com.mitenko.repkit.monetisation.ProStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
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

/** A [ProStore] with settable state; [restores] counts [restore] calls. */
class FakeProStore : ProStore {
    override val cacheLoaded = MutableStateFlow(true)
    override val pending = MutableStateFlow(false)
    override val price = MutableStateFlow<String?>(null)
    val eventFlow = MutableSharedFlow<ProEvent>(extraBufferCapacity = 8)
    override val events: Flow<ProEvent> = eventFlow
    var restores = 0
    override fun refresh() {}
    override fun restore() {
        restores++
    }
}
