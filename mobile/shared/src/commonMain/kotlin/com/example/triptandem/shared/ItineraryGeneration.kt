package com.triptandem.shared

/** Scopes from PRD 04. Wire values are stable across Android, iOS, and Cloud Functions. */
enum class GenerationScope(val wireValue: String, val label: String) {
    WholeTrip("whole_trip", "Whole trip"),
    SingleDay("single_day", "One day"),
    FillEmptyDays("fill_empty_days", "Fill empty days"),
}

enum class GenerationJobState(val wireValue: String) {
    Queued("queued"),
    Running("running"),
    Succeeded("succeeded"),
    PartiallySucceeded("partially_succeeded"),
    Failed("failed"),
    Cancelled("cancelled"),
    Expired("expired"),
    Applied("applied"),
}

/**
 * Only values the user explicitly supplies are sent to the server. The app
 * never sends member names, emails, bios, contact data, exact lodging, or
 * private notes to the generation provider.
 */
data class ItineraryGenerationInput(
    val scope: GenerationScope = GenerationScope.WholeTrip,
    val dayDate: String? = null,
    val pace: TripPace? = null,
    val budgetBand: BudgetBand? = null,
    val interests: List<String> = emptyList(),
    val dailyStartLabel: String? = null,
    val dailyEndLabel: String? = null,
    val accessibilityNotes: String? = null,
    val dietNotes: String? = null,
    val lockedItemIds: List<String> = emptyList(),
) {
    fun validationError(): TripTandemError? = when {
        scope == GenerationScope.SingleDay && dayDate.isNullOrBlank() -> TripTandemError.Validation("dayDate")
        dayDate != null && !dayDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) -> TripTandemError.Validation("dayDate")
        scope != GenerationScope.SingleDay && dayDate != null -> TripTandemError.Validation("dayDate")
        interests.size > 12 || interests.any { it.length > 60 } -> TripTandemError.Validation("interests")
        lockedItemIds.size > 500 || lockedItemIds.any { it.length !in 1..80 } -> TripTandemError.Validation("lockedItemIds")
        (dailyStartLabel?.length ?: 0) > 32 -> TripTandemError.Validation("dailyStartLabel")
        (dailyEndLabel?.length ?: 0) > 32 -> TripTandemError.Validation("dailyEndLabel")
        (accessibilityNotes?.length ?: 0) > 500 -> TripTandemError.Validation("accessibilityNotes")
        (dietNotes?.length ?: 0) > 500 -> TripTandemError.Validation("dietNotes")
        else -> null
    }

    fun toWireMap(): Map<String, Any?> = buildMap {
        put("scope", scope.wireValue)
        dayDate?.trim()?.takeIf { it.isNotEmpty() }?.let { put("dayDate", it) }
        pace?.let { put("pace", it.wireValue) }
        budgetBand?.let { put("budgetBand", it.wireValue) }
        put("interests", interests.distinct().take(12))
        dailyStartLabel?.trim()?.takeIf { it.isNotEmpty() }?.let { put("dailyStartLabel", it) }
        dailyEndLabel?.trim()?.takeIf { it.isNotEmpty() }?.let { put("dailyEndLabel", it) }
        accessibilityNotes?.trim()?.takeIf { it.isNotEmpty() }?.let { put("accessibilityNotes", it) }
        dietNotes?.trim()?.takeIf { it.isNotEmpty() }?.let { put("dietNotes", it) }
        put("lockedItemIds", lockedItemIds.distinct().take(500))
    }
}

/** Local-only storage for generation parameters and a resumable job ID. */
interface ItineraryGenerationDraftRepository {
    fun load(tripId: String): ItineraryGenerationInput?
    fun save(tripId: String, input: ItineraryGenerationInput)
    fun clear(tripId: String)

    /**
     * Keeps a queued/running preview discoverable after the screen is left or
     * the app is restarted. The provider output itself is never cached here.
     */
    fun loadActiveJobId(tripId: String): String? = null
    fun saveActiveJobId(tripId: String, jobId: String) = Unit
    fun clearActiveJobId(tripId: String) = Unit
}

object NoOpItineraryGenerationDraftRepository : ItineraryGenerationDraftRepository {
    override fun load(tripId: String): ItineraryGenerationInput? = null
    override fun save(tripId: String, input: ItineraryGenerationInput) = Unit
    override fun clear(tripId: String) = Unit
}

data class GeneratedItineraryItem(
    val id: String,
    val type: ItineraryItemType,
    val title: String,
    val dayDate: String,
    val startTimeLabel: String? = null,
    val flexibleTime: Boolean = true,
    val durationMinutes: Int = 60,
    val place: String? = null,
    val note: String? = null,
    val duplicateOfItemId: String? = null,
    val warning: String? = null,
)

data class ItineraryGenerationPreview(
    val schemaVersion: String,
    val assumptions: List<String>,
    val warnings: List<String>,
    val items: List<GeneratedItineraryItem>,
    val unverifiedInformationNotice: String,
)

data class ItineraryGenerationJob(
    val jobId: String,
    val state: GenerationJobState,
    val schemaVersion: String? = null,
    val preview: ItineraryGenerationPreview? = null,
    val generatedItemCount: Int = 0,
    val rejectedItemCount: Int = 0,
    val failureClass: String? = null,
    val errorMessage: String? = null,
)

data class ApplyGenerationResult(
    val jobId: String,
    val appliedItemIds: List<String>,
    val idempotent: Boolean,
)

interface ItineraryGenerationRepository {
    suspend fun createJob(tripId: String, input: ItineraryGenerationInput): DataResult<ItineraryGenerationJob>
    suspend fun getJob(jobId: String): DataResult<ItineraryGenerationJob>
    suspend fun cancelJob(jobId: String): DataResult<ItineraryGenerationJob>
    suspend fun applyJob(
        jobId: String,
        selectedItemIds: List<String>,
        idempotencyKey: String,
        editedItems: List<GeneratedItineraryItem> = emptyList(),
    ): DataResult<ApplyGenerationResult>
}

object UnavailableItineraryGenerationRepository : ItineraryGenerationRepository {
    override suspend fun createJob(tripId: String, input: ItineraryGenerationInput): DataResult<ItineraryGenerationJob> =
        DataResult.Failure(TripTandemError.GenerationFailed("unavailable"))

    override suspend fun getJob(jobId: String): DataResult<ItineraryGenerationJob> =
        DataResult.Failure(TripTandemError.GenerationFailed("unavailable"))

    override suspend fun cancelJob(jobId: String): DataResult<ItineraryGenerationJob> =
        DataResult.Failure(TripTandemError.GenerationFailed("unavailable"))

    override suspend fun applyJob(
        jobId: String,
        selectedItemIds: List<String>,
        idempotencyKey: String,
        editedItems: List<GeneratedItineraryItem>,
    ): DataResult<ApplyGenerationResult> =
        DataResult.Failure(TripTandemError.GenerationFailed("unavailable"))
}
