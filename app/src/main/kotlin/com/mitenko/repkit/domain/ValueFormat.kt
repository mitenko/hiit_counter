package com.mitenko.repkit.domain

import java.math.BigDecimal

/** Parsing and formatting for the settings edit dialog (spec §8.2). Pure; null means "doesn't parse". */
object ValueFormat {
    private val MIN_SEC = Regex("""(\d+):([0-5]\d)""")
    private val WHOLE = Regex("""\d+""")
    private val DECIMAL = Regex("""\d+(\.\d*)?|\.\d+""")

    /** `m:ss` with exactly two second digits 00–59 (`"1:30"` = 90), or plain whole seconds (`"90"`). */
    fun parseSeconds(text: String): Int? {
        val t = text.trim()
        MIN_SEC.matchEntire(t)?.let { m ->
            val minutes = m.groupValues[1].toLongOrNull() ?: return null
            val total = minutes * 60 + m.groupValues[2].toLong()
            return if (total <= Int.MAX_VALUE) total.toInt() else null
        }
        return parseInt(t)
    }

    /** `formatSeconds(90) = "01:30"`. The hard range keeps values ≤ 59:59, so there is no hour form. */
    fun formatSeconds(sec: Int): String = TimerText.formatMmSs(sec)

    /** Non-negative whole numbers only. */
    fun parseInt(text: String): Int? {
        val t = text.trim()
        return if (WHOLE.matches(t)) t.toIntOrNull() else null
    }

    /** Non-negative finite decimals; `.` or `,` as the separator. */
    fun parseDecimal(text: String): Double? {
        val t = text.trim().replace(',', '.')
        if (!DECIMAL.matches(t)) return null
        return t.toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    /** Shortest plain form: 19.5 → "19.5", 20.0 → "20", 0.3 → "0.3". */
    fun formatDecimal(value: Double): String = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
}
