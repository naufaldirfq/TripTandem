package com.triptandem.data

import android.content.Context
import com.triptandem.shared.BudgetBand
import com.triptandem.shared.GenerationScope
import com.triptandem.shared.ItineraryGenerationDraftRepository
import com.triptandem.shared.ItineraryGenerationInput
import com.triptandem.shared.TripPace
import org.json.JSONArray
import org.json.JSONObject

/** Stores parameters and a server job ID; prompts and provider output are never cached. */
class AndroidItineraryGenerationDraftRepository(context: Context) : ItineraryGenerationDraftRepository {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun load(tripId: String): ItineraryGenerationInput? = runCatching {
        val json = preferences.getString(key(tripId), null) ?: return null
        val objectValue = JSONObject(json)
        val scope = GenerationScope.entries.firstOrNull { it.wireValue == objectValue.optString("scope") }
            ?: GenerationScope.WholeTrip
        val pace = TripPace.entries.firstOrNull { it.wireValue == objectValue.optString("pace") }
        val budget = BudgetBand.entries.firstOrNull { it.wireValue == objectValue.optString("budgetBand") }
        ItineraryGenerationInput(
            scope = scope,
            dayDate = objectValue.optString("dayDate").takeIf(String::isNotBlank),
            pace = pace,
            budgetBand = budget,
            interests = objectValue.optJSONArray("interests").toStrings(),
            dailyStartLabel = objectValue.optString("dailyStartLabel").takeIf(String::isNotBlank),
            dailyEndLabel = objectValue.optString("dailyEndLabel").takeIf(String::isNotBlank),
            accessibilityNotes = objectValue.optString("accessibilityNotes").takeIf(String::isNotBlank),
            dietNotes = objectValue.optString("dietNotes").takeIf(String::isNotBlank),
            lockedItemIds = objectValue.optJSONArray("lockedItemIds").toStrings(),
        )
    }.getOrNull()

    override fun save(tripId: String, input: ItineraryGenerationInput) {
        val objectValue = JSONObject().apply {
            put("scope", input.scope.wireValue)
            input.dayDate?.let { put("dayDate", it) }
            input.pace?.let { put("pace", it.wireValue) }
            input.budgetBand?.let { put("budgetBand", it.wireValue) }
            put("interests", JSONArray(input.interests.distinct().take(12)))
            input.dailyStartLabel?.let { put("dailyStartLabel", it) }
            input.dailyEndLabel?.let { put("dailyEndLabel", it) }
            input.accessibilityNotes?.let { put("accessibilityNotes", it) }
            input.dietNotes?.let { put("dietNotes", it) }
            put("lockedItemIds", JSONArray(input.lockedItemIds.distinct().take(500)))
        }
        preferences.edit().putString(key(tripId), objectValue.toString()).apply()
    }

    override fun clear(tripId: String) {
        preferences.edit()
            .remove(key(tripId))
            .remove(activeJobKey(tripId))
            .apply()
    }

    override fun loadActiveJobId(tripId: String): String? =
        preferences.getString(activeJobKey(tripId), null)?.trim()?.takeIf { it.isNotEmpty() }

    override fun saveActiveJobId(tripId: String, jobId: String) {
        preferences.edit().putString(activeJobKey(tripId), jobId.trim()).apply()
    }

    override fun clearActiveJobId(tripId: String) {
        preferences.edit().remove(activeJobKey(tripId)).apply()
    }

    private fun key(tripId: String): String = "generation:$tripId"
    private fun activeJobKey(tripId: String): String = "generation_active_job:$tripId"

    private companion object {
        const val PREFERENCES_NAME = "triptandem_generation_drafts"
    }
}

private fun JSONArray?.toStrings(): List<String> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
        }
    }
}
