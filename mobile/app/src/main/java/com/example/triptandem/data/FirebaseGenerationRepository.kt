package com.triptandem.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.triptandem.shared.ApplyGenerationResult
import com.triptandem.shared.DataResult
import com.triptandem.shared.GeneratedItineraryItem
import com.triptandem.shared.GenerationJobState
import com.triptandem.shared.ItineraryGenerationInput
import com.triptandem.shared.ItineraryGenerationJob
import com.triptandem.shared.ItineraryGenerationPreview
import com.triptandem.shared.ItineraryGenerationRepository
import com.triptandem.shared.ItineraryItemType
import com.triptandem.shared.TripPace
import com.triptandem.shared.TripTandemError
import kotlinx.coroutines.tasks.await

/**
 * Callable Functions adapter. Firestore generation jobs are intentionally not
 * read directly by the client; this keeps prompts, quotas, and Apply policy
 * behind the server boundary on both platforms.
 */
class FirebaseItineraryGenerationRepository(
    private val auth: FirebaseAuth,
    private val functions: FirebaseFunctions,
) : ItineraryGenerationRepository {
    override suspend fun createJob(tripId: String, input: ItineraryGenerationInput): DataResult<ItineraryGenerationJob> {
        if (auth.currentUser == null) return DataResult.Failure(TripTandemError.Unauthenticated)
        input.validationError()?.let { return DataResult.Failure(it) }
        return call("createItineraryGenerationJob", mapOf("tripId" to tripId, "input" to input.toWireMap())) { map ->
            parseJob(map)
        }
    }

    override suspend fun getJob(jobId: String): DataResult<ItineraryGenerationJob> {
        if (auth.currentUser == null) return DataResult.Failure(TripTandemError.Unauthenticated)
        return call("getItineraryGenerationJob", mapOf("jobId" to jobId), ::parseJob)
    }

    override suspend fun cancelJob(jobId: String): DataResult<ItineraryGenerationJob> {
        if (auth.currentUser == null) return DataResult.Failure(TripTandemError.Unauthenticated)
        return call("cancelItineraryGenerationJob", mapOf("jobId" to jobId), ::parseJob)
    }

    override suspend fun applyJob(
        jobId: String,
        selectedItemIds: List<String>,
        idempotencyKey: String,
        editedItems: List<GeneratedItineraryItem>,
    ): DataResult<ApplyGenerationResult> {
        if (auth.currentUser == null) return DataResult.Failure(TripTandemError.Unauthenticated)
        if (selectedItemIds.size > 120 || editedItems.size > 120 || idempotencyKey.isBlank()) return DataResult.Failure(TripTandemError.Validation("selection"))
        val payload = buildMap<String, Any?> {
            put("jobId", jobId)
            put("selectedItemIds", selectedItemIds)
            put("idempotencyKey", idempotencyKey)
            if (editedItems.isNotEmpty()) put("editedItems", editedItems.map(GeneratedItineraryItem::toWireMap))
        }
        return call("applyItineraryGenerationJob", payload) { map ->
            ApplyGenerationResult(
                jobId = map.string("jobId") ?: jobId,
                appliedItemIds = map.stringList("appliedItemIds"),
                idempotent = map.bool("idempotent"),
            )
        }
    }

    private suspend fun <T> call(name: String, payload: Map<String, Any?>, parse: (Map<*, *>) -> T): DataResult<T> = try {
        val result = functions.getHttpsCallable(name).call(payload).await()
        val map = result.data as? Map<*, *> ?: return DataResult.Failure(TripTandemError.GenerationFailed("invalid_response"))
        DataResult.Success(parse(map))
    } catch (error: FirebaseFunctionsException) {
        DataResult.Failure(error.toTripTandemError())
    } catch (error: Throwable) {
        DataResult.Failure(if (error.message?.contains("network", ignoreCase = true) == true) TripTandemError.Offline else TripTandemError.GenerationFailed("client_error"))
    }
}

private fun parseJob(map: Map<*, *>): ItineraryGenerationJob {
    val previewMap = map.mapValue("preview")
    return ItineraryGenerationJob(
        jobId = map.string("jobId").orEmpty(),
        state = GenerationJobState.entries.firstOrNull { it.wireValue == map.string("state") } ?: GenerationJobState.Failed,
        schemaVersion = map.string("schemaVersion"),
        preview = previewMap?.let(::parsePreview),
        generatedItemCount = map.int("generatedItemCount"),
        rejectedItemCount = map.int("rejectedItemCount"),
        failureClass = map.string("failureClass"),
        errorMessage = map.string("errorMessage"),
    )
}

private fun parsePreview(map: Map<*, *>): ItineraryGenerationPreview {
    val itemMaps = map.listValue("items")
    return ItineraryGenerationPreview(
        schemaVersion = map.string("schemaVersion").orEmpty(),
        assumptions = map.stringList("assumptions"),
        warnings = map.stringList("warnings"),
        items = itemMaps.mapNotNull { item ->
            val type = when (item.string("type")) {
                "activity" -> ItineraryItemType.Activity
                "transport" -> ItineraryItemType.Transport
                "meal" -> ItineraryItemType.Meal
                "free" -> ItineraryItemType.Note
                else -> null
            } ?: return@mapNotNull null
            GeneratedItineraryItem(
                id = item.string("id").orEmpty(),
                type = type,
                title = item.string("title").orEmpty(),
                dayDate = item.string("dayDate").orEmpty(),
                startTimeLabel = item.string("startTimeLabel"),
                flexibleTime = item.bool("flexibleTime"),
                durationMinutes = item.int("durationMinutes"),
                place = item.string("place"),
                note = item.string("note"),
                duplicateOfItemId = item.string("duplicateOfItemId"),
                warning = item.string("warning"),
            )
        },
        unverifiedInformationNotice = map.string("unverifiedInformationNotice")
            ?: "AI suggestions may be outdated. Check details before you go.",
    )
}

private fun FirebaseFunctionsException.toTripTandemError(): TripTandemError = when (code) {
    FirebaseFunctionsException.Code.UNAUTHENTICATED -> TripTandemError.Unauthenticated
    FirebaseFunctionsException.Code.PERMISSION_DENIED -> TripTandemError.PermissionDenied
    FirebaseFunctionsException.Code.NOT_FOUND -> TripTandemError.NotFound
    FirebaseFunctionsException.Code.INVALID_ARGUMENT -> {
        // The server uses a neutral invalid-argument response for unsafe
        // planning intent. Preserve that explicit safety state so the shared
        // UI can explain the refusal instead of showing a form-validation
        // error. Other invalid arguments remain field validation failures.
        if (message.orEmpty().contains("isn't available for itinerary planning", ignoreCase = true)) {
            TripTandemError.GenerationFailed("safety_blocked")
        } else {
            TripTandemError.Validation()
        }
    }
    FirebaseFunctionsException.Code.ABORTED -> TripTandemError.Conflict
    FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> TripTandemError.QuotaExceeded
    FirebaseFunctionsException.Code.FAILED_PRECONDITION -> {
        // Keep the documented coarse generation vocabulary in parity with the
        // iOS adapter. Provider text is never copied into a client error or
        // analytics payload. Generic failed-preconditions (for example a
        // read-only trip or a preview that is not ready) remain conflicts.
        val normalized = message.orEmpty().lowercase()
        when {
            normalized.contains("organizer_pro_required") -> TripTandemError.EntitlementRequired
            normalized.contains("ai_generation_disabled") -> TripTandemError.GenerationFailed("feature_disabled")
            normalized.contains("provider_unconfigured") -> TripTandemError.GenerationFailed("provider_unconfigured")
            normalized.contains("no_valid_suggestions") -> TripTandemError.GenerationFailed("no_valid_suggestions")
            normalized.contains("invalid_provider_output") -> TripTandemError.GenerationFailed("invalid_provider_output")
            normalized.contains("access_revoked") -> TripTandemError.GenerationFailed("access_revoked")
            normalized.contains("provider_timeout") -> TripTandemError.GenerationFailed("provider_timeout")
            normalized.contains("provider_rate_limited") -> TripTandemError.GenerationFailed("provider_rate_limited")
            normalized.contains("provider_error") -> TripTandemError.GenerationFailed("provider_error")
            normalized.contains("expired") -> TripTandemError.GenerationFailed("expired")
            normalized.contains("cancelled") -> TripTandemError.GenerationFailed("cancelled")
            else -> TripTandemError.Conflict
        }
    }
    FirebaseFunctionsException.Code.UNAVAILABLE,
    FirebaseFunctionsException.Code.DEADLINE_EXCEEDED,
    -> TripTandemError.Offline
    else -> TripTandemError.GenerationFailed(code.name.lowercase())
}

private fun Map<*, *>.string(key: String): String? = this[key] as? String
private fun Map<*, *>.bool(key: String): Boolean = this[key] as? Boolean ?: false
private fun Map<*, *>.int(key: String): Int = (this[key] as? Number)?.toInt() ?: 0
private fun Map<*, *>.stringList(key: String): List<String> = (this[key] as? List<*>)?.filterIsInstance<String>().orEmpty()
private fun Map<*, *>.listValue(key: String): List<Map<*, *>> = (this[key] as? List<*>)?.mapNotNull { it as? Map<*, *> }.orEmpty()
private fun Map<*, *>.mapValue(key: String): Map<*, *>? = this[key] as? Map<*, *>

private fun GeneratedItineraryItem.toWireMap(): Map<String, Any?> = buildMap {
    put("id", id)
    put("title", title)
    put("startTimeLabel", startTimeLabel)
    put("flexibleTime", flexibleTime)
    put("durationMinutes", durationMinutes)
    put("place", place)
    put("note", note)
}
