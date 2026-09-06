package com.example.triptandem

import com.triptandem.shared.validateTripDetails
import com.triptandem.shared.supportsAppleSignIn
import org.junit.Assert.*
import org.junit.Test

class TripFormValidationTest {
    @Test fun reportedMalformedDraftExplainsDatesAndTimezone() {
        val errors = validateTripDetails("tess", "indo", "2025", "2026", "UTCererer", "USD")
        assertEquals(setOf("dates", "destinationTimezone"), errors.keys)
        assertTrue(errors.values.all { it.isNotBlank() })
    }
    @Test fun rejectsImpossibleReversedAndOverlongDates() {
        for ((start, end) in listOf("2026-02-30" to "2026-03-03", "2026-09-21" to "2026-09-18", "2026-01-01" to "2026-03-02")) {
            assertTrue(validateTripDetails("Kyoto", "Japan", start, end, "Asia/Tokyo", "JPY").containsKey("dates"))
        }
    }
    @Test fun acceptsValidTripAndLeapDay() {
        assertTrue(validateTripDetails("Kyoto", "Japan", "2028-02-29", "2028-03-02", "Asia/Tokyo", "JPY").isEmpty())
        assertTrue(validateTripDetails("Bali", "Indonesia", "2026-09-18", "2026-09-21", "Asia/Makassar", "IDR").isEmpty())
    }
    @Test fun appleIsNotAvailableOnAndroid() { assertFalse(supportsAppleSignIn()) }
}
