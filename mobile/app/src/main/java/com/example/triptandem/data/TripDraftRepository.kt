package com.triptandem.data

import android.content.Context
import com.triptandem.shared.TripDraftRepository
import com.triptandem.shared.TripDraftSnapshot
import com.triptandem.shared.BudgetBand
import com.triptandem.shared.TripPace
import com.triptandem.shared.TripVisibility

/** Small, non-sensitive SharedPreferences draft store for the pre-create form. */
class AndroidTripDraftRepository(context: Context) : TripDraftRepository {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun load(): TripDraftSnapshot? {
        if (!preferences.getBoolean(KEY_PRESENT, false)) return null
        return TripDraftSnapshot(
            title = preferences.getString(KEY_TITLE, "").orEmpty(),
            destination = preferences.getString(KEY_DESTINATION, "").orEmpty(),
            startDate = preferences.getString(KEY_START_DATE, "").orEmpty(),
            endDate = preferences.getString(KEY_END_DATE, "").orEmpty(),
            destinationTimezone = preferences.getString(KEY_TIMEZONE, "UTC") ?: "UTC",
            datesFlexible = preferences.getBoolean(KEY_FLEXIBLE, false),
            visibility = TripVisibility.entries.firstOrNull { it.wireValue == preferences.getString(KEY_VISIBILITY, TripVisibility.Private.wireValue) }
                ?: TripVisibility.Private,
            capacity = preferences.getInt(KEY_CAPACITY, 4).coerceIn(2, 12),
            pace = when (preferences.getString(KEY_PACE, TripPace.Balanced.wireValue)) {
                "slow" -> TripPace.Relaxed
                "fast" -> TripPace.Packed
                else -> TripPace.entries.firstOrNull { it.wireValue == preferences.getString(KEY_PACE, null) } ?: TripPace.Balanced
            },
            budgetBand = when (preferences.getString(KEY_BUDGET, BudgetBand.Moderate.wireValue)) {
                "mid" -> BudgetBand.Moderate
                "flexible" -> BudgetBand.Comfort
                else -> BudgetBand.entries.firstOrNull { it.wireValue == preferences.getString(KEY_BUDGET, null) } ?: BudgetBand.Moderate
            },
            currency = preferences.getString(KEY_CURRENCY, "USD") ?: "USD",
            expectationNote = preferences.getString(KEY_EXPECTATION, "").orEmpty(),
            interests = preferences.getStringSet(KEY_INTERESTS, emptySet()).orEmpty().toList(),
            coverColor = preferences.getString(KEY_COVER_COLOR, "#E8704A") ?: "#E8704A",
        )
    }

    override fun save(snapshot: TripDraftSnapshot) {
        preferences.edit()
            .putBoolean(KEY_PRESENT, true)
            .putString(KEY_TITLE, snapshot.title)
            .putString(KEY_DESTINATION, snapshot.destination)
            .putString(KEY_START_DATE, snapshot.startDate)
            .putString(KEY_END_DATE, snapshot.endDate)
            .putString(KEY_TIMEZONE, snapshot.destinationTimezone)
            .putBoolean(KEY_FLEXIBLE, snapshot.datesFlexible)
            .putString(KEY_VISIBILITY, snapshot.visibility.wireValue)
            .putInt(KEY_CAPACITY, snapshot.capacity.coerceIn(2, 12))
            .putString(KEY_PACE, snapshot.pace.wireValue)
            .putString(KEY_BUDGET, snapshot.budgetBand.wireValue)
            .putString(KEY_CURRENCY, snapshot.currency)
            .putString(KEY_EXPECTATION, snapshot.expectationNote)
            .putStringSet(KEY_INTERESTS, snapshot.interests.toSet())
            .putString(KEY_COVER_COLOR, snapshot.coverColor)
            .apply()
    }

    override fun clear() {
        preferences.edit().clear().apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "triptandem_trip_draft"
        const val KEY_PRESENT = "present"
        const val KEY_TITLE = "title"
        const val KEY_DESTINATION = "destination"
        const val KEY_START_DATE = "startDate"
        const val KEY_END_DATE = "endDate"
        const val KEY_TIMEZONE = "timezone"
        const val KEY_FLEXIBLE = "datesFlexible"
        const val KEY_VISIBILITY = "visibility"
        const val KEY_CAPACITY = "capacity"
        const val KEY_PACE = "pace"
        const val KEY_BUDGET = "budgetBand"
        const val KEY_CURRENCY = "currency"
        const val KEY_EXPECTATION = "expectation"
        const val KEY_INTERESTS = "interests"
        const val KEY_COVER_COLOR = "coverColor"
    }
}
