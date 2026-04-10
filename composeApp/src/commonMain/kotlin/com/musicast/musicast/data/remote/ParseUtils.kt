package com.musicast.musicast.data.remote

/**
 * Parses iTunes duration strings to milliseconds.
 * Supports formats: "HH:MM:SS", "MM:SS", "seconds"
 */
fun String.toDurationMs(): Long? {
    val trimmed = trim()

    // Try as plain seconds
    trimmed.toLongOrNull()?.let { return it * 1000 }

    val parts = trimmed.split(":")
    return when (parts.size) {
        3 -> {
            val h = parts[0].toLongOrNull() ?: return null
            val m = parts[1].toLongOrNull() ?: return null
            val s = parts[2].toLongOrNull() ?: return null
            (h * 3600 + m * 60 + s) * 1000
        }
        2 -> {
            val m = parts[0].toLongOrNull() ?: return null
            val s = parts[1].toLongOrNull() ?: return null
            (m * 60 + s) * 1000
        }
        else -> null
    }
}

/**
 * Parses RFC 2822 date strings to epoch milliseconds.
 * Example: "Mon, 01 Jan 2024 12:00:00 +0000"
 */
fun String.toEpochMs(): Long? {
    val months = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4,
        "may" to 5, "jun" to 6, "jul" to 7, "aug" to 8,
        "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
    )

    return try {
        val cleaned = trim().replace("  ", " ")
        val parts = cleaned.split(" ").filter { it.isNotEmpty() }

        // "Mon, 01 Jan 2024 12:00:00 +0000" or "01 Jan 2024 12:00:00 +0000"
        val offset = if (parts[0].endsWith(",")) 1 else 0
        val day = parts[offset].toInt()
        val month = months[parts[offset + 1].lowercase().take(3)] ?: return null
        val year = parts[offset + 2].toInt()
        val timeParts = parts[offset + 3].split(":")
        val hour = timeParts[0].toInt()
        val minute = timeParts[1].toInt()
        val second = timeParts.getOrNull(2)?.toInt() ?: 0

        // Simple epoch calculation (UTC, ignoring timezone offset for sorting purposes)
        val daysFromEpoch = daysSinceEpoch(year, month, day)
        val secondsFromEpoch = daysFromEpoch * 86400L + hour * 3600L + minute * 60L + second
        secondsFromEpoch * 1000L
    } catch (_: Exception) {
        null
    }
}

private fun daysSinceEpoch(year: Int, month: Int, day: Int): Long {
    var y = year
    var m = month
    if (m <= 2) { y--; m += 12 }
    val era = y / 400
    val yoe = y - era * 400
    val doy = (153 * (m - 3) + 2) / 5 + day - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return (era * 146097L + doe - 719468)
}
