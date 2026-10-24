package com.mitenko.repkit.ads

import com.mitenko.repkit.di.ApplicationScope
import com.mitenko.repkit.domain.CrashReporter
import com.mitenko.repkit.domain.Entitlements
import com.mitenko.repkit.domain.Tier
import com.mitenko.repkit.monetisation.CurrentActivityProvider
import com.mitenko.repkit.monetisation.ProStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Spec rev 41 §2.2: the Settings row's view of the privacy options form; a seam so view-model tests can fake it. */
interface AdPrivacy {
    /** True when the consent state says the user must be able to reopen the privacy options form. */
    val required: StateFlow<Boolean>
    fun open()
}

/**
 * Spec rev 41 §2.2: on FREE, tier → consent → MobileAds.initialize, and only then [ready]. PRO never runs any of
 * it, and turning PRO at any step ends not ready.
 */
@Singleton
class AdsController @Inject constructor(
    private val entitlements: Entitlements,
    private val proStore: ProStore,
    private val consent: ConsentGateway,
    private val sdk: AdsSdk,
    private val activity: CurrentActivityProvider,
    private val crash: CrashReporter,
    @param:ApplicationScope private val scope: CoroutineScope,
) : AdPrivacy {
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()
    private val _privacy = MutableStateFlow(false)
    val privacyOptionsRequired: StateFlow<Boolean> = _privacy.asStateFlow()

    private val lock = Mutex()
    private var initialized = false

    init {
        scope.launch {
            entitlements.tier.collect { tier ->
                if (tier == Tier.PRO) {
                    _ready.value = false
                    _privacy.value = false
                }
            }
        }
    }

    /** Called from MainActivity.onResume: (re)runs the sequence on FREE. */
    fun onForeground() {
        scope.launch { guarded { run() } }
    }

    /** Nothing in the ad sequence may throw out of the app scope: a failure is logged (exception type only) and ads stay off. */
    private suspend fun guarded(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            crash.log("ads: sequence failed: ${e.javaClass.simpleName}")
        }
    }

    private fun free() = entitlements.tier.value == Tier.FREE

    private suspend fun run() = lock.withLock {
        // A Pro user reads FREE until the cached tier is loaded; never start consent before that (Review Focus 1).
        proStore.cacheLoaded.first { it }
        if (!free()) return@withLock
        val a = activity.get() ?: return@withLock
        val state = consent.update(a)
        if (!state.ok) {
            crash.log("ads: consent update failed")
            return@withLock
        }
        if (!free()) return@withLock
        _privacy.value = state.privacyOptionsRequired
        consent.showFormIfRequired(a)
        if (!free()) return@withLock
        if (!consent.canRequestAds()) {
            _ready.value = false
            return@withLock
        }
        if (!initialized) {
            sdk.initialize()
            initialized = true
        }
        _ready.value = free()
    }

    override val required: StateFlow<Boolean> get() = privacyOptionsRequired

    override fun open() = showPrivacyOptions()

    fun showPrivacyOptions() {
        val a = activity.get() ?: return
        consent.showPrivacyOptions(a)
    }
}
