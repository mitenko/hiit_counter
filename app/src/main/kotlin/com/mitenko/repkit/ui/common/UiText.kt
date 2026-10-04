package com.mitenko.repkit.ui.common

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources

/**
 * User-facing text that isn't resolved yet (spec revision 24): a string or plural resource with its
 * format args, resolved where `Resources` exist, so ViewModels and mappers stay free of English.
 * An arg that is itself a [UiText] is resolved first. [Raw] is text that is already final and
 * never translated, such as a system exception's message.
 */
sealed interface UiText {
    data class Res(@param:StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    data class Plural(@param:PluralsRes val id: Int, val count: Int, val args: List<Any> = listOf(count)) : UiText

    data class Raw(val text: String) : UiText

    fun resolve(res: Resources): String = when (this) {
        is Res -> res.getString(id, *args.resolveArgs(res))
        is Plural -> res.getQuantityString(id, count, *args.resolveArgs(res))
        is Raw -> text
    }
}

private fun List<Any>.resolveArgs(res: Resources): Array<Any> =
    map { if (it is UiText) it.resolve(res) else it }.toTypedArray()

/** Resolves against the composition's resources, so a locale change recomposes with the new text. */
@Composable
fun UiText.resolve(): String = resolve(LocalResources.current)
