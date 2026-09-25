package com.mitenko.hiitcounter.ui.common

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateFormats {
    private val dateTime = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH)
    private val date = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

    fun dateTime(instant: Instant, zone: ZoneId): String = dateTime.format(instant.atZone(zone))
    fun date(instant: Instant, zone: ZoneId): String = date.format(instant.atZone(zone))
}
