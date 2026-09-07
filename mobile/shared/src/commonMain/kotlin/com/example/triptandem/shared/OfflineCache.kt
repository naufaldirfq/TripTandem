package com.triptandem.shared

import kotlinx.serialization.json.*

/**
 * Platform-independent abstraction for encrypted key-value payload storage.
 * Android implements this with AES-GCM backed by AndroidKeyStore.
 * iOS implements this with CryptoKit AES-GCM and Keychain storage.
 */
interface SecurePayloadStorage {
    suspend fun read(key: String): String?
    suspend fun write(key: String, payload: String)
    suspend fun delete(key: String)
    suspend fun listKeys(): List<String>
    suspend fun clear()
}

/** In-memory implementation used for deterministic unit tests and previews. */
class InMemorySecurePayloadStorage : SecurePayloadStorage {
    private val store = mutableMapOf<String, String>()

    override suspend fun read(key: String): String? = store[key]

    override suspend fun write(key: String, payload: String) {
        store[key] = payload
    }

    override suspend fun delete(key: String) {
        store.remove(key)
    }

    override suspend fun listKeys(): List<String> = store.keys.toList()

    override suspend fun clear() {
        store.clear()
    }
}

/**
 * A self-contained authorized trip snapshot stored in encrypted platform persistence.
 * Tracks the last sync timestamp and server revision to enforce freshness and
 * offline read-only guarantees.
 */
data class CachedTripBundle(
    val trip: TripRecord,
    val items: List<ItineraryItem>,
    val members: List<TripMember>,
    val lastSyncEpochMillis: Long,
    val serverRevision: Int,
    val userId: String,
)

object OfflineCachePolicy {
    /** 7-day maximum offline authorization window per PRD 11 edge case requirements. */
    const val MAX_OFFLINE_AUTH_WINDOW_MILLIS = 7 * 24 * 60 * 60 * 1000L

    fun freshnessBucket(lastSyncEpochMillis: Long, nowEpochMillis: Long = currentEpochMillis()): String {
        val ageMillis = (nowEpochMillis - lastSyncEpochMillis).coerceAtLeast(0)
        return when {
            ageMillis < 60 * 60 * 1000L -> "<1h"
            ageMillis < 24 * 60 * 60 * 1000L -> "1-24h"
            ageMillis < 7 * 24 * 60 * 60 * 1000L -> "1-7d"
            else -> ">7d"
        }
    }

    fun isStale(lastSyncEpochMillis: Long, nowEpochMillis: Long = currentEpochMillis()): Boolean {
        return (nowEpochMillis - lastSyncEpochMillis) > MAX_OFFLINE_AUTH_WINDOW_MILLIS
    }

    fun isAuthorizedOffline(lastSyncEpochMillis: Long, nowEpochMillis: Long = currentEpochMillis()): Boolean {
        return !isStale(lastSyncEpochMillis, nowEpochMillis)
    }
}

interface ProtectedTripCacheRepository {
    suspend fun getCachedTrips(userId: String): List<TripRecord>
    suspend fun getCachedTripBundle(userId: String, tripId: String): CachedTripBundle?
    suspend fun saveTripBundle(bundle: CachedTripBundle)
    suspend fun updateCachedTrip(trip: TripRecord, userId: String)
    suspend fun updateCachedItems(tripId: String, items: List<ItineraryItem>, userId: String)
    suspend fun updateCachedMembers(tripId: String, members: List<TripMember>, userId: String)
    suspend fun removeTrip(userId: String, tripId: String)
    suspend fun clearAll(userId: String? = null, reason: String = "user_cleared")
    suspend fun getCacheSizeBytes(userId: String? = null): Long
}

object NoOpProtectedTripCacheRepository : ProtectedTripCacheRepository {
    override suspend fun getCachedTrips(userId: String): List<TripRecord> = emptyList()
    override suspend fun getCachedTripBundle(userId: String, tripId: String): CachedTripBundle? = null
    override suspend fun saveTripBundle(bundle: CachedTripBundle) = Unit
    override suspend fun updateCachedTrip(trip: TripRecord, userId: String) = Unit
    override suspend fun updateCachedItems(tripId: String, items: List<ItineraryItem>, userId: String) = Unit
    override suspend fun updateCachedMembers(tripId: String, members: List<TripMember>, userId: String) = Unit
    override suspend fun removeTrip(userId: String, tripId: String) = Unit
    override suspend fun clearAll(userId: String?, reason: String) = Unit
    override suspend fun getCacheSizeBytes(userId: String?): Long = 0L
}

class StandardProtectedTripCacheRepository(
    private val storage: SecurePayloadStorage,
    private val analytics: TripTandemAnalytics? = null,
) : ProtectedTripCacheRepository {

    override suspend fun getCachedTrips(userId: String): List<TripRecord> {
        val tripIds = loadTripIndex(userId)
        val result = mutableListOf<TripRecord>()
        for (tripId in tripIds) {
            val bundle = getCachedTripBundle(userId, tripId, logAnalytics = false)
            if (bundle != null) {
                result.add(bundle.trip)
            }
        }
        if (result.isNotEmpty()) {
            val oldestSync = result.minOfOrNull { it.updatedAtEpochMillis ?: 0L } ?: 0L
            analytics?.logEvent(
                TripTandemAnalytics.Events.OFFLINE_CACHE_READ,
                mapOf("freshness_bucket" to OfflineCachePolicy.freshnessBucket(oldestSync)),
            )
        }
        return result
    }

    override suspend fun getCachedTripBundle(userId: String, tripId: String): CachedTripBundle? {
        return getCachedTripBundle(userId, tripId, logAnalytics = true)
    }

    private suspend fun getCachedTripBundle(userId: String, tripId: String, logAnalytics: Boolean): CachedTripBundle? {
        val payload = storage.read(bundleKey(userId, tripId)) ?: return null
        val bundle = runCatching { parseTripBundle(payload) }.getOrNull()
        if (bundle == null) {
            // Self-heal corrupted payload safely
            storage.delete(bundleKey(userId, tripId))
            removeFromTripIndex(userId, tripId)
            return null
        }
        if (logAnalytics) {
            analytics?.logEvent(
                TripTandemAnalytics.Events.OFFLINE_CACHE_READ,
                mapOf("freshness_bucket" to OfflineCachePolicy.freshnessBucket(bundle.lastSyncEpochMillis)),
            )
        }
        return bundle
    }

    override suspend fun saveTripBundle(bundle: CachedTripBundle) {
        val payload = serializeTripBundle(bundle)
        storage.write(bundleKey(bundle.userId, bundle.trip.id), payload)
        addToTripIndex(bundle.userId, bundle.trip.id)
    }

    override suspend fun updateCachedTrip(trip: TripRecord, userId: String) {
        val existing = getCachedTripBundle(userId, trip.id, logAnalytics = false)
        val now = currentEpochMillis()
        val bundle = if (existing != null) {
            existing.copy(
                trip = trip,
                serverRevision = trip.revision,
                lastSyncEpochMillis = now,
            )
        } else {
            CachedTripBundle(
                trip = trip,
                items = emptyList(),
                members = emptyList(),
                lastSyncEpochMillis = now,
                serverRevision = trip.revision,
                userId = userId,
            )
        }
        saveTripBundle(bundle)
    }

    override suspend fun updateCachedItems(tripId: String, items: List<ItineraryItem>, userId: String) {
        val existing = getCachedTripBundle(userId, tripId, logAnalytics = false) ?: return
        val updated = existing.copy(
            items = items,
            lastSyncEpochMillis = currentEpochMillis(),
        )
        saveTripBundle(updated)
    }

    override suspend fun updateCachedMembers(tripId: String, members: List<TripMember>, userId: String) {
        val existing = getCachedTripBundle(userId, tripId, logAnalytics = false) ?: return
        val updated = existing.copy(
            members = members,
            lastSyncEpochMillis = currentEpochMillis(),
        )
        saveTripBundle(updated)
    }

    override suspend fun removeTrip(userId: String, tripId: String) {
        storage.delete(bundleKey(userId, tripId))
        removeFromTripIndex(userId, tripId)
        analytics?.logEvent(
            TripTandemAnalytics.Events.PROTECTED_CACHE_CLEARED,
            mapOf("reason" to "membership_removed"),
        )
    }

    override suspend fun clearAll(userId: String?, reason: String) {
        if (userId == null) {
            storage.clear()
        } else {
            val tripIds = loadTripIndex(userId)
            for (tripId in tripIds) {
                storage.delete(bundleKey(userId, tripId))
            }
            storage.delete(indexKey(userId))
        }
        analytics?.logEvent(
            TripTandemAnalytics.Events.PROTECTED_CACHE_CLEARED,
            mapOf("reason" to reason),
        )
    }

    override suspend fun getCacheSizeBytes(userId: String?): Long {
        var total = 0L
        val keys = storage.listKeys()
        for (key in keys) {
            if (userId != null && !key.contains(":$userId:")) continue
            val payload = storage.read(key)
            if (payload != null) {
                total += payload.encodeToByteArray().size
            }
        }
        return total
    }

    private fun bundleKey(userId: String, tripId: String): String = "trip_bundle:$userId:$tripId"
    private fun indexKey(userId: String): String = "trip_index:$userId"

    private suspend fun loadTripIndex(userId: String): List<String> {
        val payload = storage.read(indexKey(userId)) ?: return emptyList()
        return runCatching {
            val json = Json.parseToJsonElement(payload).jsonArray
            json.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        }.getOrDefault(emptyList())
    }

    private suspend fun addToTripIndex(userId: String, tripId: String) {
        val current = loadTripIndex(userId).toMutableSet()
        if (current.add(tripId)) {
            val json = buildJsonArray {
                current.forEach { add(JsonPrimitive(it)) }
            }
            storage.write(indexKey(userId), json.toString())
        }
    }

    private suspend fun removeFromTripIndex(userId: String, tripId: String) {
        val current = loadTripIndex(userId).toMutableSet()
        if (current.remove(tripId)) {
            val json = buildJsonArray {
                current.forEach { add(JsonPrimitive(it)) }
            }
            storage.write(indexKey(userId), json.toString())
        }
    }

    companion object {
        fun serializeTripBundle(bundle: CachedTripBundle): String {
            val root = buildJsonObject {
                put("userId", bundle.userId)
                put("lastSyncEpochMillis", bundle.lastSyncEpochMillis)
                put("serverRevision", bundle.serverRevision)
                put("trip", serializeTripRecord(bundle.trip))
                put("items", buildJsonArray {
                    bundle.items.forEach { add(serializeItineraryItem(it)) }
                })
                put("members", buildJsonArray {
                    bundle.members.forEach { add(serializeTripMember(it)) }
                })
            }
            return root.toString()
        }

        fun parseTripBundle(payload: String): CachedTripBundle {
            val json = Json.parseToJsonElement(payload).jsonObject
            val userId = json["userId"]?.jsonPrimitive?.content ?: ""
            val lastSync = json["lastSyncEpochMillis"]?.jsonPrimitive?.longOrNull ?: 0L
            val revision = json["serverRevision"]?.jsonPrimitive?.intOrNull ?: 0
            val trip = parseTripRecord(json["trip"]?.jsonObject ?: error("missing trip"))
            val items = json["items"]?.jsonArray?.mapNotNull { el ->
                runCatching { parseItineraryItem(el.jsonObject) }.getOrNull()
            } ?: emptyList()
            val members = json["members"]?.jsonArray?.mapNotNull { el ->
                runCatching { parseTripMember(el.jsonObject) }.getOrNull()
            } ?: emptyList()

            return CachedTripBundle(
                trip = trip,
                items = items,
                members = members,
                lastSyncEpochMillis = lastSync,
                serverRevision = revision,
                userId = userId,
            )
        }

        private fun serializeTripRecord(trip: TripRecord): JsonObject = buildJsonObject {
            put("id", trip.id)
            put("ownerId", trip.ownerId)
            put("title", trip.title)
            put("destination", trip.destination)
            put("startDate", trip.startDate)
            put("endDate", trip.endDate)
            put("destinationTimezone", trip.destinationTimezone)
            put("datesFlexible", trip.datesFlexible)
            put("visibility", trip.visibility.wireValue)
            put("capacity", trip.capacity)
            put("status", trip.status.wireValue)
            trip.currency?.let { put("currency", it) }
            trip.budgetBand?.let { put("budgetBand", it.wireValue) }
            trip.pace?.let { put("pace", it.wireValue) }
            trip.expectationNote?.let { put("expectationNote", it) }
            put("revision", trip.revision)
            put("activeMemberCount", trip.activeMemberCount)
            put("interests", buildJsonArray { trip.interests.forEach { add(JsonPrimitive(it)) } })
            trip.coverColor?.let { put("coverColor", it) }
            trip.createdAtEpochMillis?.let { put("createdAtEpochMillis", it) }
            trip.updatedAtEpochMillis?.let { put("updatedAtEpochMillis", it) }
        }

        private fun parseTripRecord(json: JsonObject): TripRecord {
            val visibilityStr = json["visibility"]?.jsonPrimitive?.content
            val visibility = TripVisibility.entries.firstOrNull { it.wireValue == visibilityStr } ?: TripVisibility.Private
            val statusStr = json["status"]?.jsonPrimitive?.content
            val status = TripStatus.entries.firstOrNull { it.wireValue == statusStr } ?: TripStatus.Planning
            val budgetStr = json["budgetBand"]?.jsonPrimitive?.contentOrNull
            val budget = BudgetBand.entries.firstOrNull { it.wireValue == budgetStr }
            val paceStr = json["pace"]?.jsonPrimitive?.contentOrNull
            val pace = TripPace.entries.firstOrNull { it.wireValue == paceStr }

            return TripRecord(
                id = json["id"]?.jsonPrimitive?.content ?: "",
                ownerId = json["ownerId"]?.jsonPrimitive?.content ?: "",
                title = json["title"]?.jsonPrimitive?.content ?: "",
                destination = json["destination"]?.jsonPrimitive?.content ?: "",
                startDate = json["startDate"]?.jsonPrimitive?.content ?: "",
                endDate = json["endDate"]?.jsonPrimitive?.content ?: "",
                destinationTimezone = json["destinationTimezone"]?.jsonPrimitive?.content ?: "UTC",
                datesFlexible = json["datesFlexible"]?.jsonPrimitive?.booleanOrNull ?: false,
                visibility = visibility,
                capacity = json["capacity"]?.jsonPrimitive?.intOrNull ?: 4,
                status = status,
                currency = json["currency"]?.jsonPrimitive?.contentOrNull,
                budgetBand = budget,
                pace = pace,
                expectationNote = json["expectationNote"]?.jsonPrimitive?.contentOrNull,
                revision = json["revision"]?.jsonPrimitive?.intOrNull ?: 0,
                activeMemberCount = json["activeMemberCount"]?.jsonPrimitive?.intOrNull ?: 1,
                interests = json["interests"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
                coverColor = json["coverColor"]?.jsonPrimitive?.contentOrNull,
                createdAtEpochMillis = json["createdAtEpochMillis"]?.jsonPrimitive?.longOrNull,
                updatedAtEpochMillis = json["updatedAtEpochMillis"]?.jsonPrimitive?.longOrNull,
            )
        }

        private fun serializeItineraryItem(item: ItineraryItem): JsonObject = buildJsonObject {
            put("id", item.id)
            put("tripId", item.tripId)
            put("type", item.type.wireValue)
            put("title", item.title)
            item.startTimeEpochMillis?.let { put("startTimeEpochMillis", it) }
            item.startTimeLabel?.let { put("startTimeLabel", it) }
            put("durationMinutes", item.durationMinutes)
            item.place?.let { put("place", it) }
            item.note?.let { put("note", it) }
            put("status", item.status.wireValue)
            put("visibility", item.visibility.wireValue)
            put("position", item.position)
            put("revision", item.revision)
            item.lastEditedBy?.let { put("lastEditedBy", it) }
            item.updatedAtEpochMillis?.let { put("updatedAtEpochMillis", it) }
            item.dayDate?.let { put("dayDate", it) }
            item.deletedAtEpochMillis?.let { put("deletedAtEpochMillis", it) }
            item.undoExpiresAtEpochMillis?.let { put("undoExpiresAtEpochMillis", it) }
            put("flexibleTime", item.flexibleTime)
            item.generatedBy?.let { put("generatedBy", it) }
            item.generationJobId?.let { put("generationJobId", it) }
        }

        private fun parseItineraryItem(json: JsonObject): ItineraryItem {
            val typeStr = json["type"]?.jsonPrimitive?.content
            val type = ItineraryItemType.entries.firstOrNull { it.wireValue == typeStr } ?: ItineraryItemType.Activity
            val statusStr = json["status"]?.jsonPrimitive?.content
            val status = ItineraryItemStatus.entries.firstOrNull { it.wireValue == statusStr } ?: ItineraryItemStatus.Planned
            val visStr = json["visibility"]?.jsonPrimitive?.content
            val visibility = ItineraryVisibility.entries.firstOrNull { it.wireValue == visStr } ?: ItineraryVisibility.Members

            return ItineraryItem(
                id = json["id"]?.jsonPrimitive?.content ?: "",
                tripId = json["tripId"]?.jsonPrimitive?.content ?: "",
                type = type,
                title = json["title"]?.jsonPrimitive?.content ?: "",
                startTimeEpochMillis = json["startTimeEpochMillis"]?.jsonPrimitive?.longOrNull,
                startTimeLabel = json["startTimeLabel"]?.jsonPrimitive?.contentOrNull,
                durationMinutes = json["durationMinutes"]?.jsonPrimitive?.intOrNull ?: 60,
                place = json["place"]?.jsonPrimitive?.contentOrNull,
                note = json["note"]?.jsonPrimitive?.contentOrNull,
                status = status,
                visibility = visibility,
                position = json["position"]?.jsonPrimitive?.intOrNull ?: 0,
                revision = json["revision"]?.jsonPrimitive?.intOrNull ?: 0,
                lastEditedBy = json["lastEditedBy"]?.jsonPrimitive?.contentOrNull,
                updatedAtEpochMillis = json["updatedAtEpochMillis"]?.jsonPrimitive?.longOrNull,
                dayDate = json["dayDate"]?.jsonPrimitive?.contentOrNull,
                deletedAtEpochMillis = json["deletedAtEpochMillis"]?.jsonPrimitive?.longOrNull,
                undoExpiresAtEpochMillis = json["undoExpiresAtEpochMillis"]?.jsonPrimitive?.longOrNull,
                flexibleTime = json["flexibleTime"]?.jsonPrimitive?.booleanOrNull ?: false,
                generatedBy = json["generatedBy"]?.jsonPrimitive?.contentOrNull,
                generationJobId = json["generationJobId"]?.jsonPrimitive?.contentOrNull,
            )
        }

        private fun serializeTripMember(member: TripMember): JsonObject = buildJsonObject {
            put("tripId", member.tripId)
            put("userId", member.userId)
            put("displayName", member.displayName)
            put("role", member.role.wireValue)
            put("status", member.status.wireValue)
            member.inviteId?.let { put("inviteId", it) }
            member.joinedAtEpochMillis?.let { put("joinedAtEpochMillis", it) }
            member.updatedAtEpochMillis?.let { put("updatedAtEpochMillis", it) }
        }

        private fun parseTripMember(json: JsonObject): TripMember {
            val roleStr = json["role"]?.jsonPrimitive?.content
            val role = TripMemberRole.entries.firstOrNull { it.wireValue == roleStr } ?: TripMemberRole.Viewer
            val statusStr = json["status"]?.jsonPrimitive?.content
            val status = MembershipStatus.entries.firstOrNull { it.wireValue == statusStr } ?: MembershipStatus.Active

            return TripMember(
                tripId = json["tripId"]?.jsonPrimitive?.content ?: "",
                userId = json["userId"]?.jsonPrimitive?.content ?: "",
                displayName = json["displayName"]?.jsonPrimitive?.content ?: "",
                role = role,
                status = status,
                inviteId = json["inviteId"]?.jsonPrimitive?.contentOrNull,
                joinedAtEpochMillis = json["joinedAtEpochMillis"]?.jsonPrimitive?.longOrNull,
                updatedAtEpochMillis = json["updatedAtEpochMillis"]?.jsonPrimitive?.longOrNull,
            )
        }
    }
}
