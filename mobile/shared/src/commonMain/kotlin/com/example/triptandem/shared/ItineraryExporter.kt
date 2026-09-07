package com.triptandem.shared

enum class ExportFormat(val wireValue: String, val label: String) {
    PlainText("plain_text", "Plain Text"),
    Markdown("markdown", "Markdown"),
}

data class ExportOptions(
    val format: ExportFormat = ExportFormat.PlainText,
    val includeTimes: Boolean = true,
    val includePlaces: Boolean = true,
    val includeNotes: Boolean = false,
    val includeLodgingDetails: Boolean = false,
    val includeMemberInitials: Boolean = false,
) {
    fun includedFieldCount(): Int {
        var count = 0
        if (includeTimes) count++
        if (includePlaces) count++
        if (includeNotes) count++
        if (includeLodgingDetails) count++
        if (includeMemberInitials) count++
        return count
    }
}

object ItineraryExporter {

    fun generateSummary(
        trip: TripRecord,
        items: List<ItineraryItem>,
        team: List<TripMember> = emptyList(),
        options: ExportOptions = ExportOptions(),
        generatedAtEpochMillis: Long = currentEpochMillis(),
    ): String {
        val days = daysInclusive(trip.startDate, trip.endDate).coerceIn(1, 60)
        val activeItems = items.filter { it.deletedAtEpochMillis == null && it.status != ItineraryItemStatus.Cancelled }
        val generatedTimeStr = formatGenerationTimestamp(generatedAtEpochMillis, trip.destinationTimezone)

        return when (options.format) {
            ExportFormat.PlainText -> buildPlainText(trip, days, activeItems, team, options, generatedTimeStr)
            ExportFormat.Markdown -> buildMarkdown(trip, days, activeItems, team, options, generatedTimeStr)
        }
    }

    private fun buildPlainText(
        trip: TripRecord,
        dayCount: Int,
        items: List<ItineraryItem>,
        team: List<TripMember>,
        options: ExportOptions,
        generatedTimeStr: String,
    ): String = buildString {
        appendLine(trip.title.trim())
        appendLine("=".repeat(trip.title.trim().length.coerceIn(12, 40)))
        appendLine("Destination : ${trip.destination.trim()}")
        appendLine("Dates       : ${trip.startDate} to ${trip.endDate} (${dayCount} days)")
        appendLine("Timezone    : ${trip.destinationTimezone}")
        appendLine("Generated   : $generatedTimeStr")

        if (options.includeMemberInitials && team.isNotEmpty()) {
            val activeMembers = team.filter { it.status == MembershipStatus.Active }
            val memberInitials = activeMembers.map { initials(it.displayName) }.filter { it.isNotBlank() }
            if (memberInitials.isNotEmpty()) {
                appendLine("Travelers   : ${memberInitials.joinToString(", ")} (${activeMembers.size} members)")
            }
        }

        appendLine()

        for (dayIndex in 0 until dayCount) {
            val date = dayDateAt(trip, dayIndex)
            val dayItems = items.filter { (it.dayDate ?: trip.startDate) == date }.sortedBy { it.position }

            appendLine("DAY ${dayIndex + 1} · ${weekday(date)} ${shortDate(date)}")
            appendLine("-".repeat(32))

            if (dayItems.isEmpty()) {
                appendLine("  (Free day / No scheduled stops)")
            } else {
                for (item in dayItems) {
                    if (item.type == ItineraryItemType.Lodging && !options.includeLodgingDetails) {
                        appendLine("  • Lodging (details withheld by export options)")
                        continue
                    }

                    val timePart = if (options.includeTimes) {
                        when {
                            !item.startTimeLabel.isNullOrBlank() -> "${item.startTimeLabel} (${item.durationMinutes}m): "
                            item.flexibleTime -> "Flexible (${item.durationMinutes}m): "
                            else -> ""
                        }
                    } else ""

                    appendLine("  • $timePart${item.title.trim()} [${item.type.label}]")

                    if (options.includePlaces && !item.place.isNullOrBlank()) {
                        appendLine("    Place: ${item.place.trim()}")
                    }
                    if (options.includeNotes && !item.note.isNullOrBlank()) {
                        appendLine("    Note: ${item.note.trim()}")
                    }
                }
            }
            appendLine()
        }

        appendLine("Exported via TripTandem · Privacy-safe shared itinerary")
    }

    private fun buildMarkdown(
        trip: TripRecord,
        dayCount: Int,
        items: List<ItineraryItem>,
        team: List<TripMember>,
        options: ExportOptions,
        generatedTimeStr: String,
    ): String = buildString {
        appendLine("# ${trip.title.trim()}")
        appendLine()
        appendLine("**Destination:** ${trip.destination.trim()}  ")
        appendLine("**Dates:** ${trip.startDate} – ${trip.endDate} (${dayCount} days)  ")
        appendLine("**Timezone:** ${trip.destinationTimezone}  ")
        appendLine("**Generated:** $generatedTimeStr  ")

        if (options.includeMemberInitials && team.isNotEmpty()) {
            val activeMembers = team.filter { it.status == MembershipStatus.Active }
            val memberInitials = activeMembers.map { initials(it.displayName) }.filter { it.isNotBlank() }
            if (memberInitials.isNotEmpty()) {
                appendLine("**Travelers:** ${memberInitials.joinToString(", ")} (${activeMembers.size} members)  ")
            }
        }

        appendLine()

        for (dayIndex in 0 until dayCount) {
            val date = dayDateAt(trip, dayIndex)
            val dayItems = items.filter { (it.dayDate ?: trip.startDate) == date }.sortedBy { it.position }

            appendLine("### Day ${dayIndex + 1} — ${weekday(date)}, ${shortDate(date)}")
            appendLine()

            if (dayItems.isEmpty()) {
                appendLine("_Free day / No scheduled stops_")
            } else {
                for (item in dayItems) {
                    if (item.type == ItineraryItemType.Lodging && !options.includeLodgingDetails) {
                        appendLine("- **Lodging** _(details withheld by export options)_")
                        continue
                    }

                    val timeBadge = if (options.includeTimes) {
                        when {
                            !item.startTimeLabel.isNullOrBlank() -> "`${item.startTimeLabel}` "
                            item.flexibleTime -> "`Flexible` "
                            else -> ""
                        }
                    } else ""

                    appendLine("- $timeBadge**${item.title.trim()}** _(${item.type.label}, ${item.durationMinutes}m)_")

                    if (options.includePlaces && !item.place.isNullOrBlank()) {
                        appendLine("  - **Place:** ${item.place.trim()}")
                    }
                    if (options.includeNotes && !item.note.isNullOrBlank()) {
                        appendLine("  - **Note:** ${item.note.trim()}")
                    }
                }
            }
            appendLine()
        }

        appendLine("---")
        appendLine("*Exported via TripTandem · Privacy-safe shared itinerary*")
    }

    private fun formatGenerationTimestamp(epochMillis: Long, timezone: String): String {
        return "timestamp: $epochMillis ($timezone)"
    }
}
