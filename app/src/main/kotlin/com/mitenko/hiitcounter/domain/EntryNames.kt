package com.mitenko.hiitcounter.domain

sealed interface NameCheck {
    data class Ok(val name: String) : NameCheck
    data object Empty : NameCheck
    data object TooLong : NameCheck
}

/** Entry-name rules (spec §5.5). Lengths are `String.length` (UTF-16 units). Duplicates are allowed. */
object EntryNames {
    const val MAX_LENGTH = 40
    const val COPY_SUFFIX = " copy"

    fun validate(raw: String): NameCheck {
        val name = raw.trim()
        return when {
            name.isEmpty() -> NameCheck.Empty
            name.length > MAX_LENGTH -> NameCheck.TooLong
            else -> NameCheck.Ok(name)
        }
    }

    /** The base is cut so the suffix always survives: `base.take(40 - " copy".length) + " copy"`. */
    fun duplicateName(base: String): String = base.take(MAX_LENGTH - COPY_SUFFIX.length) + COPY_SUFFIX

    /** Inline explanation for the name dialog (spec §8.3); null when the name is valid. */
    fun errorMessage(check: NameCheck): String? = when (check) {
        is NameCheck.Ok -> null
        NameCheck.Empty -> "Enter a name"
        NameCheck.TooLong -> "Use at most $MAX_LENGTH characters"
    }
}
