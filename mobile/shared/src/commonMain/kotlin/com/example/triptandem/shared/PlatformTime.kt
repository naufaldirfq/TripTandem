package com.triptandem.shared

/** Current wall-clock time used only for user-facing expiry/audit labels. */
expect fun currentEpochMillis(): Long

/**
 * Converts a destination-local ISO date and optional HH:mm label to an epoch
 * timestamp. A null result means the label is intentionally flexible (for
 * example, "morning") or the supplied date/timezone cannot be parsed.
 */
expect fun localDateTimeToEpochMillis(dayDate: String?, timeLabel: String?, timezoneId: String): Long?

/** Provider availability follows the actual host platform. */
expect fun supportsAppleSignIn(): Boolean
