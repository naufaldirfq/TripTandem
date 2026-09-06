package com.example.triptandem

import com.triptandem.shared.*
import org.junit.Assert.*
import org.junit.Test

class ScheduleAvailabilityTest {
    private val date = "2026-09-18"
    private val coffee = ItineraryItem("coffee", "trip", ItineraryItemType.Meal, "Coffee", 1L,
        startTimeLabel = "09:00", durationMinutes = 60, dayDate = date, flexibleTime = false)

    @Test fun detectsOverlapButAllowsAdjacentActivities() {
        assertEquals(listOf(coffee), scheduleConflicts(date, 570, 60, listOf(coffee)))
        assertTrue(scheduleConflicts(date, 600, 60, listOf(coffee)).isEmpty())
        assertTrue(scheduleConflicts(date, 480, 60, listOf(coffee)).isEmpty())
    }
    @Test fun ignoresOtherDaysFlexibleCancelledAndDeletedItems() {
        val ignored = listOf(coffee.copy(dayDate = "2026-09-19"), coffee.copy(flexibleTime = true),
            coffee.copy(status = ItineraryItemStatus.Cancelled), coffee.copy(deletedAtEpochMillis = 2L))
        assertTrue(scheduleConflicts(date, 540, 60, ignored).isEmpty())
    }
    @Test fun editingDoesNotConflictWithItself() {
        assertTrue(scheduleConflicts(date, 540, 60, listOf(coffee), "coffee").isEmpty())
        assertEquals(1, scheduleConflicts(date, 540, 60, listOf(coffee, coffee.copy(id = "other")), "coffee").size)
    }
    @Test fun pointInTimeNotesReserveTheirMinute() {
        val note = coffee.copy(durationMinutes = 0)
        assertEquals(listOf(note), scheduleConflicts(date, 540, 0, listOf(note)))
        assertTrue(scheduleConflicts(date, 541, 0, listOf(note)).isEmpty())
    }
}
