package com.mitenko.repkit.ui.common

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import android.icu.text.DateFormat as IcuDateFormat
import android.icu.util.TimeZone as IcuTimeZone

/**
 * Dates in the given locale (spec revision 24), the device's by default. Every English locale keeps
 * the app's day-first patterns and [Locale.ENGLISH] month names exactly ("24 Sep 2026, 05:55",
 * "12 Sep", "September 2026"; en-GB would otherwise say "Sept"); every other language gets its own
 * order, month names and punctuation from the same ICU skeleton.
 */
object DateFormats {
    private const val DATE_TIME = "yMMMdHHmm"
    private const val DATE = "yMMMd"
    private const val DAY_MONTH = "MMMd"
    private const val MONTH_YEAR = "yMMMM"

    private val english = mapOf(
        DATE_TIME to "d MMM yyyy, HH:mm",
        DATE to "d MMM yyyy",
        DAY_MONTH to "d MMM",
        MONTH_YEAR to "MMMM yyyy",
    ).mapValues { (_, pattern) -> DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH) }

    fun dateTime(instant: Instant, zone: ZoneId, locale: Locale = Locale.getDefault()): String =
        format(DATE_TIME, instant, zone, locale)

    fun date(instant: Instant, zone: ZoneId, locale: Locale = Locale.getDefault()): String =
        format(DATE, instant, zone, locale)

    /** The chart's date labels (spec R6 §4.2, rev 9 §3): "12 Sep". */
    fun dayMonth(instant: Instant, zone: ZoneId, locale: Locale = Locale.getDefault()): String =
        format(DAY_MONTH, instant, zone, locale)

    /** A calendar block's title: "September 2026". */
    fun monthYear(month: YearMonth, locale: Locale = Locale.getDefault()): String =
        format(MONTH_YEAR, month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC, locale)

    private fun format(skeleton: String, instant: Instant, zone: ZoneId, locale: Locale): String {
        if (locale.language == Locale.ENGLISH.language) {
            return english.getValue(skeleton).format(instant.atZone(zone))
        }
        // ICU formats aren't thread-safe; one per call is cheap, as ICU caches the locale data.
        val icu = IcuDateFormat.getInstanceForSkeleton(skeleton, locale)
        icu.timeZone = IcuTimeZone.getTimeZone(zone.id)
        return icu.format(Date.from(instant))
    }
}
