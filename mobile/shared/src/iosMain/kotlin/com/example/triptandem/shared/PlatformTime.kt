package com.triptandem.shared

import platform.CoreFoundation.CFAbsoluteTimeGetCurrent
import platform.Foundation.NSDateComponents
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSTimeZone
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.timeZoneWithName

actual fun currentEpochMillis(): Long = ((CFAbsoluteTimeGetCurrent() + 978307200.0) * 1000.0).toLong()

actual fun localDateTimeToEpochMillis(dayDate: String?, timeLabel: String?, timezoneId: String): Long? {
    val dateParts = dayDate?.trim()?.split("-") ?: return null
    val timeParts = timeLabel?.trim()?.split(":") ?: return null
    if (dateParts.size != 3 || timeParts.size != 2) return null
    val year = dateParts[0].toIntOrNull() ?: return null
    val month = dateParts[1].toIntOrNull() ?: return null
    val day = dateParts[2].toIntOrNull() ?: return null
    val hour = timeParts[0].toIntOrNull() ?: return null
    val minute = timeParts[1].toIntOrNull() ?: return null
    if (year !in 1..9999 || month !in 1..12 || day !in 1..31 || hour !in 0..23 || minute !in 0..59) return null
    val calendar = NSCalendar(calendarIdentifier = NSCalendarIdentifierGregorian) ?: return null
    calendar.timeZone = NSTimeZone.timeZoneWithName(timezoneId.trim()) ?: return null
    val components = NSDateComponents().apply {
        this.year = year.toLong()
        this.month = month.toLong()
        this.day = day.toLong()
        this.hour = hour.toLong()
        this.minute = minute.toLong()
    }
    val date = calendar.dateFromComponents(components) ?: return null
    val check = calendar.components(
        unitFlags = NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay or
            NSCalendarUnitHour or NSCalendarUnitMinute,
        fromDate = date,
    )
    if (check.year.toInt() != year || check.month.toInt() != month || check.day.toInt() != day || check.hour.toInt() != hour || check.minute.toInt() != minute) return null
    return (date.timeIntervalSince1970() * 1000.0).toLong()
}

actual fun supportsAppleSignIn(): Boolean = true
