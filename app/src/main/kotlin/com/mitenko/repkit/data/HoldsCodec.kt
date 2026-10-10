package com.mitenko.repkit.data

import com.mitenko.repkit.domain.model.Hold
import com.mitenko.repkit.domain.model.HoldKind

/**
 * The `entry.holds` column (spec rev 16 §5, rev 34 §4): `"56:3,60+:2"` in list order (an At hold
 * `at:for`, a From hold `at+:for`), `"-"` for an empty list. The empty string is the column default
 * and is never written, so it stays free to mean "not yet written by v5 code" (EntryMapping then
 * reads the legacy hold_at / hold_for).
 */
internal object HoldsCodec {
    private const val EMPTY = "-"
    private const val FROM_MARK = "+"

    /** `at` is plain digits, optionally followed by one `+` for a From hold (plan note 3). */
    private val AT_PART = Regex("""(\d+)(\+?)""")

    fun encode(holds: List<Hold>): String =
        if (holds.isEmpty()) EMPTY else holds.joinToString(",") { "${it.at}${if (it.kind == HoldKind.FROM) FROM_MARK else ""}:${it.forCount}" }

    /**
     * Item by item: a malformed item, or one with at < 1 or forCount < 0, is dropped and the rest are
     * kept in order. Null when no item survives (including `""`), unless [text] is the empty-list `"-"`.
     */
    fun decode(text: String): List<Hold>? {
        if (text == EMPTY) return emptyList()
        return text.split(",").mapNotNull(::decodeItem).ifEmpty { null }
    }

    private fun decodeItem(item: String): Hold? {
        val parts = item.split(":")
        if (parts.size != 2) return null
        val match = AT_PART.matchEntire(parts[0]) ?: return null
        val at = match.groupValues[1].toIntOrNull()?.takeIf { it >= 1 } ?: return null
        val forCount = parts[1].toIntOrNull()?.takeIf { it >= 0 } ?: return null
        val kind = if (match.groupValues[2].isEmpty()) HoldKind.AT else HoldKind.FROM
        return Hold(at, forCount, kind)
    }
}
