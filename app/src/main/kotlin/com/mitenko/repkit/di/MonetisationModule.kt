package com.mitenko.repkit.di

import com.mitenko.repkit.domain.Entitlements
import com.mitenko.repkit.domain.FreeLimits
import com.mitenko.repkit.domain.ProUpgrade
import com.mitenko.repkit.monetisation.NoOpProUpgrade
import com.mitenko.repkit.monetisation.UnlockedEntitlements
import com.mitenko.repkit.ui.ads.AdRenderer
import com.mitenko.repkit.ui.ads.NoAdRenderer
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Monetisation seams (spec revision 18). These are the v1 bindings: everything unlocked, Go Pro a
 * no-op and an ad renderer that draws nothing. Billing and ads replace them here later.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class MonetisationModule {
    @Binds @Singleton
    abstract fun entitlements(impl: UnlockedEntitlements): Entitlements

    @Binds
    abstract fun proUpgrade(impl: NoOpProUpgrade): ProUpgrade

    companion object {
        @Provides
        fun freeLimits(): FreeLimits = FreeLimits()

        /** Draws nothing (spec revision 18 §4); an ads SDK's renderer replaces it later. */
        @Provides
        fun adRenderer(): AdRenderer = NoAdRenderer
    }
}
