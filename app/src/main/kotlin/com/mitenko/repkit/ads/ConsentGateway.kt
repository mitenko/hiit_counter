package com.mitenko.repkit.ads

import android.app.Activity

/** Google's UMP behind a testable face (spec rev 41 §2.2). [UmpConsentGateway] is the real one. */
interface ConsentGateway {
    /** requestConsentInfoUpdate; ok is false on an error (offline, misconfigured). */
    suspend fun update(activity: Activity): ConsentState

    /** loadAndShowConsentFormIfRequired; returns when the form is closed or wasn't needed. */
    suspend fun showFormIfRequired(activity: Activity)

    fun canRequestAds(): Boolean

    /** Reopens the form from Settings → Ad privacy choices. */
    fun showPrivacyOptions(activity: Activity)
}

data class ConsentState(val ok: Boolean, val privacyOptionsRequired: Boolean)

/** MobileAds.initialize behind a testable face. Implementations must not throw: they log a failure and return. */
interface AdsSdk {
    /** Switches to Dispatchers.IO itself (MobileAds.initialize must not run on the main thread); returns when the SDK is ready. */
    suspend fun initialize()
}
