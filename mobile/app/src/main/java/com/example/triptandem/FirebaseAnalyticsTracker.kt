package com.triptandem

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.triptandem.shared.TripTandemAnalytics

internal class FirebaseAnalyticsTracker(
    private val firebaseAnalytics: FirebaseAnalytics,
) : TripTandemAnalytics {
    override fun logScreen(screenName: String) {
        if (screenName !in allowedScreenNames || screenName.length > 40 || screenName.any(Char::isWhitespace)) return
        logEvent(TripTandemAnalytics.Events.SCREEN_VIEW, mapOf("screen_name" to screenName))
    }

    override fun logEvent(name: String, parameters: Map<String, String>) {
        if (name !in allowedEvents) return
        val bundle = Bundle().apply {
            parameters
                .filterKeys { it in allowedParameters[name].orEmpty() }
                .filterValues { it.length <= 40 && it.none(Char::isWhitespace) }
                .forEach { (key, value) -> putString(key, value) }
        }
        firebaseAnalytics.logEvent(name, bundle)
    }

    private companion object {
        val allowedEvents = setOf(
            TripTandemAnalytics.Events.OPEN_PUBLISH_STARTED,
            TripTandemAnalytics.Events.OPEN_PUBLISH_COMPLETED,
            TripTandemAnalytics.Events.DISCOVER_SEARCH_PERFORMED,
            TripTandemAnalytics.Events.DISCOVER_RESULT_OPENED,
            TripTandemAnalytics.Events.DISCOVER_EMPTY_VIEWED,
            TripTandemAnalytics.Events.OPEN_TRIP_CLOSED,
            TripTandemAnalytics.Events.COMPATIBILITY_VIEWED,
            TripTandemAnalytics.Events.JOIN_REQUEST_STARTED,
            TripTandemAnalytics.Events.JOIN_REQUEST_SUBMITTED,
            TripTandemAnalytics.Events.JOIN_REQUEST_WITHDRAWN,
            TripTandemAnalytics.Events.JOIN_REQUEST_REVIEWED,
            TripTandemAnalytics.Events.JOIN_REQUEST_INVALIDATED,
            TripTandemAnalytics.Events.SAFETY_CONTROL_OPENED,
            TripTandemAnalytics.Events.BLOCK_COMPLETED,
            TripTandemAnalytics.Events.REPORT_SUBMITTED,
            TripTandemAnalytics.Events.PUBLIC_CONTENT_HELD,
            TripTandemAnalytics.Events.MODERATION_ACTIONED,
            TripTandemAnalytics.Events.NOTIFICATION_PERMISSION_PROMPTED,
            TripTandemAnalytics.Events.NOTIFICATION_PERMISSION_RESULT,
            TripTandemAnalytics.Events.NOTIFICATION_CREATED,
            TripTandemAnalytics.Events.NOTIFICATION_OPENED,
            TripTandemAnalytics.Events.ACTIVITY_ACTION_COMPLETED,
            TripTandemAnalytics.Events.NOTIFICATION_PREFERENCE_CHANGED,

            TripTandemAnalytics.Events.SCREEN_VIEW,
            TripTandemAnalytics.Events.AUTH_ANONYMOUS_SUCCEEDED,
            TripTandemAnalytics.Events.AUTH_ANONYMOUS_FAILED,
            TripTandemAnalytics.Events.AUTH_SESSION_RESTORED,
            TripTandemAnalytics.Events.SIGN_UP_STARTED,
            TripTandemAnalytics.Events.SIGN_UP_COMPLETED,
            TripTandemAnalytics.Events.PROFILE_ESSENTIALS_COMPLETED,
            TripTandemAnalytics.Events.PROFILE_OPTIONAL_COMPLETED,
            TripTandemAnalytics.Events.PROFILE_PREVIEW_OPENED,
            TripTandemAnalytics.Events.ACCOUNT_DELETION_STARTED,
            TripTandemAnalytics.Events.ACCOUNT_DELETION_COMPLETED,
            TripTandemAnalytics.Events.TRIP_CREATE_STARTED,
            TripTandemAnalytics.Events.TRIP_DRAFT_CONTINUED,
            TripTandemAnalytics.Events.TRIP_CREATED,
            TripTandemAnalytics.Events.TRIP_CONFLICT_DETECTED,
            TripTandemAnalytics.Events.TRIP_VISIBILITY_CHANGED,
            TripTandemAnalytics.Events.TRIP_CANCELLED,
            TripTandemAnalytics.Events.TRIP_ARCHIVED,
            TripTandemAnalytics.Events.TRIP_DELETED,
            TripTandemAnalytics.Events.ITINERARY_ITEM_CREATED,
            TripTandemAnalytics.Events.ITINERARY_ITEM_UPDATED,
            TripTandemAnalytics.Events.ITINERARY_ITEM_REORDERED,
            TripTandemAnalytics.Events.ITINERARY_ITEM_DELETED,
            TripTandemAnalytics.Events.ITINERARY_CONFLICT_DETECTED,
            TripTandemAnalytics.Events.ITINERARY_CONFLICT_RESOLVED,
            TripTandemAnalytics.Events.ITINERARY_UNDO_RESTORED,
            TripTandemAnalytics.Events.ITINERARY_DAY_VIEWED,
            TripTandemAnalytics.Events.INVITE_CREATED,
            TripTandemAnalytics.Events.INVITE_SHARE_OPENED,
            TripTandemAnalytics.Events.INVITE_ACCEPTED,
            TripTandemAnalytics.Events.INVITE_FAILED,
            TripTandemAnalytics.Events.MEMBER_ROLE_CHANGED,
            TripTandemAnalytics.Events.MEMBER_REMOVED,
            TripTandemAnalytics.Events.MEMBER_LEFT,
            TripTandemAnalytics.Events.OWNERSHIP_TRANSFERRED,
            TripTandemAnalytics.Events.PAYWALL_VIEWED,
            TripTandemAnalytics.Events.PACKAGE_SELECTED,
            TripTandemAnalytics.Events.PURCHASE_STARTED,
            TripTandemAnalytics.Events.PURCHASE_COMPLETED,
            TripTandemAnalytics.Events.PURCHASE_CANCELLED,
            TripTandemAnalytics.Events.PURCHASE_FAILED,
            TripTandemAnalytics.Events.RESTORE_STARTED,
            TripTandemAnalytics.Events.RESTORE_COMPLETED,
            TripTandemAnalytics.Events.ENTITLEMENT_CHANGED,
            TripTandemAnalytics.Events.GENERATION_STARTED,
            TripTandemAnalytics.Events.GENERATION_COMPLETED,
            TripTandemAnalytics.Events.GENERATION_PREVIEW_EDITED,
            TripTandemAnalytics.Events.GENERATION_APPLIED,
            TripTandemAnalytics.Events.GENERATION_FAILED,
            TripTandemAnalytics.Events.GENERATION_PAYWALL_VIEWED,
            TripTandemAnalytics.Events.OFFLINE_CACHE_READ,
            TripTandemAnalytics.Events.OFFLINE_REFRESH_COMPLETED,
            TripTandemAnalytics.Events.EXPORT_STARTED,
            TripTandemAnalytics.Events.EXPORT_COMPLETED,
            TripTandemAnalytics.Events.PROTECTED_CACHE_CLEARED,
        )

        val allowedParameters = mapOf(
            TripTandemAnalytics.Events.OPEN_PUBLISH_STARTED to setOf(),
            TripTandemAnalytics.Events.OPEN_PUBLISH_COMPLETED to setOf("capacity_bucket", "trip_length_bucket"),
            TripTandemAnalytics.Events.DISCOVER_SEARCH_PERFORMED to setOf("result_count_bucket", "filter_count"),
            TripTandemAnalytics.Events.DISCOVER_RESULT_OPENED to setOf("rank_bucket", "reason_count"),
            TripTandemAnalytics.Events.DISCOVER_EMPTY_VIEWED to setOf("filter_count"),
            TripTandemAnalytics.Events.OPEN_TRIP_CLOSED to setOf("reason_class"),
            TripTandemAnalytics.Events.COMPATIBILITY_VIEWED to setOf("label", "reason_count", "missing_field_count"),
            TripTandemAnalytics.Events.JOIN_REQUEST_STARTED to setOf(),
            TripTandemAnalytics.Events.JOIN_REQUEST_SUBMITTED to setOf("intro_length_bucket"),
            TripTandemAnalytics.Events.JOIN_REQUEST_WITHDRAWN to setOf("age_bucket"),
            TripTandemAnalytics.Events.JOIN_REQUEST_REVIEWED to setOf("decision", "age_bucket", "capacity_bucket"),
            TripTandemAnalytics.Events.JOIN_REQUEST_INVALIDATED to setOf("reason_class"),
            TripTandemAnalytics.Events.SAFETY_CONTROL_OPENED to setOf("source"),
            TripTandemAnalytics.Events.BLOCK_COMPLETED to setOf("context"),
            TripTandemAnalytics.Events.REPORT_SUBMITTED to setOf("category", "subject_type"),
            TripTandemAnalytics.Events.PUBLIC_CONTENT_HELD to setOf("reason_class"),
            TripTandemAnalytics.Events.MODERATION_ACTIONED to setOf("action_class", "severity"),
            TripTandemAnalytics.Events.NOTIFICATION_PERMISSION_PROMPTED to setOf("context"),
            TripTandemAnalytics.Events.NOTIFICATION_PERMISSION_RESULT to setOf("result"),
            TripTandemAnalytics.Events.NOTIFICATION_CREATED to setOf("type", "channel"),
            TripTandemAnalytics.Events.NOTIFICATION_OPENED to setOf("type", "age_bucket"),
            TripTandemAnalytics.Events.ACTIVITY_ACTION_COMPLETED to setOf("type"),
            TripTandemAnalytics.Events.NOTIFICATION_PREFERENCE_CHANGED to setOf("category", "enabled"),

            TripTandemAnalytics.Events.SCREEN_VIEW to setOf("screen_name"),
            TripTandemAnalytics.Events.AUTH_ANONYMOUS_FAILED to setOf("error_type"),
            TripTandemAnalytics.Events.SIGN_UP_STARTED to setOf("method"),
            TripTandemAnalytics.Events.SIGN_UP_COMPLETED to setOf("method"),
            TripTandemAnalytics.Events.PROFILE_OPTIONAL_COMPLETED to setOf("field_count_bucket"),
            TripTandemAnalytics.Events.TRIP_CREATE_STARTED to setOf("source"),
            TripTandemAnalytics.Events.TRIP_CREATED to setOf("visibility", "capacity_bucket"),
            TripTandemAnalytics.Events.TRIP_VISIBILITY_CHANGED to setOf("from", "to"),
            TripTandemAnalytics.Events.ITINERARY_ITEM_CREATED to setOf("type", "entry_method"),
            TripTandemAnalytics.Events.ITINERARY_ITEM_UPDATED to setOf("field_group"),
            TripTandemAnalytics.Events.ITINERARY_ITEM_REORDERED to setOf("method"),
            TripTandemAnalytics.Events.ITINERARY_CONFLICT_DETECTED to setOf("conflict_type"),
            TripTandemAnalytics.Events.ITINERARY_DAY_VIEWED to setOf("relative_day_bucket"),
            TripTandemAnalytics.Events.TRIP_CANCELLED to setOf("member_count_bucket"),
            TripTandemAnalytics.Events.INVITE_CREATED to setOf("role", "expiry_bucket"),
            TripTandemAnalytics.Events.INVITE_SHARE_OPENED to setOf("channel_category"),
            TripTandemAnalytics.Events.INVITE_ACCEPTED to setOf("role"),
            TripTandemAnalytics.Events.INVITE_FAILED to setOf("reason_class"),
            TripTandemAnalytics.Events.MEMBER_ROLE_CHANGED to setOf("from", "to"),
            TripTandemAnalytics.Events.PAYWALL_VIEWED to setOf("trigger", "offering_id", "entitlement_state"),
            TripTandemAnalytics.Events.PACKAGE_SELECTED to setOf("package_type"),
            TripTandemAnalytics.Events.PURCHASE_STARTED to setOf("package_type"),
            TripTandemAnalytics.Events.PURCHASE_COMPLETED to setOf("package_type", "trial", "localized_price_bucket", "active"),
            TripTandemAnalytics.Events.PURCHASE_CANCELLED to setOf("package_type"),
            TripTandemAnalytics.Events.PURCHASE_FAILED to setOf("package_type", "error_class"),
            TripTandemAnalytics.Events.RESTORE_COMPLETED to setOf("result_class", "active"),
            TripTandemAnalytics.Events.ENTITLEMENT_CHANGED to setOf("from", "to", "source"),
            TripTandemAnalytics.Events.GENERATION_STARTED to setOf("scope", "entitlement_state", "existing_item_bucket"),
            TripTandemAnalytics.Events.GENERATION_COMPLETED to setOf("scope", "latency_bucket", "result_status", "item_count_bucket"),
            TripTandemAnalytics.Events.GENERATION_APPLIED to setOf("selected_count_bucket", "duplicate_warning"),
            TripTandemAnalytics.Events.GENERATION_FAILED to setOf("failure_class"),
            TripTandemAnalytics.Events.GENERATION_PAYWALL_VIEWED to setOf("trigger"),
            TripTandemAnalytics.Events.OFFLINE_CACHE_READ to setOf("freshness_bucket"),
            TripTandemAnalytics.Events.OFFLINE_REFRESH_COMPLETED to setOf("result"),
            TripTandemAnalytics.Events.EXPORT_STARTED to setOf("format"),
            TripTandemAnalytics.Events.EXPORT_COMPLETED to setOf("format", "included_field_count"),
            TripTandemAnalytics.Events.PROTECTED_CACHE_CLEARED to setOf("reason"),
        )

        val allowedScreenNames = setOf(
            "welcome", "auth", "profile_setup", "trips", "create_trip",
            "itinerary", "members", "invite",
        )
    }
}
