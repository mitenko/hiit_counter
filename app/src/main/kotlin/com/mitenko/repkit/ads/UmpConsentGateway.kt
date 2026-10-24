package com.mitenko.repkit.ads

import android.app.Activity
import android.content.Context
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform
import com.mitenko.repkit.BuildConfig
import com.mitenko.repkit.domain.CrashReporter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Google's User Messaging Platform (spec rev 41 §2.2). It never throws: every UMP error becomes a
 * `ConsentState(ok = false)` or a logged return, with the error code only (no personal data).
 */
@Singleton
class UmpConsentGateway @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val crash: CrashReporter,
) : ConsentGateway {
    private val info: ConsentInformation by lazy { UserMessagingPlatform.getConsentInformation(context) }

    override suspend fun update(activity: Activity): ConsentState = try {
        // UMP is a main-thread API; the controller runs on the application scope.
        withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { cont ->
                info.requestConsentInfoUpdate(
                    activity, params(activity),
                    {
                        val required = info.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
                        cont.resume(ConsentState(ok = true, privacyOptionsRequired = required))
                    },
                    { error ->
                        logError("consent update", error)
                        cont.resume(ConsentState(ok = false, privacyOptionsRequired = false))
                    },
                )
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        crash.log("ads: consent update threw ${e.javaClass.simpleName}")
        ConsentState(ok = false, privacyOptionsRequired = false)
    }

    override suspend fun showFormIfRequired(activity: Activity) {
        try {
            withContext(Dispatchers.Main.immediate) {
                suspendCancellableCoroutine { cont ->
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                        logError("consent form", error)
                        cont.resume(Unit)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            crash.log("ads: consent form threw ${e.javaClass.simpleName}")
        }
    }

    override fun canRequestAds(): Boolean = try {
        info.canRequestAds()
    } catch (e: Exception) {
        crash.log("ads: canRequestAds threw ${e.javaClass.simpleName}")
        false
    }

    override fun showPrivacyOptions(activity: Activity) {
        try {
            UserMessagingPlatform.showPrivacyOptionsForm(activity) { error -> logError("privacy options", error) }
        } catch (e: Exception) {
            crash.log("ads: privacy options threw ${e.javaClass.simpleName}")
        }
    }

    /** Debug builds only, and only with -Prepkit.consentEea=true: pretend to be in the EEA to see the consent form. */
    private fun params(activity: Activity): ConsentRequestParameters {
        val builder = ConsentRequestParameters.Builder()
        if (BuildConfig.DEBUG && BuildConfig.CONSENT_EEA) {
            val debug = ConsentDebugSettings.Builder(activity)
                .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
            if (BuildConfig.CONSENT_TEST_DEVICE.isNotBlank()) debug.addTestDeviceHashedId(BuildConfig.CONSENT_TEST_DEVICE)
            builder.setConsentDebugSettings(debug.build())
        }
        return builder.build()
    }

    private fun logError(where: String, error: FormError?) {
        if (error != null) crash.log("ads: $where error ${error.errorCode}")
    }
}
