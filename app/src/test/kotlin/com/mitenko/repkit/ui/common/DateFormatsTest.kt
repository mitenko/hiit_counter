package com.mitenko.repkit.ui.common

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

/** Robolectric for android.icu; every call pins its locale (spec revision 24). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DateFormatsTest {
    private val t = Instant.parse("2026-09-24T12:55:00Z")
    private val zone = ZoneId.of("America/Los_Angeles")

    @Test
    fun `formats like the sheet in the given zone`() {
        assertEquals("24 Sep 2026, 05:55", DateFormats.dateTime(t, zone, Locale.ENGLISH))
        assertEquals("24 Sep 2026", DateFormats.date(t, zone, Locale.ENGLISH))
    }

    @Test
    fun `formats a day and month, and a month and year`() {
        assertEquals("5 Sep", DateFormats.dayMonth(Instant.parse("2026-09-05T20:00:00Z"), zone, Locale.ENGLISH))
        assertEquals("September 2026", DateFormats.monthYear(YearMonth.of(2026, 9), Locale.ENGLISH))
    }

    @Test
    fun `every English locale keeps the same day-first text`() {
        listOf(Locale.US, Locale.UK, Locale.forLanguageTag("en-IN")).forEach { locale ->
            assertEquals("24 Sep 2026, 05:55", DateFormats.dateTime(t, zone, locale))
            assertEquals("September 2026", DateFormats.monthYear(YearMonth.of(2026, 9), locale))
        }
    }

    @Test
    fun `other languages use their own month names and order`() {
        val es = Locale.forLanguageTag("es-ES")
        assertTrue(DateFormats.monthYear(YearMonth.of(2026, 9), es).contains("septiembre"))
        assertTrue(DateFormats.date(t, zone, es).contains("2026"))
        assertTrue(DateFormats.dayMonth(t, zone, es).startsWith("24"))
        val zh = Locale.SIMPLIFIED_CHINESE
        assertTrue(DateFormats.monthYear(YearMonth.of(2026, 9), zh).contains("9月"))
        assertNotEquals("24 Sep", DateFormats.dayMonth(t, zone, zh))
        // The zone still applies: 12:55 UTC is 05:55 in Los Angeles.
        assertTrue(DateFormats.dateTime(t, zone, Locale.GERMANY).contains("05:55"))
    }
}
