package com.mitenko.repkit.di

import com.mitenko.repkit.BuildConfig
import com.mitenko.repkit.ads.AdMobRenderer
import com.mitenko.repkit.ads.AdPrivacy
import com.mitenko.repkit.ads.AdsController
import com.mitenko.repkit.ads.AdsSdk
import com.mitenko.repkit.ads.ConsentGateway
import com.mitenko.repkit.ads.MobileAdsSdk
import com.mitenko.repkit.ads.UmpConsentGateway
import com.mitenko.repkit.domain.Entitlements
import com.mitenko.repkit.domain.FreeLimits
import com.mitenko.repkit.domain.ProUpgrade
import com.mitenko.repkit.domain.Tier
import com.mitenko.repkit.monetisation.BillingEntitlements
import com.mitenko.repkit.monetisation.CurrentActivityProvider
import com.mitenko.repkit.monetisation.FixedEntitlements
import com.mitenko.repkit.monetisation.ProStore
import com.mitenko.repkit.platform.CurrentActivity
import com.mitenko.repkit.platform.billing.BillingGateway
import com.mitenko.repkit.platform.billing.PlayBillingGateway
import com.mitenko.repkit.ui.ads.AdRenderer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Spec rev 41 §1.4: the debug tier override; null means use billing. Release builds always use billing. */
fun forcedTier(debug: Boolean, value: String): Tier? =
    if (!debug) null else runCatching { Tier.valueOf(value.trim().uppercase()) }.getOrNull()

/** The override, if any, as one shared [FixedEntitlements]. */
class ForcedTier(tier: Tier?) {
    val fixed: FixedEntitlements? = tier?.let(::FixedEntitlements)
}

/**
 * Monetisation bindings (spec revisions 18 and 41): Play Billing behind [BillingEntitlements], unless a debug build
 * forces a tier. [dagger.Lazy] keeps an override build from ever constructing [BillingEntitlements] (no Play connection).
 */
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

    @Provides @Singleton
    fun consentGateway(impl: UmpConsentGateway): ConsentGateway = impl

    @Provides @Singleton
    fun adsSdk(impl: MobileAdsSdk): AdsSdk = impl

    /** Spec rev 41 §2.2: Settings' "Ad privacy choices" row talks to the controller through this. */
    @Provides @Singleton
    fun adPrivacy(controller: AdsController): AdPrivacy = controller

    /** Spec rev 41 §2.1: the AdMob renderer; it draws nothing until consent and initialize are done. */
    @Provides @Singleton
    fun adRenderer(controller: AdsController): AdRenderer = AdMobRenderer(controller.ready)
}
