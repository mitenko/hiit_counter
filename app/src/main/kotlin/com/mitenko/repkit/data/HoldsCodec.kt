package com.mitenko.repkit.data

import com.mitenko.repkit.domain.model.Hold

/**
 * The `entry.holds` column (spec rev 16 §5): `"56:3,64:4"` in list order, `"-"` for an empty list.
 * The empty string is the column default and is never written, so it stays free to mean "not yet
 * written by v5 code" (EntryMapping then reads the legacy hold_at / hold_for).
 */
internal object HoldsCodec {
    private const val EMPTY = "-"

    fun encode(holds: List<Hold>): String =
        if (holds.isEmpty()) EMPTY else holds.joinToString(",") { "${it.at}:${it.forCount}" }

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
        val at = parts[0].toIntOrNull()?.takeIf { it >= 1 } ?: return null
        val forCount = parts[1].toIntOrNull()?.takeIf { it >= 0 } ?: return null
        return Hold(at, forCount)
    }
}
