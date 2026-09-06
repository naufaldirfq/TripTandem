package com.triptandem.shared

/**
 * Privacy-safe analytics boundary shared by the Compose UI and each platform.
 *
 * Event parameters must be coarse product metadata only. Never pass itinerary
 * text, exact locations, dates, member biographies, contact information, or
 * other user-generated content through this interface.
 */
interface TripTandemAnalytics {
    fun logEvent(name: String, parameters: Map<String, String> = emptyMap())

    fun logScreen(screenName: String) {
        logEvent(
            Events.SCREEN_VIEW,
            parameters = mapOf("screen_name" to screenName),
        )
    }

    object Events {
        const val SCREEN_VIEW = "screen_view"
        const val AUTH_ANONYMOUS_SUCCEEDED = "auth_anonymous_succeeded"
        const val AUTH_ANONYMOUS_FAILED = "auth_anonymous_failed"
        const val AUTH_SESSION_RESTORED = "auth_session_restored"
        const val SIGN_UP_STARTED = "sign_up_started"
        const val SIGN_UP_COMPLETED = "sign_up_completed"
        const val PROFILE_ESSENTIALS_COMPLETED = "profile_essentials_completed"
        const val PROFILE_OPTIONAL_COMPLETED = "profile_optional_completed"
        const val PROFILE_PREVIEW_OPENED = "profile_preview_opened"
        const val ACCOUNT_DELETION_STARTED = "account_deletion_started"
        const val ACCOUNT_DELETION_COMPLETED = "account_deletion_completed"
        const val TRIP_CREATE_STARTED = "trip_create_started"
        const val TRIP_DRAFT_CONTINUED = "trip_draft_continued"
        const val TRIP_CREATED = "trip_created"
        const val TRIP_CONFLICT_DETECTED = "trip_conflict_detected"
        const val TRIP_VISIBILITY_CHANGED = "trip_visibility_changed"
        const val TRIP_CANCELLED = "trip_cancelled"
        const val TRIP_ARCHIVED = "trip_archived"
        const val TRIP_DELETED = "trip_deleted"
        const val ITINERARY_ITEM_CREATED = "itinerary_item_created"
        const val ITINERARY_ITEM_UPDATED = "itinerary_item_updated"
        const val ITINERARY_ITEM_REORDERED = "itinerary_item_reordered"
        const val ITINERARY_ITEM_DELETED = "itinerary_item_deleted"
        const val ITINERARY_CONFLICT_DETECTED = "itinerary_conflict_detected"
        const val ITINERARY_CONFLICT_RESOLVED = "itinerary_conflict_resolved"
        const val ITINERARY_UNDO_RESTORED = "itinerary_undo_restored"
        const val ITINERARY_DAY_VIEWED = "itinerary_day_viewed"
        const val INVITE_CREATED = "invite_created"
        const val INVITE_SHARE_OPENED = "invite_share_opened"
        const val INVITE_ACCEPTED = "invite_accepted"
        const val INVITE_FAILED = "invite_failed"
        const val MEMBER_ROLE_CHANGED = "member_role_changed"
        const val MEMBER_REMOVED = "member_removed"
        const val MEMBER_LEFT = "member_left"
        const val OWNERSHIP_TRANSFERRED = "ownership_transferred"
        const val PAYWALL_VIEWED = "paywall_viewed"
        const val PACKAGE_SELECTED = "package_selected"
        const val PURCHASE_STARTED = "purchase_started"
        const val PURCHASE_COMPLETED = "purchase_completed"
        const val PURCHASE_CANCELLED = "purchase_cancelled"
        const val PURCHASE_FAILED = "purchase_failed"
        const val RESTORE_STARTED = "restore_started"
        const val RESTORE_COMPLETED = "restore_completed"
        const val ENTITLEMENT_CHANGED = "entitlement_changed"
        const val GENERATION_STARTED = "generation_started"
        const val GENERATION_COMPLETED = "generation_completed"
        const val GENERATION_PREVIEW_EDITED = "generation_preview_edited"
        const val GENERATION_APPLIED = "generation_applied"
        const val GENERATION_FAILED = "generation_failed"
        const val GENERATION_PAYWALL_VIEWED = "generation_paywall_viewed"
    }
}

object NoOpTripTandemAnalytics : TripTandemAnalytics {
    override fun logEvent(name: String, parameters: Map<String, String>) = Unit
}
