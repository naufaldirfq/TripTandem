package com.example.triptandem

import com.triptandem.shared.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class OfflineCacheAndExportTest {

    private fun testTrip(id: String = "trip-1", ownerId: String = "user-1"): TripRecord {
        return TripRecord(
            id = id,
            ownerId = ownerId,
            title = "Kyoto Journey 伏見稲荷",
            destination = "Kyoto, Japan",
            startDate = "2026-10-12",
            endDate = "2026-10-14",
            destinationTimezone = "Asia/Tokyo",
            datesFlexible = false,
            visibility = TripVisibility.Private,
            capacity = 4,
            status = TripStatus.Confirmed,
            currency = "JPY",
            budgetBand = BudgetBand.Moderate,
            pace = TripPace.Balanced,
            expectationNote = "Cozy autumn trip",
            revision = 3,
            activeMemberCount = 2,
            interests = listOf("Food", "History"),
            coverColor = "#E8704A",
            createdAtEpochMillis = 1770000000000L,
            updatedAtEpochMillis = 1770001000000L,
        )
    }

    private fun testItems(tripId: String = "trip-1"): List<ItineraryItem> {
        return listOf(
            ItineraryItem(
                id = "item-1",
                tripId = tripId,
                type = ItineraryItemType.Activity,
                title = "Fushimi Inari Taisha 伏見稲荷大社",
                startTimeEpochMillis = null,
                startTimeLabel = "09:00",
                durationMinutes = 120,
                place = "Fushimi Ward",
                note = "Secret organizer note: bring 500 yen coins",
                status = ItineraryItemStatus.Planned,
                visibility = ItineraryVisibility.Members,
                position = 0,
                revision = 1,
                dayDate = "2026-10-12",
            ),
            ItineraryItem(
                id = "item-2",
                tripId = tripId,
                type = ItineraryItemType.Lodging,
                title = "Ryokan Gion",
                startTimeEpochMillis = null,
                startTimeLabel = "16:00",
                durationMinutes = 60,
                place = "Private street address #402, Gion",
                note = "Door access code: 1234*",
                status = ItineraryItemStatus.Booked,
                visibility = ItineraryVisibility.Members,
                position = 1,
                dayDate = "2026-10-12",
            ),
        )
    }

    private fun testMembers(tripId: String = "trip-1"): List<TripMember> {
        return listOf(
            TripMember(
                tripId = tripId,
                userId = "user-1",
                displayName = "Alex Doe",
                role = TripMemberRole.Owner,
                status = MembershipStatus.Active,
            ),
            TripMember(
                tripId = tripId,
                userId = "user-2",
                displayName = "Sam Traveler",
                role = TripMemberRole.Editor,
                status = MembershipStatus.Active,
            ),
        )
    }

    @Test
    fun testSaveAndRetrieveCachedTripBundle() = runTest {
        val storage = InMemorySecurePayloadStorage()
        val repo = StandardProtectedTripCacheRepository(storage)

        val bundle = CachedTripBundle(
            trip = testTrip(),
            items = testItems(),
            members = testMembers(),
            lastSyncEpochMillis = 1000000L,
            serverRevision = 3,
            userId = "user-1",
        )

        repo.saveTripBundle(bundle)

        val retrieved = repo.getCachedTripBundle("user-1", "trip-1")
        assertNotNull(retrieved)
        assertEquals("Kyoto Journey 伏見稲荷", retrieved?.trip?.title)
        assertEquals(3, retrieved?.serverRevision)
        assertEquals(2, retrieved?.items?.size)
        assertEquals(2, retrieved?.members?.size)

        val trips = repo.getCachedTrips("user-1")
        assertEquals(1, trips.size)
        assertEquals("trip-1", trips.first().id)
    }

    @Test
    fun testFreshnessBucketsAndStaleWindow() {
        val now = 10_000_000_000L
        assertEquals("<1h", OfflineCachePolicy.freshnessBucket(now - 30 * 60 * 1000L, now))
        assertEquals("1-24h", OfflineCachePolicy.freshnessBucket(now - 5 * 3600 * 1000L, now))
        assertEquals("1-7d", OfflineCachePolicy.freshnessBucket(now - 3 * 86400 * 1000L, now))
        assertEquals(">7d", OfflineCachePolicy.freshnessBucket(now - 10 * 86400 * 1000L, now))

        assertFalse(OfflineCachePolicy.isStale(now - 5 * 86400 * 1000L, now))
        assertTrue(OfflineCachePolicy.isStale(now - 8 * 86400 * 1000L, now))
    }

    @Test
    fun testCorruptionRecoveryDoesNotCrash() = runTest {
        val storage = InMemorySecurePayloadStorage()
        val repo = StandardProtectedTripCacheRepository(storage)

        // Write malformed JSON
        storage.write("trip_bundle:user-1:trip-broken", "not valid json {[[")
        storage.write("trip_index:user-1", "[\"trip-broken\"]")

        val bundle = repo.getCachedTripBundle("user-1", "trip-broken")
        assertNull(bundle)

        // Corrupted entry should be pruned
        assertNull(storage.read("trip_bundle:user-1:trip-broken"))
        val trips = repo.getCachedTrips("user-1")
        assertTrue(trips.isEmpty())
    }

    @Test
    fun testClearAllRemovesCachedData() = runTest {
        val storage = InMemorySecurePayloadStorage()
        val repo = StandardProtectedTripCacheRepository(storage)

        repo.saveTripBundle(
            CachedTripBundle(
                trip = testTrip("t1"),
                items = emptyList(),
                members = emptyList(),
                lastSyncEpochMillis = 1000L,
                serverRevision = 1,
                userId = "user-1",
            ),
        )
        repo.saveTripBundle(
            CachedTripBundle(
                trip = testTrip("t2"),
                items = emptyList(),
                members = emptyList(),
                lastSyncEpochMillis = 1000L,
                serverRevision = 1,
                userId = "user-2",
            ),
        )

        assertTrue(repo.getCacheSizeBytes("user-1") > 0)
        repo.clearAll("user-1", "sign_out")

        assertTrue(repo.getCachedTrips("user-1").isEmpty())
        assertEquals(1, repo.getCachedTrips("user-2").size)
    }

    @Test
    fun testExportExcludesSensitiveFieldsByDefault() {
        val trip = testTrip()
        val items = testItems()
        val members = testMembers()

        val defaultExport = ItineraryExporter.generateSummary(
            trip = trip,
            items = items,
            team = members,
            options = ExportOptions(),
        )

        // Default: No notes, no lodging details, no member names
        assertFalse(defaultExport.contains("bring 500 yen coins"))
        assertFalse(defaultExport.contains("1234*"))
        assertFalse(defaultExport.contains("Private street address"))
        assertFalse(defaultExport.contains("Alex Doe"))
        assertFalse(defaultExport.contains("Sam Traveler"))
        assertTrue(defaultExport.contains("Fushimi Inari Taisha 伏見稲荷大社"))
        assertTrue(defaultExport.contains("Lodging (details withheld by export options)"))
    }

    @Test
    fun testExportWithOptInFieldsIncludesNotesAndMembers() {
        val trip = testTrip()
        val items = testItems()
        val members = testMembers()

        val fullExport = ItineraryExporter.generateSummary(
            trip = trip,
            items = items,
            team = members,
            options = ExportOptions(
                includeTimes = true,
                includePlaces = true,
                includeNotes = true,
                includeLodgingDetails = true,
                includeMemberInitials = true,
            ),
        )

        assertTrue(fullExport.contains("bring 500 yen coins"))
        assertTrue(fullExport.contains("Ryokan Gion"))
        assertTrue(fullExport.contains("Private street address #402"))
        assertTrue(fullExport.contains("AD, ST")) // Initials only, no full PII/emails
        assertTrue(fullExport.contains("Asia/Tokyo"))
    }

    @Test
    fun testExportMarkdownFormat() {
        val trip = testTrip()
        val items = testItems()

        val markdown = ItineraryExporter.generateSummary(
            trip = trip,
            items = items,
            options = ExportOptions(format = ExportFormat.Markdown),
        )

        assertTrue(markdown.startsWith("# Kyoto Journey 伏見稲荷"))
        assertTrue(markdown.contains("### Day 1"))
        assertTrue(markdown.contains("**Destination:** Kyoto, Japan"))
    }
}
