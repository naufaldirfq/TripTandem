package com.triptandem.shared

/**
 * Remote-configurable rollout controls shared by both mobile platforms.
 *
 * All defaults are deliberately false. In particular, community and AI
 * capabilities must fail closed when Remote Config is unavailable, stale, or
 * malformed. The flags are not an authorization boundary; Firestore rules and
 * server-side checks remain authoritative.
 */
data class TripTandemFeatureFlags(
    val aiGenerationEnabled: Boolean = false,
    val openTripPublishingEnabled: Boolean = false,
    val discoveryEnabled: Boolean = false,
    val joinRequestsEnabled: Boolean = false,
    val pushEnabled: Boolean = false,
) {
    val communityDiscoveryEnabled: Boolean
        get() = openTripPublishingEnabled && discoveryEnabled

    companion object {
        val SafeDefaults = TripTandemFeatureFlags()

        const val AI_GENERATION_ENABLED = "ai_generation_enabled"
        const val OPEN_TRIP_PUBLISHING_ENABLED = "open_trip_publishing_enabled"
        const val DISCOVERY_ENABLED = "discovery_enabled"
        const val JOIN_REQUESTS_ENABLED = "join_requests_enabled"
        const val PUSH_ENABLED = "push_enabled"
    }
}
