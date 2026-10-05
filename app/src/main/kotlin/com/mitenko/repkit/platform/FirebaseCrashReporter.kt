package com.mitenko.repkit.platform

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mitenko.repkit.domain.CrashReporter

/**
 * [CrashReporter] over Firebase Crashlytics and Analytics (spec revision 30). Both start disabled
 * by the manifest; [setEnabled] is the only thing that turns them on. Analytics sends its automatic
 * events only: the app logs no custom events and sets no user properties.
 */
class FirebaseCrashReporter(context: Context) : CrashReporter {
    private val appContext = context.applicationContext

    override fun setEnabled(enabled: Boolean) {
        val crashlytics = FirebaseCrashlytics.getInstance()
        crashlytics.setCrashlyticsCollectionEnabled(enabled)
        // Switching off also drops reports cached while collection was off, so none leave later.
        if (!enabled) crashlytics.deleteUnsentReports()
        FirebaseAnalytics.getInstance(appContext).setAnalyticsCollectionEnabled(enabled)
    }

    override fun recordNonFatal(t: Throwable, context: String) {
        val crashlytics = FirebaseCrashlytics.getInstance()
        crashlytics.log(context)
        crashlytics.recordException(t)
    }

    override fun log(message: String) {
        FirebaseCrashlytics.getInstance().log(message)
    }
}
