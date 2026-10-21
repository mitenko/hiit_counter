package com.mitenko.repkit.platform

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.mitenko.repkit.monetisation.CurrentActivityProvider
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

/** Tracks the resumed Activity for Play's purchase sheet (spec rev 41 §1.5). Weak, so it never leaks one. */
@Singleton
class CurrentActivity @Inject constructor() : CurrentActivityProvider, Application.ActivityLifecycleCallbacks {
    private var resumed: WeakReference<Activity>? = null
    override fun get(): Activity? = resumed?.get()
    override fun onActivityResumed(activity: Activity) { resumed = WeakReference(activity) }
    override fun onActivityPaused(activity: Activity) { if (resumed?.get() === activity) resumed = null }
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
