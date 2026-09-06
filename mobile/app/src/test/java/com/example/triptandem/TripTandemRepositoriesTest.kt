package com.example.triptandem

import com.triptandem.shared.CreateItineraryItemInput
import com.triptandem.shared.CreateTripInput
import com.triptandem.shared.DataResult
import com.triptandem.shared.ItineraryItemType
import com.triptandem.shared.SaveTravelerProfileInput
import com.triptandem.shared.TripTandemError
import com.triptandem.shared.TripTandemRepositories
import com.triptandem.shared.TripPace
import com.triptandem.shared.BudgetBand
import com.triptandem.shared.ProfileVisibility
import com.triptandem.shared.TripRecord
import com.triptandem.shared.TripStatus
import com.triptandem.shared.TripVisibility
import com.triptandem.shared.activeOwnedTripCount
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripTandemRepositoriesTest {
    @Test
    fun activeTripAllowanceCountsOnlyTripsOwnedByTheOrganizer() {
        val trips = listOf(
            TripRecord(
                id = "owned",
                ownerId = "alex",
                title = "Owned plan",
                destination = "Kyoto",
                startDate = "2026-04-12",
                endDate = "2026-04-14",
                visibility = TripVisibility.Private,
                capacity = 6,
                status = TripStatus.Planning,
            ),
            TripRecord(
                id = "joined",
                ownerId = "maya",
                title = "Joined plan",
                destination = "Lisbon",
                startDate = "2026-05-23",
                endDate = "2026-05-26",
                visibility = TripVisibility.Unlisted,
                capacity = 6,
                status = TripStatus.Confirmed,
            ),
            TripRecord(
                id = "completed-owned",
                ownerId = "alex",
                title = "Past plan",
                destination = "Osaka",
                startDate = "2025-04-12",
                endDate = "2025-04-14",
                visibility = TripVisibility.Private,
                capacity = 6,
                status = TripStatus.Completed,
            ),
        )

        assertEquals(1, activeOwnedTripCount(trips, "alex"))
        assertEquals(1, activeOwnedTripCount(trips, "maya"))
    }

    @Test
    fun localStoreKeepsItineraryItemsOnTheirSelectedDay() = runTest {
        val repositories = TripTandemRepositories.local()
        repositories.profile.saveCurrentProfile(
            SaveTravelerProfileInput(displayName = "Alex", homeRegion = "Indonesia", ageConfirmed = true),
        )
        val trip = (repositories.trips.createTrip(
            CreateTripInput(
                title = "Kyoto",
                destination = "Kyoto, Japan",
                startDate = "2026-04-12",
                endDate = "2026-04-14",
            ),
        ) as DataResult.Success).value

        repositories.itinerary.createItem(
            trip.id,
            CreateItineraryItemInput(
                type = ItineraryItemType.Activity,
                title = "Fushimi Inari",
                startTimeEpochMillis = null,
                durationMinutes = 90,
                position = 0,
                dayDate = "2026-04-13",
            ),
        )

        val items = (repositories.itinerary.listItems(trip.id) as DataResult.Success).value
        assertEquals("2026-04-13", items.single().dayDate)
    }

    @Test
    fun localStoreAcceptsFlexibleLabelsUsedByGenerationPreview() = runTest {
        val repositories = TripTandemRepositories.local()
        repositories.profile.saveCurrentProfile(
            SaveTravelerProfileInput(displayName = "Alex", homeRegion = "Indonesia", ageConfirmed = true),
        )
        val trip = (repositories.trips.createTrip(
            CreateTripInput("Kyoto", "Kyoto", "2026-04-12", "2026-04-12"),
        ) as DataResult.Success).value

        val result = repositories.itinerary.createItem(
            trip.id,
            CreateItineraryItemInput(
                type = ItineraryItemType.Activity,
                title = "Flexible plan",
                startTimeEpochMillis = null,
                startTimeLabel = "late morning",
                flexibleTime = true,
                durationMinutes = 60,
                position = 0,
            ),
        )

        assertTrue(result is DataResult.Success)
    }

    @Test
    fun localStoreRejectsStaleTripAndItemWrites() = runTest {
        val repositories = TripTandemRepositories.local()
        repositories.profile.saveCurrentProfile(SaveTravelerProfileInput(displayName = "Alex", homeRegion = "Indonesia", ageConfirmed = true))
        val trip = (repositories.trips.createTrip(CreateTripInput("Kyoto", "Kyoto", "2026-04-12", "2026-04-12")) as DataResult.Success).value
        val updated = repositories.trips.updateTrip(
            trip.id,
            com.triptandem.shared.UpdateTripInput(
                title = trip.title,
                destination = trip.destination,
                startDate = trip.startDate,
                endDate = trip.endDate,
                visibility = trip.visibility,
                capacity = trip.capacity,
                status = trip.status,
                expectedRevision = trip.revision,
            ),
        ) as DataResult.Success
        assertEquals(1, updated.value.revision)
        val conflict = repositories.trips.updateTrip(
            trip.id,
            com.triptandem.shared.UpdateTripInput(
                title = "Stale",
                destination = trip.destination,
                startDate = trip.startDate,
                endDate = trip.endDate,
                visibility = trip.visibility,
                capacity = trip.capacity,
                status = trip.status,
                expectedRevision = trip.revision,
            ),
        )
        assertTrue(conflict is DataResult.Failure && conflict.error == TripTandemError.Conflict)
    }

    @Test
    fun localStorePersistsOptionalProfileSignalsAndConsentTimestamp() = runTest {
        val repositories = TripTandemRepositories.local()
        val result = repositories.profile.saveCurrentProfile(
            SaveTravelerProfileInput(
                displayName = "Alex",
                homeRegion = "Indonesia",
                primaryLanguage = "English",
                ageConfirmed = true,
                pace = TripPace.Slow,
                budgetBand = BudgetBand.Mid,
                visibility = ProfileVisibility.Connections,
                additionalLanguages = listOf("Bahasa Indonesia", "English", "English"),
                interests = listOf("Food", "Nature"),
            ),
        )

        assertTrue(result is DataResult.Success)
        val profile = (result as DataResult.Success).value
        assertEquals("Indonesia", profile.homeRegion)
        assertEquals(TripPace.Slow, profile.pace)
        assertEquals(BudgetBand.Mid, profile.budgetBand)
        assertEquals(ProfileVisibility.Connections, profile.visibility)
        assertEquals(listOf("Bahasa Indonesia", "English"), profile.additionalLanguages)
        assertNotNull(profile.consentedAtEpochMillis)
    }

    @Test
    fun localStoreSupportsUndoForSoftDeletedItineraryItem() = runTest {
        val repositories = TripTandemRepositories.local()
        repositories.profile.saveCurrentProfile(SaveTravelerProfileInput(displayName = "Alex", homeRegion = "Indonesia", ageConfirmed = true))
        val trip = (repositories.trips.createTrip(
            CreateTripInput("Kyoto", "Kyoto", "2026-04-12", "2026-04-12"),
        ) as DataResult.Success).value
        val item = (repositories.itinerary.createItem(
            trip.id,
            CreateItineraryItemInput(
                type = ItineraryItemType.Activity,
                title = "Fushimi Inari",
                startTimeEpochMillis = null,
                durationMinutes = 90,
                position = 0,
            ),
        ) as DataResult.Success).value

        assertTrue(repositories.itinerary.deleteItem(trip.id, item.id) is DataResult.Success)
        assertTrue((repositories.itinerary.listItems(trip.id) as DataResult.Success).value.isEmpty())
        val restored = repositories.itinerary.restoreItem(trip.id, item.id)
        assertTrue(restored is DataResult.Success)
        assertEquals(item.id, (restored as DataResult.Success).value.id)
        assertEquals(1, (repositories.itinerary.listItems(trip.id) as DataResult.Success).value.size)
    }

    @Test
    fun localStorePreventsTheSoleOwnerFromLeaving() = runTest {
        val repositories = TripTandemRepositories.local()
        repositories.profile.saveCurrentProfile(SaveTravelerProfileInput(displayName = "Alex", homeRegion = "Indonesia", ageConfirmed = true))
        val trip = (repositories.trips.createTrip(
            CreateTripInput("Kyoto", "Kyoto", "2026-04-12", "2026-04-12"),
        ) as DataResult.Success).value

        val result = repositories.members.leaveTrip(trip.id)

        assertTrue(result is DataResult.Failure)
        assertEquals(TripTandemError.Validation("ownership"), (result as DataResult.Failure).error)
    }
}
