package com.mitenko.repkit.ads

import android.content.Context
import com.google.android.gms.ads.MobileAds
import com.mitenko.repkit.domain.CrashReporter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** The real [AdsSdk] (spec rev 41 §2.2): initializes off the main thread and never throws. */
@Singleton
class MobileAdsSdk @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val crash: CrashReporter,
) : AdsSdk {
    override suspend fun initialize() {
        try {
            withContext(Dispatchers.IO) {
                suspendCancellableCoroutine { cont -> MobileAds.initialize(context) { cont.resume(Unit) } }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            crash.log("ads: initialize threw ${e.javaClass.simpleName}")
        }
    }
}
