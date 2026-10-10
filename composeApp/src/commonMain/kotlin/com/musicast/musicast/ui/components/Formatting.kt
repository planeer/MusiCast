package com.musicast.musicast.ui.components

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** "Today", "Yesterday", "3 days ago", "12 Mar", or "12 Mar 2024" for older years. */
fun formatPublishDate(epochMs: Long): String {
    val zone = TimeZone.currentSystemDefault()
    val date = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(zone).date
    val today = Clock.System.now().toLocalDateTime(zone).date
    val daysAgo = date.daysUntil(today)
    return when {
        daysAgo <= 0 -> "Today"
        daysAgo == 1 -> "Yesterday"
        daysAgo < 7 -> "$daysAgo days ago"
        date.year == today.year -> "${date.dayOfMonth} ${MONTHS[date.monthNumber - 1]}"
        else -> "${date.dayOfMonth} ${MONTHS[date.monthNumber - 1]} ${date.year}"
    }
}

/** "1h 44m" or "38m"; null for missing/zero durations, which feeds often report. */
fun formatDuration(ms: Long?): String? {
    if (ms == null || ms <= 0) return null
    val totalMinutes = (ms + 30_000) / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours == 0L -> "${minutes.coerceAtLeast(1)}m"
        minutes == 0L -> "${hours}h"
        else -> "${hours}h ${minutes}m"
    }
}
