package com.mitenko.repkit.domain

import java.math.BigDecimal

/**
 * Weights as text (spec rev 26 §2, plan Spec note 26): integer hundredths shown as the shortest plain
 * decimal with "." in every locale, like the penalty rate, and read back with "." or ",". Pure.
 */
object WeightFormat {
    private val WEIGHT = Regex("""\d+([.,]\d{0,2})?|[.,]\d{1,2}""")
    private val MAX = BigDecimal.valueOf(Int.MAX_VALUE.toLong())

    /** 2250 → "22.5", 2000 → "20", 125 → "1.25". */
    fun format(hundredths: Int): String = BigDecimal.valueOf(hundredths.toLong(), 2).stripTrailingZeros().toPlainString()

    /** "22.5" or "22,5" → 2250. Null for 3+ decimals, a sign, letters, a blank, or more than Int.MAX_VALUE hundredths. */
    fun parse(text: String): Int? {
        val t = text.trim()
        if (!WEIGHT.matches(t)) return null
        val hundredths = BigDecimal(t.replace(',', '.')).movePointRight(2)
        return if (hundredths > MAX) null else hundredths.toInt()
    }

    /**
     * The count a weight's plural takes (plan Spec note 62). Android plurals take an Int, so a whole
     * weight is its own count, a fraction below 1 is 0 and any other fraction is 2. For en / es / zh / hi
     * that picks exactly CLDR's category for the decimal: "1 kilo", "1.5 kilos", Hindi "0.5" one.
     */
    fun pluralCount(hundredths: Int): Int = when {
        hundredths % 100 == 0 -> hundredths / 100
        hundredths < 100 -> 0
        else -> 2
    }
}
