package com.triptandem.shared

/**
 * Domain-level failures exposed to shared UI code. Platform repositories map
 * Firebase/HTTP exceptions into this small, privacy-safe vocabulary.
 */
sealed interface TripTandemError {
    data object Unauthenticated : TripTandemError
    data object PermissionDenied : TripTandemError
    data object NotFound : TripTandemError
    data object Offline : TripTandemError
    data object Conflict : TripTandemError
    data object ReauthenticationRequired : TripTandemError
    data object Suspended : TripTandemError
    data object CapacityReached : TripTandemError
    data object InviteExpired : TripTandemError
    data object InviteRevoked : TripTandemError
    /** The user dismissed a provider's sign-in sheet or browser flow. */
    data object AuthCancelled : TripTandemError
    /** A provider credential is already attached to another Firebase UID. */
    data object AuthConflict : TripTandemError
    data object PurchaseCancelled : TripTandemError
    data object PurchasePending : TripTandemError
    data object PurchaseUnavailable : TripTandemError
    /** The server rejected a Pro-only action because no verified entitlement exists. */
    data object EntitlementRequired : TripTandemError
    /** The server rejected a generation because the fair-use allowance is spent. */
    data object QuotaExceeded : TripTandemError
    /** The generation worker returned a safe, user-actionable failure. */
    data class GenerationFailed(val failureClass: String? = null) : TripTandemError
    data class Validation(val field: String? = null) : TripTandemError
    data class Unknown(val causeType: String? = null) : TripTandemError
}

sealed interface DataResult<out T> {
    data class Success<T>(val value: T) : DataResult<T>
    data class Failure(val error: TripTandemError) : DataResult<Nothing>
}

/** Minimal cross-platform identity surface used to upgrade anonymous sessions. */
data class AuthSession(
    val uid: String,
    val isAnonymous: Boolean,
    val email: String? = null,
)

interface IdentityRepository {
    suspend fun currentSession(): DataResult<AuthSession?>
    suspend fun createOrLinkEmail(email: String, password: String): DataResult<AuthSession>
    suspend fun signInWithEmail(email: String, password: String): DataResult<AuthSession>
    suspend fun signInWithGoogle(): DataResult<AuthSession>
    suspend fun signInWithApple(): DataResult<AuthSession>
    suspend fun signOut(): DataResult<Unit>
}

object UnavailableIdentityRepository : IdentityRepository {
    override suspend fun currentSession(): DataResult<AuthSession?> = DataResult.Success(null)
    override suspend fun createOrLinkEmail(email: String, password: String): DataResult<AuthSession> = DataResult.Failure(TripTandemError.Unknown("identity_unavailable"))
    override suspend fun signInWithEmail(email: String, password: String): DataResult<AuthSession> = DataResult.Failure(TripTandemError.Unknown("identity_unavailable"))
    override suspend fun signInWithGoogle(): DataResult<AuthSession> = DataResult.Failure(TripTandemError.Unknown("identity_unavailable"))
    override suspend fun signInWithApple(): DataResult<AuthSession> = DataResult.Failure(TripTandemError.Unknown("identity_unavailable"))
    override suspend fun signOut(): DataResult<Unit> = DataResult.Success(Unit)
}

enum class ProfileVisibility(val wireValue: String) {
    Private("private"),
    Connections("connections"),
}

enum class AccountStatus(val wireValue: String) {
    Active("active"),
    Suspended("suspended"),
    Deleted("deleted"),
}

/** Policy identifiers are versioned so consent can be audited without storing document text. */
object PolicyVersions {
    const val TERMS = "2026-09-03"
    const val PRIVACY = "2026-09-03"
}

/** Controlled vocabulary used by the profile and trip style forms. */
object TripInterestVocabulary {
    val values = listOf(
        "Art & museums",
        "Food",
        "Nature",
        "Nightlife",
        "Photography",
        "Shopping",
        "Slow mornings",
        "Sports",
        "History",
    )
}

enum class TripVisibility(val wireValue: String) {
    Private("private"),
    Unlisted("unlisted"),
    Open("open"),
}

enum class TripStatus(val wireValue: String) {
    Draft("draft"),
    Planning("planning"),
    Confirmed("confirmed"),
    Completed("completed"),
    Cancelled("cancelled"),
    Archived("archived"),
}

/**
 * The labels and wire values intentionally follow PRD 01. The companion
 * aliases keep source compatibility with the first prototype, which used
 * `Slow`, `Fast`, `Mid`, and `Flexible` in tests and local previews.
 */
enum class TripPace(val wireValue: String, val label: String) {
    Relaxed("relaxed", "Relaxed"),
    Balanced("balanced", "Balanced"),
    Packed("packed", "Packed"),
    ;

    companion object {
        val Slow: TripPace get() = Relaxed
        val Fast: TripPace get() = Packed
    }
}

enum class BudgetBand(val wireValue: String, val label: String) {
    Budget("budget", "Budget"),
    Moderate("moderate", "Moderate"),
    Comfort("comfort", "Comfort"),
    Premium("premium", "Premium"),
    ;

    companion object {
        val Mid: BudgetBand get() = Moderate
        val Flexible: BudgetBand get() = Comfort
    }
}

enum class TripMemberRole(val wireValue: String) {
    Owner("owner"),
    Editor("editor"),
    Viewer("viewer"),
}

enum class MembershipStatus(val wireValue: String) {
    Invited("invited"),
    Active("active"),
    Removed("removed"),
}

enum class ItineraryItemType(val wireValue: String, val label: String) {
    Activity("activity", "Activity"),
    Transport("transport", "Transit"),
    Meal("meal", "Food"),
    Lodging("lodging", "Lodging"),
    Note("free", "Note"),
}

enum class ItineraryItemStatus(val wireValue: String, val label: String) {
    Idea("idea", "Idea"),
    Planned("planned", "Planned"),
    Booked("booked", "Booked"),
    Completed("completed", "Completed"),
    Cancelled("cancelled", "Cancelled"),
}

enum class ItineraryVisibility(val wireValue: String) {
    Members("members"),
    TripSummary("trip"),
}

/** User-owned profile fields. Public projections are intentionally separate. */
data class TravelerProfile(
    val uid: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val homeRegion: String? = null,
    val primaryLanguage: String = "English",
    val ageConfirmed: Boolean = false,
    val pace: TripPace? = null,
    val budgetBand: BudgetBand? = null,
    val visibility: ProfileVisibility = ProfileVisibility.Private,
    val additionalLanguages: List<String> = emptyList(),
    val interests: List<String> = emptyList(),
    val termsVersion: String = PolicyVersions.TERMS,
    val privacyVersion: String = PolicyVersions.PRIVACY,
    val consentedAtEpochMillis: Long? = null,
    val accountStatus: AccountStatus = AccountStatus.Active,
    val createdAtEpochMillis: Long? = null,
    val updatedAtEpochMillis: Long? = null,
)

data class SaveTravelerProfileInput(
    val displayName: String,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val homeRegion: String? = null,
    val primaryLanguage: String = "English",
    val ageConfirmed: Boolean,
    val pace: TripPace? = null,
    val budgetBand: BudgetBand? = null,
    val visibility: ProfileVisibility = ProfileVisibility.Private,
    val additionalLanguages: List<String> = emptyList(),
    val interests: List<String> = emptyList(),
    val termsVersion: String = PolicyVersions.TERMS,
    val privacyVersion: String = PolicyVersions.PRIVACY,
)

data class CreateTripInput(
    val title: String,
    val destination: String,
    val startDate: String,
    val endDate: String,
    val destinationTimezone: String = "UTC",
    val datesFlexible: Boolean = false,
    val visibility: TripVisibility = TripVisibility.Private,
    val capacity: Int = 4,
    val status: TripStatus = TripStatus.Planning,
    val currency: String? = null,
    val budgetBand: BudgetBand? = null,
    val pace: TripPace? = null,
    val expectationNote: String? = null,
    val interests: List<String> = emptyList(),
    val coverColor: String? = null,
)

/** Draft fields are intentionally separate from a server TripRecord. They
 * are safe to keep locally before the user explicitly creates a trip. */
data class TripDraftSnapshot(
    val title: String = "",
    val destination: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val destinationTimezone: String = "UTC",
    val datesFlexible: Boolean = false,
    val visibility: TripVisibility = TripVisibility.Private,
    val capacity: Int = 4,
    val pace: TripPace = TripPace.Balanced,
    val budgetBand: BudgetBand = BudgetBand.Moderate,
    val currency: String = "USD",
    val expectationNote: String = "",
    val interests: List<String> = emptyList(),
    val coverColor: String = "#E8704A",
)

interface TripDraftRepository {
    fun load(): TripDraftSnapshot?
    fun save(snapshot: TripDraftSnapshot)
    fun clear()
}

object NoOpTripDraftRepository : TripDraftRepository {
    override fun load(): TripDraftSnapshot? = null
    override fun save(snapshot: TripDraftSnapshot) = Unit
    override fun clear() = Unit
}

data class UpdateTripInput(
    val title: String,
    val destination: String,
    val startDate: String,
    val endDate: String,
    val destinationTimezone: String = "UTC",
    val datesFlexible: Boolean = false,
    val visibility: TripVisibility,
    val capacity: Int,
    val status: TripStatus,
    val currency: String? = null,
    val budgetBand: BudgetBand? = null,
    val pace: TripPace? = null,
    val expectationNote: String? = null,
    val expectedRevision: Int,
    val interests: List<String> = emptyList(),
    val coverColor: String? = null,
)

data class TripRecord(
    val id: String,
    val ownerId: String,
    val title: String,
    val destination: String,
    val startDate: String,
    val endDate: String,
    val destinationTimezone: String = "UTC",
    val datesFlexible: Boolean = false,
    val visibility: TripVisibility,
    val capacity: Int,
    val status: TripStatus,
    val currency: String? = null,
    val budgetBand: BudgetBand? = null,
    val pace: TripPace? = null,
    val expectationNote: String? = null,
    val revision: Int = 0,
    val activeMemberCount: Int = 1,
    val interests: List<String> = emptyList(),
    val coverColor: String? = null,
    val createdAtEpochMillis: Long? = null,
    val updatedAtEpochMillis: Long? = null,
)

/**
 * Counts only active trips owned by [ownerId] for organizer-only limits.
 * Membership in somebody else's trip must never consume the owner's Free
 * plan allowance (PRD 10, Organizer-funded trip behavior).
 */
fun activeOwnedTripCount(trips: Iterable<TripRecord>, ownerId: String): Int =
    trips.count { trip ->
        trip.ownerId == ownerId && trip.status in setOf(
            TripStatus.Draft,
            TripStatus.Planning,
            TripStatus.Confirmed,
        )
    }

data class TripMember(
    val tripId: String,
    val userId: String,
    val displayName: String,
    val role: TripMemberRole,
    val status: MembershipStatus,
    val inviteId: String? = null,
    val joinedAtEpochMillis: Long? = null,
    val updatedAtEpochMillis: Long? = null,
)

data class CreateInviteInput(
    val role: TripMemberRole,
    val expiresInHours: Int = 24 * 7,
    val maxUses: Int = 1,
)

data class InviteLink(
    val id: String,
    val tripId: String,
    val role: TripMemberRole,
    val expiresAtEpochMillis: Long,
    val maxUses: Int,
    val uses: Int = 0,
    val revoked: Boolean = false,
    /** Raw token is returned once for sharing and is never persisted. */
    val shareToken: String,
    val createdAtEpochMillis: Long? = null,
    /** Safe, denormalized preview fields shown before membership is accepted. */
    val tripTitle: String? = null,
    val destination: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val inviterDisplayName: String? = null,
)

/** A sanitized deep-link payload kept in memory until authentication/acceptance. */
data class PendingInvite(
    val tripId: String,
    val shareToken: String,
)

data class ItineraryItem(
    val id: String,
    val tripId: String,
    val type: ItineraryItemType,
    val title: String,
    val startTimeEpochMillis: Long?,
    val startTimeLabel: String? = null,
    val durationMinutes: Int,
    val place: String? = null,
    val note: String? = null,
    val status: ItineraryItemStatus = ItineraryItemStatus.Planned,
    val visibility: ItineraryVisibility = ItineraryVisibility.Members,
    val position: Int = 0,
    val revision: Int = 0,
    val lastEditedBy: String? = null,
    val updatedAtEpochMillis: Long? = null,
    /** ISO date in the destination timezone. Null is treated as the trip start day for legacy data. */
    val dayDate: String? = null,
    /** Set during the 30-second undo window; deleted items are excluded from normal reads. */
    val deletedAtEpochMillis: Long? = null,
    val undoExpiresAtEpochMillis: Long? = null,
    /** True when the item has no fixed destination-local clock time. */
    val flexibleTime: Boolean = startTimeEpochMillis == null,
    /** Non-null when the item was created from an explicitly applied AI draft. */
    val generatedBy: String? = null,
    val generationJobId: String? = null,
)

data class CreateItineraryItemInput(
    val type: ItineraryItemType,
    val title: String,
    val startTimeEpochMillis: Long?,
    val startTimeLabel: String? = null,
    val durationMinutes: Int,
    val place: String? = null,
    val note: String? = null,
    val status: ItineraryItemStatus = ItineraryItemStatus.Planned,
    val visibility: ItineraryVisibility = ItineraryVisibility.Members,
    val position: Int,
    val dayDate: String? = null,
    val undoExpiresAtEpochMillis: Long? = null,
    /** Flexible items may use labels such as "morning" instead of a clock time. */
    val flexibleTime: Boolean = startTimeEpochMillis == null,
)

data class UpdateItineraryItemInput(
    val type: ItineraryItemType,
    val title: String,
    val startTimeEpochMillis: Long?,
    val startTimeLabel: String? = null,
    val durationMinutes: Int,
    val place: String? = null,
    val note: String? = null,
    val status: ItineraryItemStatus,
    val visibility: ItineraryVisibility,
    val position: Int,
    val expectedRevision: Int,
    val dayDate: String? = null,
    val undoExpiresAtEpochMillis: Long? = null,
    /** Flexible items may use labels such as "morning" instead of a clock time. */
    val flexibleTime: Boolean = startTimeEpochMillis == null,
)

/** Shared persistence boundaries; platform implementations are injected by the app shell. */
interface TravelerProfileRepository {
    suspend fun getCurrentProfile(): DataResult<TravelerProfile?>
    suspend fun saveCurrentProfile(input: SaveTravelerProfileInput): DataResult<TravelerProfile>
    suspend fun deleteCurrentAccount(): DataResult<Unit>
}

interface TripRepository {
    suspend fun listMyTrips(): DataResult<List<TripRecord>>
    suspend fun createTrip(input: CreateTripInput): DataResult<TripRecord>
    suspend fun getTrip(tripId: String): DataResult<TripRecord>
    suspend fun updateTrip(tripId: String, input: UpdateTripInput): DataResult<TripRecord>
    suspend fun deleteTrip(tripId: String): DataResult<Unit>
}

interface ItineraryRepository {
    suspend fun listItems(tripId: String): DataResult<List<ItineraryItem>>
    suspend fun createItem(tripId: String, input: CreateItineraryItemInput): DataResult<ItineraryItem>
    suspend fun updateItem(tripId: String, itemId: String, input: UpdateItineraryItemInput): DataResult<ItineraryItem>
    suspend fun deleteItem(tripId: String, itemId: String): DataResult<Unit>
    suspend fun restoreItem(tripId: String, itemId: String): DataResult<ItineraryItem>
}

interface TripMemberRepository {
    suspend fun listMembers(tripId: String): DataResult<List<TripMember>>
    suspend fun getCurrentMember(tripId: String): DataResult<TripMember?>
    suspend fun updateRole(tripId: String, userId: String, role: TripMemberRole): DataResult<TripMember>
    suspend fun removeMember(tripId: String, userId: String): DataResult<Unit>
    suspend fun leaveTrip(tripId: String): DataResult<Unit>
    suspend fun transferOwnership(tripId: String, newOwnerId: String): DataResult<Unit>
}

interface InviteRepository {
    suspend fun createInvite(tripId: String, input: CreateInviteInput): DataResult<InviteLink>
    suspend fun revokeInvite(tripId: String, inviteId: String): DataResult<Unit>
    suspend fun resolveInvite(tripId: String, shareToken: String): DataResult<InviteLink>
    suspend fun acceptInvite(tripId: String, inviteId: String, role: TripMemberRole): DataResult<TripMember>
}

/** The complete Phase 1 dependency bundle. */
data class TripTandemRepositories(
    val profile: TravelerProfileRepository,
    val trips: TripRepository,
    val itinerary: ItineraryRepository,
    val members: TripMemberRepository,
    val invites: InviteRepository,
    val identity: IdentityRepository = UnavailableIdentityRepository,
    val tripDrafts: TripDraftRepository = NoOpTripDraftRepository,
    val generation: ItineraryGenerationRepository = UnavailableItineraryGenerationRepository,
    val generationDrafts: ItineraryGenerationDraftRepository = NoOpItineraryGenerationDraftRepository,
) {
    companion object {
        fun local(): TripTandemRepositories {
            val store = InMemoryTripTandemStore()
            return TripTandemRepositories(store, store, store, store, store)
        }
    }
}

/** Local store for previews and deterministic unit tests. */
private class InMemoryTripTandemStore :
    TravelerProfileRepository,
    TripRepository,
    ItineraryRepository,
    TripMemberRepository,
    InviteRepository {
    private val userId = "local-user"
    private var profile: TravelerProfile? = null
    private var sequence = 0
    private val trips = linkedMapOf<String, TripRecord>()
    private val members = linkedMapOf<String, MutableList<TripMember>>()
    private val items = linkedMapOf<String, MutableList<ItineraryItem>>()
    private val invites = linkedMapOf<String, InviteLink>()

    override suspend fun getCurrentProfile(): DataResult<TravelerProfile?> = DataResult.Success(profile)

    override suspend fun saveCurrentProfile(input: SaveTravelerProfileInput): DataResult<TravelerProfile> {
        if (input.displayName.trim().length !in 2..40) return DataResult.Failure(TripTandemError.Validation("displayName"))
        if (!input.ageConfirmed) return DataResult.Failure(TripTandemError.Validation("ageConfirmed"))
        if ((input.bio?.length ?: 0) > 240) return DataResult.Failure(TripTandemError.Validation("bio"))
        if (input.homeRegion.isNullOrBlank() || input.homeRegion.trim().length !in 1..80) return DataResult.Failure(TripTandemError.Validation("homeRegion"))
        if (input.primaryLanguage.trim().length !in 2..80) return DataResult.Failure(TripTandemError.Validation("primaryLanguage"))
        if (input.termsVersion.isBlank() || input.privacyVersion.isBlank()) return DataResult.Failure(TripTandemError.Validation("consent"))
        if (input.additionalLanguages.size > 5 || input.interests.size > 12) return DataResult.Failure(TripTandemError.Validation("interests"))
        sequence++
        val now = currentEpochMillis()
        val saved = TravelerProfile(
            uid = userId,
            displayName = input.displayName.trim(),
            avatarUrl = input.avatarUrl,
            bio = input.bio,
            homeRegion = input.homeRegion,
            primaryLanguage = input.primaryLanguage,
            ageConfirmed = input.ageConfirmed,
            pace = input.pace,
            budgetBand = input.budgetBand,
            visibility = input.visibility,
            additionalLanguages = input.additionalLanguages.distinct().take(5),
            interests = input.interests.distinct().take(12),
            termsVersion = input.termsVersion,
            privacyVersion = input.privacyVersion,
            consentedAtEpochMillis = now,
            accountStatus = profile?.accountStatus ?: AccountStatus.Active,
            createdAtEpochMillis = profile?.createdAtEpochMillis ?: now,
            updatedAtEpochMillis = now,
        )
        profile = saved
        return DataResult.Success(saved)
    }

    override suspend fun deleteCurrentAccount(): DataResult<Unit> {
        trips.values.filter { it.ownerId == userId }.map { it.id }.forEach { tripId ->
            trips.remove(tripId)
            members.remove(tripId)
            items.remove(tripId)
            invites.values.removeAll { it.tripId == tripId }
        }
        members.values.forEach { list -> list.removeAll { it.userId == userId } }
        profile = null
        return DataResult.Success(Unit)
    }

    override suspend fun listMyTrips(): DataResult<List<TripRecord>> =
        DataResult.Success(trips.values.filter { trip ->
            trip.ownerId == userId || members[trip.id].orEmpty().any { it.userId == userId && it.status == MembershipStatus.Active }
        })

    override suspend fun createTrip(input: CreateTripInput): DataResult<TripRecord> {
        // The production Firebase adapter enforces this in a trusted
        // transaction. Keep local previews conservative so they do not
        // demonstrate a flow that would be rejected for a free organizer.
        if (trips.values.count {
                it.ownerId == userId &&
                    it.status in setOf(TripStatus.Draft, TripStatus.Planning, TripStatus.Confirmed)
            } >= 1
        ) return DataResult.Failure(TripTandemError.EntitlementRequired)
        if (input.title.trim().length !in 2..120) return DataResult.Failure(TripTandemError.Validation("title"))
        if (input.destination.trim().length !in 1..160) return DataResult.Failure(TripTandemError.Validation("destination"))
        if (!validIsoDate(input.startDate) || !validIsoDate(input.endDate) || input.startDate > input.endDate || tripLengthInDays(input.startDate, input.endDate) > 60) {
            return DataResult.Failure(TripTandemError.Validation("dates"))
        }
        if (input.capacity !in 2..12) return DataResult.Failure(TripTandemError.Validation("capacity"))
        if (input.capacity > 6) return DataResult.Failure(TripTandemError.EntitlementRequired)
        if (input.destinationTimezone.trim().length !in 1..80) return DataResult.Failure(TripTandemError.Validation("destinationTimezone"))
        if (input.currency?.matches(Regex("[A-Z]{3}")) == false) return DataResult.Failure(TripTandemError.Validation("currency"))
        if ((input.expectationNote?.length ?: 0) > 500) return DataResult.Failure(TripTandemError.Validation("expectationNote"))
        if (input.interests.size > 12) return DataResult.Failure(TripTandemError.Validation("interests"))
        if (input.coverColor?.matches(Regex("#[0-9A-Fa-f]{6}")) == false) return DataResult.Failure(TripTandemError.Validation("coverColor"))
        val id = "local-trip-${++sequence}"
        val now = currentEpochMillis()
        val trip = TripRecord(
            id = id,
            ownerId = userId,
            title = input.title.trim(),
            destination = input.destination.trim(),
            startDate = input.startDate,
            endDate = input.endDate,
            destinationTimezone = input.destinationTimezone,
            datesFlexible = input.datesFlexible,
            visibility = input.visibility,
            capacity = input.capacity,
            status = input.status,
            currency = input.currency,
            budgetBand = input.budgetBand,
            pace = input.pace,
            expectationNote = input.expectationNote,
            revision = 0,
            activeMemberCount = 1,
            interests = input.interests.distinct().take(12),
            coverColor = input.coverColor,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )
        trips[id] = trip
        members[id] = mutableListOf(
            TripMember(id, userId, profile?.displayName ?: "You", TripMemberRole.Owner, MembershipStatus.Active, inviteId = null, joinedAtEpochMillis = now, updatedAtEpochMillis = now),
        )
        items[id] = mutableListOf()
        return DataResult.Success(trip)
    }

    override suspend fun getTrip(tripId: String): DataResult<TripRecord> =
        trips[tripId]?.let { DataResult.Success(it) } ?: DataResult.Failure(TripTandemError.NotFound)

    override suspend fun updateTrip(tripId: String, input: UpdateTripInput): DataResult<TripRecord> {
        val current = trips[tripId] ?: return DataResult.Failure(TripTandemError.NotFound)
        if (current.revision != input.expectedRevision) return DataResult.Failure(TripTandemError.Conflict)
        if (input.title.trim().length !in 2..120) return DataResult.Failure(TripTandemError.Validation("title"))
        if (input.destination.trim().length !in 1..160) return DataResult.Failure(TripTandemError.Validation("destination"))
        if (!validIsoDate(input.startDate) || !validIsoDate(input.endDate) || input.startDate > input.endDate || tripLengthInDays(input.startDate, input.endDate) > 60) {
            return DataResult.Failure(TripTandemError.Validation("dates"))
        }
        if (input.capacity !in 2..12) return DataResult.Failure(TripTandemError.Validation("capacity"))
        val activeStatuses = setOf(TripStatus.Draft, TripStatus.Planning, TripStatus.Confirmed)
        val reactivating = current.status !in activeStatuses && input.status in activeStatuses
        // Keep existing Pro-sized trips editable after expiry, while blocking
        // capacity changes and Pro-sized inactive-to-active reactivation on the
        // free path. The local preview also preserves the one-active-trip cap.
        if (input.capacity > 6 && (input.capacity != current.capacity || reactivating)) {
            return DataResult.Failure(TripTandemError.EntitlementRequired)
        }
        if (reactivating && trips.values.any {
                it.id != tripId && it.ownerId == userId && it.status in activeStatuses
            }
        ) {
            return DataResult.Failure(TripTandemError.EntitlementRequired)
        }
        if (input.destinationTimezone.trim().length !in 1..80) return DataResult.Failure(TripTandemError.Validation("destinationTimezone"))
        if (input.currency?.matches(Regex("[A-Z]{3}")) == false) return DataResult.Failure(TripTandemError.Validation("currency"))
        if ((input.expectationNote?.length ?: 0) > 500) return DataResult.Failure(TripTandemError.Validation("expectationNote"))
        if (input.interests.size > 12) return DataResult.Failure(TripTandemError.Validation("interests"))
        if (input.coverColor?.matches(Regex("#[0-9A-Fa-f]{6}")) == false) return DataResult.Failure(TripTandemError.Validation("coverColor"))
        val next = current.copy(
            title = input.title.trim(), destination = input.destination.trim(), startDate = input.startDate,
            endDate = input.endDate, destinationTimezone = input.destinationTimezone, datesFlexible = input.datesFlexible,
            visibility = input.visibility, capacity = input.capacity, status = input.status, currency = input.currency,
            budgetBand = input.budgetBand, pace = input.pace, expectationNote = input.expectationNote,
            interests = input.interests.distinct().take(12), coverColor = input.coverColor,
            revision = current.revision + 1, updatedAtEpochMillis = currentEpochMillis(),
        )
        trips[tripId] = next
        return DataResult.Success(next)
    }

    override suspend fun deleteTrip(tripId: String): DataResult<Unit> {
        if (!trips.containsKey(tripId)) return DataResult.Failure(TripTandemError.NotFound)
        trips.remove(tripId)
        members.remove(tripId)
        items.remove(tripId)
        invites.values.removeAll { it.tripId == tripId }
        return DataResult.Success(Unit)
    }

    override suspend fun listItems(tripId: String): DataResult<List<ItineraryItem>> =
        DataResult.Success(items[tripId].orEmpty().filter { it.deletedAtEpochMillis == null }.sortedBy { it.position })

    override suspend fun createItem(tripId: String, input: CreateItineraryItemInput): DataResult<ItineraryItem> {
        if (!trips.containsKey(tripId)) return DataResult.Failure(TripTandemError.NotFound)
        if (input.title.trim().length !in 1..160) return DataResult.Failure(TripTandemError.Validation("title"))
        if (input.durationMinutes !in 0..1440) return DataResult.Failure(TripTandemError.Validation("durationMinutes"))
        if ((input.startTimeLabel?.length ?: 0) > 32) return DataResult.Failure(TripTandemError.Validation("startTimeLabel"))
        if ((input.place?.length ?: 0) > 200) return DataResult.Failure(TripTandemError.Validation("place"))
        if ((input.note?.length ?: 0) > 1000) return DataResult.Failure(TripTandemError.Validation("note"))
        val id = "local-item-${++sequence}"
        val now = currentEpochMillis()
        val item = ItineraryItem(
            id = id,
            tripId = tripId,
            type = input.type,
            title = input.title.trim(),
            startTimeEpochMillis = input.startTimeEpochMillis,
            startTimeLabel = input.startTimeLabel,
            durationMinutes = input.durationMinutes,
            place = input.place,
            note = input.note,
            status = input.status,
            visibility = input.visibility,
            position = input.position,
            revision = 0,
            lastEditedBy = userId,
            updatedAtEpochMillis = now,
            dayDate = input.dayDate,
            deletedAtEpochMillis = null,
            undoExpiresAtEpochMillis = null,
            flexibleTime = input.flexibleTime,
        )
        items.getOrPut(tripId) { mutableListOf() }.add(item)
        return DataResult.Success(item)
    }

    override suspend fun updateItem(tripId: String, itemId: String, input: UpdateItineraryItemInput): DataResult<ItineraryItem> {
        val list = items[tripId] ?: return DataResult.Failure(TripTandemError.NotFound)
        val index = list.indexOfFirst { it.id == itemId }
        if (index < 0) return DataResult.Failure(TripTandemError.NotFound)
        val current = list[index]
        if (current.deletedAtEpochMillis != null) return DataResult.Failure(TripTandemError.NotFound)
        if (current.revision != input.expectedRevision) return DataResult.Failure(TripTandemError.Conflict)
        if (input.title.trim().length !in 1..160) return DataResult.Failure(TripTandemError.Validation("title"))
        if (input.durationMinutes !in 0..1440) return DataResult.Failure(TripTandemError.Validation("durationMinutes"))
        if (input.position !in 0..500) return DataResult.Failure(TripTandemError.Validation("position"))
        if ((input.startTimeLabel?.length ?: 0) > 32) return DataResult.Failure(TripTandemError.Validation("startTimeLabel"))
        if ((input.place?.length ?: 0) > 200) return DataResult.Failure(TripTandemError.Validation("place"))
        if ((input.note?.length ?: 0) > 1000) return DataResult.Failure(TripTandemError.Validation("note"))
        val next = current.copy(type = input.type, title = input.title.trim(), startTimeEpochMillis = input.startTimeEpochMillis, startTimeLabel = input.startTimeLabel,
            durationMinutes = input.durationMinutes, place = input.place, note = input.note, status = input.status,
            visibility = input.visibility, position = input.position, revision = current.revision + 1,
            lastEditedBy = userId, updatedAtEpochMillis = currentEpochMillis(), dayDate = input.dayDate,
            flexibleTime = input.flexibleTime)
        list[index] = next
        return DataResult.Success(next)
    }

    override suspend fun deleteItem(tripId: String, itemId: String): DataResult<Unit> {
        val list = items[tripId] ?: return DataResult.Failure(TripTandemError.NotFound)
        val index = list.indexOfFirst { it.id == itemId }
        if (index < 0) return DataResult.Failure(TripTandemError.NotFound)
        val current = list[index]
        if (current.deletedAtEpochMillis != null) return DataResult.Failure(TripTandemError.NotFound)
        val now = currentEpochMillis()
        list[index] = current.copy(
            deletedAtEpochMillis = now,
            undoExpiresAtEpochMillis = now + 30_000L,
            revision = current.revision + 1,
            updatedAtEpochMillis = now,
        )
        return DataResult.Success(Unit)
    }

    override suspend fun restoreItem(tripId: String, itemId: String): DataResult<ItineraryItem> {
        val list = items[tripId] ?: return DataResult.Failure(TripTandemError.NotFound)
        val index = list.indexOfFirst { it.id == itemId }
        if (index < 0) return DataResult.Failure(TripTandemError.NotFound)
        val current = list[index]
        val now = currentEpochMillis()
        if (current.deletedAtEpochMillis == null) return DataResult.Success(current)
        if (current.undoExpiresAtEpochMillis != null && current.undoExpiresAtEpochMillis < now) {
            return DataResult.Failure(TripTandemError.NotFound)
        }
        val restored = current.copy(
            deletedAtEpochMillis = null,
            undoExpiresAtEpochMillis = null,
            revision = current.revision + 1,
            updatedAtEpochMillis = currentEpochMillis(),
        )
        list[index] = restored
        return DataResult.Success(restored)
    }

    override suspend fun listMembers(tripId: String): DataResult<List<TripMember>> =
        DataResult.Success(members[tripId].orEmpty().toList())

    override suspend fun getCurrentMember(tripId: String): DataResult<TripMember?> =
        DataResult.Success(members[tripId].orEmpty().firstOrNull { it.userId == userId })

    override suspend fun updateRole(tripId: String, userId: String, role: TripMemberRole): DataResult<TripMember> {
        val list = members[tripId] ?: return DataResult.Failure(TripTandemError.NotFound)
        val index = list.indexOfFirst { it.userId == userId }
        if (index < 0) return DataResult.Failure(TripTandemError.NotFound)
        if (list[index].role == TripMemberRole.Owner || role == TripMemberRole.Owner) return DataResult.Failure(TripTandemError.PermissionDenied)
        val next = list[index].copy(role = role)
        list[index] = next
        return DataResult.Success(next)
    }

    override suspend fun removeMember(tripId: String, userId: String): DataResult<Unit> {
        val list = members[tripId] ?: return DataResult.Failure(TripTandemError.NotFound)
        val index = list.indexOfFirst { it.userId == userId }
        if (index < 0) return DataResult.Failure(TripTandemError.NotFound)
        if (list[index].role == TripMemberRole.Owner) return DataResult.Failure(TripTandemError.PermissionDenied)
        val now = currentEpochMillis()
        if (list[index].status == MembershipStatus.Active) {
            trips[tripId]?.let {
                trips[tripId] = it.copy(
                    activeMemberCount = (it.activeMemberCount - 1).coerceAtLeast(1),
                    revision = it.revision + 1,
                    updatedAtEpochMillis = now,
                )
            }
        }
        list[index] = list[index].copy(status = MembershipStatus.Removed, updatedAtEpochMillis = now)
        return DataResult.Success(Unit)
    }

    override suspend fun leaveTrip(tripId: String): DataResult<Unit> {
        val list = members[tripId] ?: return DataResult.Failure(TripTandemError.NotFound)
        val index = list.indexOfFirst { it.userId == userId && it.status == MembershipStatus.Active }
        if (index < 0) return DataResult.Failure(TripTandemError.NotFound)
        if (list[index].role == TripMemberRole.Owner) return DataResult.Failure(TripTandemError.Validation("ownership"))
        val now = currentEpochMillis()
        list[index] = list[index].copy(status = MembershipStatus.Removed, updatedAtEpochMillis = now)
        trips[tripId]?.let {
            trips[tripId] = it.copy(
                activeMemberCount = (it.activeMemberCount - 1).coerceAtLeast(1),
                revision = it.revision + 1,
                updatedAtEpochMillis = now,
            )
        }
        return DataResult.Success(Unit)
    }

    override suspend fun transferOwnership(tripId: String, newOwnerId: String): DataResult<Unit> {
        val list = members[tripId] ?: return DataResult.Failure(TripTandemError.NotFound)
        val ownerIndex = list.indexOfFirst { it.userId == userId && it.role == TripMemberRole.Owner && it.status == MembershipStatus.Active }
        val recipientIndex = list.indexOfFirst { it.userId == newOwnerId && it.status == MembershipStatus.Active }
        if (ownerIndex < 0 || recipientIndex < 0 || newOwnerId == userId) return DataResult.Failure(TripTandemError.PermissionDenied)
        list[ownerIndex] = list[ownerIndex].copy(role = TripMemberRole.Editor, updatedAtEpochMillis = currentEpochMillis())
        list[recipientIndex] = list[recipientIndex].copy(role = TripMemberRole.Owner, updatedAtEpochMillis = currentEpochMillis())
        trips[tripId]?.let { trips[tripId] = it.copy(ownerId = newOwnerId, revision = it.revision + 1, updatedAtEpochMillis = currentEpochMillis()) }
        return DataResult.Success(Unit)
    }

    override suspend fun createInvite(tripId: String, input: CreateInviteInput): DataResult<InviteLink> {
        if (!trips.containsKey(tripId)) return DataResult.Failure(TripTandemError.NotFound)
        if (input.role == TripMemberRole.Owner || input.expiresInHours !in 1..(24 * 30) || input.maxUses !in 1..20) {
            return DataResult.Failure(TripTandemError.Validation("invite"))
        }
        val id = "local-invite-${++sequence}"
        val token = "local-token-$sequence"
        val trip = trips[tripId] ?: return DataResult.Failure(TripTandemError.NotFound)
        val now = currentEpochMillis()
        val invite = InviteLink(
            id = id,
            tripId = tripId,
            role = input.role,
            expiresAtEpochMillis = now + input.expiresInHours * 3_600_000L,
            maxUses = input.maxUses,
            shareToken = token,
            createdAtEpochMillis = now,
            tripTitle = trip.title,
            destination = trip.destination,
            startDate = trip.startDate,
            endDate = trip.endDate,
            inviterDisplayName = profile?.displayName ?: "Trip host",
        )
        invites[id] = invite
        return DataResult.Success(invite)
    }

    override suspend fun revokeInvite(tripId: String, inviteId: String): DataResult<Unit> {
        invites[inviteId] = invites[inviteId]?.copy(revoked = true) ?: return DataResult.Failure(TripTandemError.NotFound)
        return DataResult.Success(Unit)
    }

    override suspend fun resolveInvite(tripId: String, shareToken: String): DataResult<InviteLink> =
        invites.values.firstOrNull { it.tripId == tripId && it.shareToken == shareToken }
            ?.let { invite ->
                when {
                    invite.revoked -> DataResult.Failure(TripTandemError.InviteRevoked)
                    invite.expiresAtEpochMillis <= currentEpochMillis() -> DataResult.Failure(TripTandemError.InviteExpired)
                    invite.uses >= invite.maxUses -> DataResult.Failure(TripTandemError.CapacityReached)
                    else -> DataResult.Success(invite)
                }
            } ?: DataResult.Failure(TripTandemError.NotFound)

    override suspend fun acceptInvite(tripId: String, inviteId: String, role: TripMemberRole): DataResult<TripMember> {
        if (role == TripMemberRole.Owner) return DataResult.Failure(TripTandemError.Validation("role"))
        val invite = invites[inviteId] ?: return DataResult.Failure(TripTandemError.NotFound)
        if (invite.revoked) return DataResult.Failure(TripTandemError.InviteRevoked)
        if (invite.expiresAtEpochMillis <= currentEpochMillis()) return DataResult.Failure(TripTandemError.InviteExpired)
        if (invite.role != role) return DataResult.Failure(TripTandemError.PermissionDenied)
        if (invite.uses >= invite.maxUses) return DataResult.Failure(TripTandemError.CapacityReached)
        val trip = trips[tripId] ?: return DataResult.Failure(TripTandemError.NotFound)
        val existing = members[tripId].orEmpty().firstOrNull { it.userId == userId && it.status == MembershipStatus.Active }
        if (existing != null) return DataResult.Success(existing)
        if (trip.activeMemberCount >= trip.capacity) return DataResult.Failure(TripTandemError.CapacityReached)
        val now = currentEpochMillis()
        val member = TripMember(tripId, userId, profile?.displayName ?: "Traveler", role, MembershipStatus.Active, inviteId = inviteId, joinedAtEpochMillis = now, updatedAtEpochMillis = now)
        members.getOrPut(tripId) { mutableListOf() }.removeAll { it.userId == userId }
        members.getOrPut(tripId) { mutableListOf() }.add(member)
        invites[inviteId] = invite.copy(uses = invite.uses + 1)
        trips[tripId] = trip.copy(activeMemberCount = trip.activeMemberCount + 1, revision = trip.revision + 1, updatedAtEpochMillis = now)
        return DataResult.Success(member)
    }

    private fun validIsoDate(value: String): Boolean = parseIsoDate(value) != null

    private fun tripLengthInDays(start: String, end: String): Int {
        val first = parseIsoDate(start) ?: return 0
        val last = parseIsoDate(end) ?: return 0
        return ordinal(last) - ordinal(first) + 1
    }

    private data class IsoDate(val year: Int, val month: Int, val day: Int)

    private fun parseIsoDate(value: String): IsoDate? {
        if (value.length != 10 || value[4] != '-' || value[7] != '-') return null
        val parts = value.split('-')
        if (parts.size != 3) return null
        val date = runCatching { IsoDate(parts[0].toInt(), parts[1].toInt(), parts[2].toInt()) }.getOrNull() ?: return null
        val daysInMonth = when (date.month) {
            1, 3, 5, 7, 8, 10, 12 -> 31
            4, 6, 9, 11 -> 30
            2 -> if (date.year % 4 == 0 && (date.year % 100 != 0 || date.year % 400 == 0)) 29 else 28
            else -> 0
        }
        return date.takeIf { it.year in 1..9999 && it.day in 1..daysInMonth }
    }

    private fun ordinal(date: IsoDate): Int {
        var year = date.year
        if (date.month <= 2) year--
        val era = year / 400
        val yearOfEra = year - era * 400
        val monthPrime = date.month + if (date.month > 2) -3 else 9
        val dayOfYear = (153 * monthPrime + 2) / 5 + date.day - 1
        val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
        return era * 146097 + dayOfEra
    }
}
