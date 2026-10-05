package com.mitenko.repkit.testutil

import com.mitenko.repkit.domain.CrashReporter

/** A fake [CrashReporter] for JVM tests: Firebase never loads. */
class RecordingCrashReporter : CrashReporter {
    val enabled = mutableListOf<Boolean>()
    val nonFatals = mutableListOf<Pair<Throwable, String>>()
    val logs = mutableListOf<String>()

    override fun setEnabled(enabled: Boolean) {
        this.enabled += enabled
    }

    override fun recordNonFatal(t: Throwable, context: String) {
        nonFatals += t to context
    }

    override fun log(message: String) {
        logs += message
    }
}
