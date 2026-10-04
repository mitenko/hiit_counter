package com.mitenko.repkit.domain

sealed interface NameCheck {
    data class Ok(val name: String) : NameCheck
    data object Empty : NameCheck
    data object TooLong : NameCheck
}

/** A create or rename with a name [EntryNames.validate] rejects; the UI explains [check] from resources. */
class InvalidEntryName(val check: NameCheck) : IllegalArgumentException("Invalid entry name: $check")

/** Entry-name rules (spec §5.5). Lengths are `String.length` (UTF-16 units). Duplicates are allowed. */
object EntryNames {
    const val MAX_LENGTH = 40

    fun validate(raw: String): NameCheck {
        val name = raw.trim()
        return when {
            name.isEmpty() -> NameCheck.Empty
            name.length > MAX_LENGTH -> NameCheck.TooLong
            else -> NameCheck.Ok(name)
        }
    }

    /**
     * The base is cut so the suffix always survives: `base.take(40 - suffix.length) + suffix`. The
     * suffix (" copy" in English) comes from resources (spec revision 24).
     */
    fun duplicateName(base: String, suffix: String): String = base.take((MAX_LENGTH - suffix.length).coerceAtLeast(0)) + suffix
}
