package com.mitenko.repkit.ui.entry

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mitenko.repkit.R
import com.mitenko.repkit.domain.CalendarDay
import com.mitenko.repkit.domain.MonthGrid
import com.mitenko.repkit.domain.calendarMonths
import com.mitenko.repkit.domain.model.CheckInPoint
import com.mitenko.repkit.ui.common.DateFormats
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * The Timer only calendar (spec R6 §4.2, placed by rev 9 §3 in the chart area): month blocks,
 * oldest first, from [start] to [end] (today), scrolling vertically inside the chart area. A
 * checked-in day is a filled primary circle, any other in-range day a hollow one, and an
 * out-of-range day is blank. Screen readers get the summary, not the grid (plan Spec note 20).
 */
@Composable
fun CheckInCalendar(points: List<CheckInPoint>, start: Instant, end: Instant, zone: ZoneId, modifier: Modifier = Modifier) {
    val months = remember(points, start, end, zone) { calendarMonths(points, start, end, zone) }
    val checkedDays = months.sumOf { month -> month.weeks.sumOf { week -> week.count { it?.checkedIn == true } } }
    val description = pluralStringResource(R.plurals.calendar_desc, checkedDays, checkedDays)
    // Plan Spec note 20: opens at the newest month; the first layout clamps the value to the real maximum.
    val scroll = rememberScrollState(initial = Int.MAX_VALUE)
    Column(
        modifier
            .fillMaxSize()
            .testTag("calendar")
            .semantics { contentDescription = description }
            .verticalScroll(scroll),
    ) {
        months.forEach { MonthBlock(it) }
    }
}

@Composable
private fun MonthBlock(grid: MonthGrid) {
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Text(
            DateFormats.monthYear(grid.month),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Column(Modifier.clearAndSetSemantics { }) {
            Row(Modifier.fillMaxWidth()) {
                DayOfWeek.entries.forEach { day ->
                    Text(
                        day.getDisplayName(TextStyle.NARROW, Locale.ENGLISH),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            grid.weeks.forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { day -> DayCell(day, Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: CalendarDay?, modifier: Modifier = Modifier) {
    Box(modifier.height(36.dp), contentAlignment = Alignment.Center) {
        if (day != null && day.inRange) {
            val circle = Modifier.size(28.dp)
            Box(
                if (day.checkedIn) {
                    circle.background(MaterialTheme.colorScheme.primary, CircleShape)
                } else {
                    circle.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${day.date.dayOfMonth}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (day.checkedIn) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
