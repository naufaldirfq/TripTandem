package com.example.triptandem.ui.community

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.triptandem.shared.*
import org.junit.Rule
import org.junit.Test

/**
 * Device-level behavior coverage for the Phase 3 community surface.
 *
 * The fake keeps these tests deterministic and verifies the UI contract
 * independently of Firebase Functions availability. Backend authorization,
 * moderation, retention, and rules are covered by the Functions emulator and
 * rules tests.
 */
class Phase3CommunityTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val enabledFlags = TripTandemFeatureFlags(
        openTripPublishingEnabled = true,
        discoveryEnabled = true,
        joinRequestsEnabled = true,
        pushEnabled = false,
    )

    @Test
    fun discoveryIsClearlyGatedWhenRolloutIsClosed() {
        val repository = Phase3CommunityFake()
        rule.setContent {
            MaterialTheme {
                CommunityScreen(
                    repository = repository,
                    mode = "discover",
                    flags = TripTandemFeatureFlags.SafeDefaults,
                    analytics = NoOpTripTandemAnalytics,
                )
            }
        }

        rule.onNodeWithText("Community trips are not open yet").assertIsDisplayed()
        rule.onNodeWithText("Plan a private trip and invite friends while the community prepares to welcome travelers.").assertIsDisplayed()
        rule.onNodeWithText("Create a private trip").assertIsDisplayed()
    }

    @Test
    fun discoveryReviewAndJoinRequestRespectConsent() {
        val repository = Phase3CommunityFake()
        rule.setContent {
            MaterialTheme {
                CommunityScreen(
                    repository = repository,
                    mode = "discover",
                    flags = enabledFlags,
                    analytics = NoOpTripTandemAnalytics,
                )
            }
        }

        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("Destination: Choose").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Destination: Choose").performClick()
        rule.onNodeWithText("Kyoto", useUnmergedTree = true).performClick()
        rule.onNodeWithText("Find open trips").performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("Review trip").fetchSemanticsNodes().isNotEmpty()
        }

        rule.onNode(hasText("Review trip") and hasClickAction()).performScrollTo().performClick()
        rule.waitUntil(5_000) { repository.operations.contains("detail") }
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("Review this trip").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Compatibility is informational, never a guarantee of safety.").assertIsDisplayed()
        rule.onNodeWithText("Request to join").assertIsNotEnabled()

        rule.onNode(isToggleable()).performScrollTo().performClick()
        rule.onNodeWithText("Request to join").assertIsEnabled()
        rule.onNodeWithText("Request to join").performScrollTo().performClick()
        rule.waitUntil(5_000) { repository.operations.contains("request") }
        rule.onNodeWithText("Request sent. You can withdraw it while pending.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun activityShowsUnreadStateAndMarksAllRead() {
        val repository = Phase3CommunityFake()
        rule.setContent {
            MaterialTheme {
                CommunityScreen(
                    repository = repository,
                    mode = "activity",
                    flags = TripTandemFeatureFlags.SafeDefaults,
                    analytics = NoOpTripTandemAnalytics,
                )
            }
        }

        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("A join request needs your attention").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Unread · Needs attention").assertIsDisplayed()
        rule.onNodeWithText("Mark all read").performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("Read").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Read").assertIsDisplayed()
    }
}

private class Phase3CommunityFake : CommunityRepository {
    val operations = mutableListOf<String>()

    override suspend fun execute(operation: String, input: Map<String, String>): DataResult<String> {
        operations += operation
        return when (operation) {
            "destinations" -> DataResult.Success(
                """{"items":[{"id":"kyoto","label":"Kyoto"}]}""",
            )
            "search" -> DataResult.Success(
                """{"items":[{"tripId":"trip-1","title":"Kyoto in spring","destination":"Kyoto","startDate":"2026-10-10","endDate":"2026-10-14","remainingSpots":"2","pace":"balanced","budgetBand":"moderate","host":{"initials":"MK"},"languages":["English"],"interests":["food"],"expectationNote":"Walk, eat, and explore together.","summary":"Temples and local food.","fit":{"label":"Strong match","reasons":["Shared pace"],"missingFields":[]}}]}""",
            )
            "detail" -> DataResult.Success(
                """{"projection":{"tripId":"trip-1","title":"Kyoto in spring","destination":"Kyoto","startDate":"2026-10-10","endDate":"2026-10-14","remainingSpots":"2","pace":"balanced","budgetBand":"moderate","host":{"initials":"MK"},"languages":["English"],"interests":["food"],"expectationNote":"Walk, eat, and explore together.","summary":"Temples and local food."},"fit":{"label":"Strong match","reasons":["Shared pace"],"missingFields":[]},"sharedProfile":{"initials":"MK","pace":"balanced","budgetBand":"moderate","languages":["English"],"interests":["food"]},"profileHash":"profile-hash"}""",
            )
            "request" -> DataResult.Success("""{"status":"pending"}""")
            "activity" -> DataResult.Success(
                """{"items":[{"id":"activity-1","type":"join_request_received","read":false,"actionable":true,"tripId":"trip-1"}]}""",
            )
            "read" -> DataResult.Success("""{"ok":true}""")
            else -> DataResult.Success("{}")
        }
    }
}
