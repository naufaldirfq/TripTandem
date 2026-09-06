package com.triptandem.shared

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

actual fun currentEpochMillis(): Long = System.currentTimeMillis()

actual fun localDateTimeToEpochMillis(dayDate: String?, timeLabel: String?, timezoneId: String): Long? {
    val date = dayDate?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val label = timeLabel?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (!date.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) || !label.matches(Regex("\\d{1,2}:\\d{2}"))) return null
    val zone = TimeZone.getTimeZone(timezoneId.trim())
    if (zone.id == "GMT" && timezoneId.trim() !in setOf("GMT", "UTC", "Etc/GMT", "Etc/UTC")) return null
    val value = "$date ${label.padStart(5, '0')}"
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply {
        isLenient = false
        timeZone = zone
    }
    val position = ParsePosition(0)
    val parsed = formatter.parse(value, position) ?: return null
    if (position.index != value.length || formatter.format(parsed) != value) return null
    return parsed.time
}

actual fun supportsAppleSignIn(): Boolean = false
