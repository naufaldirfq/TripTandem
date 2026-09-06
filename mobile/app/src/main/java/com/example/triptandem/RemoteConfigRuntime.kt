package com.triptandem

import android.util.Log
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.triptandem.shared.TripTandemFeatureFlags

/**
 * Android Remote Config boundary. In-app defaults are safe and fail closed;
 * remote values can only opt a feature in after the backend template enables
 * it. Remote Config is a rollout control, not an authorization mechanism.
 */
internal class RemoteConfigRuntime private constructor(
    private val remoteConfig: FirebaseRemoteConfig,
) {
    fun fetchAndActivate(onFlagsReady: (TripTandemFeatureFlags) -> Unit = {}) {
        remoteConfig
            .setDefaultsAsync(R.xml.remote_config_defaults)
            .continueWithTask {
                remoteConfig.fetchAndActivate()
            }
            .addOnSuccessListener {
                onFlagsReady(currentFlags())
            }
            .addOnFailureListener { error ->
                Log.w(TAG, "Remote Config fetch failed; keeping safe defaults", error)
                // A cached true value must not keep a high-risk capability
                // enabled after a failed refresh. Manual/private trip flows
                // remain available while AI/community/push gates fail closed.
                onFlagsReady(TripTandemFeatureFlags.SafeDefaults)
            }
    }

    private fun currentFlags(): TripTandemFeatureFlags = TripTandemFeatureFlags(
        aiGenerationEnabled = parseRemoteConfigBoolean(remoteConfig.getString(TripTandemFeatureFlags.AI_GENERATION_ENABLED)),
        openTripPublishingEnabled = parseRemoteConfigBoolean(remoteConfig.getString(TripTandemFeatureFlags.OPEN_TRIP_PUBLISHING_ENABLED)),
        discoveryEnabled = parseRemoteConfigBoolean(remoteConfig.getString(TripTandemFeatureFlags.DISCOVERY_ENABLED)),
        joinRequestsEnabled = parseRemoteConfigBoolean(remoteConfig.getString(TripTandemFeatureFlags.JOIN_REQUESTS_ENABLED)),
        pushEnabled = parseRemoteConfigBoolean(remoteConfig.getString(TripTandemFeatureFlags.PUSH_ENABLED)),
    )

    companion object {
        private const val TAG = "RemoteConfigRuntime"

        fun initialize(): RemoteConfigRuntime? = runCatching {
            val settings = FirebaseRemoteConfigSettings.Builder()
                .setFetchTimeoutInSeconds(10)
                .setMinimumFetchIntervalInSeconds(if (BuildConfig.DEBUG) 0 else 3600)
                .build()
            FirebaseRemoteConfig.getInstance().apply {
                setConfigSettingsAsync(settings)
            }.let(::RemoteConfigRuntime)
        }.onFailure { error ->
            Log.w(TAG, "Remote Config is unavailable; keeping safe defaults", error)
        }.getOrNull()
    }
}

/** Only an explicit Boolean true may opt a high-risk capability in. */
internal fun parseRemoteConfigBoolean(value: String): Boolean =
    value.trim().equals("true", ignoreCase = true)
