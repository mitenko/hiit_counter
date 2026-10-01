package com.mitenko.repkit.ui.common

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateFormats {
    private val dateTime = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH)
    private val date = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val monthYear = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

    fun dateTime(instant: Instant, zone: ZoneId): String = dateTime.format(instant.atZone(zone))
    fun date(instant: Instant, zone: ZoneId): String = date.format(instant.atZone(zone))

    /** The chart's date labels (spec R6 §4.2, rev 9 §3): "12 Sep". */
    fun dayMonth(instant: Instant, zone: ZoneId): String = dayMonth.format(instant.atZone(zone))

    /** A calendar block's title: "September 2026". */
    fun monthYear(month: YearMonth): String = monthYear.format(month)
}
