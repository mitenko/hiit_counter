package com.mitenko.repkit.testutil

import com.mitenko.repkit.ui.common.UiText

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

/** The untranslated parts of a [UiText] (its [UiText.Raw] args, at any depth), joined: e.g. an exception's message. */
fun UiText.rawText(): String = when (this) {
    is UiText.Raw -> text
    is UiText.Res -> args.filterIsInstance<UiText>().joinToString(" ") { it.rawText() }
    is UiText.Plural -> args.filterIsInstance<UiText>().joinToString(" ") { it.rawText() }
}
