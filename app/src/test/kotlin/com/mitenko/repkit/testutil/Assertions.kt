package com.mitenko.repkit.testutil

/** Returns the thrown [T]; fails if nothing or something else is thrown. Inlined, so [block] may suspend. */
inline fun <reified T : Throwable> expectThrows(block: () -> Unit): T {
    try {
        block()
    } catch (e: Throwable) {
        if (e is T) return e
        throw AssertionError("Expected ${T::class.java.simpleName} but got ${e::class.java.simpleName}", e)
    }
    throw AssertionError("Expected ${T::class.java.simpleName} but nothing was thrown")
}
