package com.example.triptandem

import com.triptandem.parseRemoteConfigBoolean
import com.triptandem.shared.TripTandemFeatureFlags
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteConfigFeatureFlagsTest {
    @Test
    fun safeDefaultsFailClosed() {
        val flags = TripTandemFeatureFlags.SafeDefaults

        assertFalse(flags.aiGenerationEnabled)
        assertFalse(flags.openTripPublishingEnabled)
        assertFalse(flags.discoveryEnabled)
        assertFalse(flags.joinRequestsEnabled)
        assertFalse(flags.pushEnabled)
        assertFalse(flags.communityDiscoveryEnabled)
    }

    @Test
    fun communityDiscoveryRequiresPublishingAndDiscovery() {
        assertFalse(
            TripTandemFeatureFlags(
                openTripPublishingEnabled = true,
                discoveryEnabled = false,
            ).communityDiscoveryEnabled,
        )
        assertFalse(
            TripTandemFeatureFlags(
                openTripPublishingEnabled = false,
                discoveryEnabled = true,
            ).communityDiscoveryEnabled,
        )
        assertTrue(
            TripTandemFeatureFlags(
                openTripPublishingEnabled = true,
                discoveryEnabled = true,
            ).communityDiscoveryEnabled,
        )
    }

    @Test
    fun malformedRemoteConfigValuesFailClosed() {
        assertTrue(parseRemoteConfigBoolean("true"))
        assertTrue(parseRemoteConfigBoolean(" TRUE "))
        assertFalse(parseRemoteConfigBoolean("1"))
        assertFalse(parseRemoteConfigBoolean("yes"))
        assertFalse(parseRemoteConfigBoolean("false"))
        assertFalse(parseRemoteConfigBoolean(""))
    }
}
