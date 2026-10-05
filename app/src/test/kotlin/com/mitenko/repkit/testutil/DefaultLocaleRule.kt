package com.mitenko.repkit.testutil

import org.junit.rules.ExternalResource
import java.util.Locale

/** Pins the JVM's default locale for a test (spec revision 24), so date text doesn't depend on the machine. */
class DefaultLocaleRule(private val locale: Locale = Locale.ENGLISH) : ExternalResource() {
    private lateinit var saved: Locale

    override fun before() {
        saved = Locale.getDefault()
        Locale.setDefault(locale)
    }

    override fun after() {
        Locale.setDefault(saved)
    }
}
