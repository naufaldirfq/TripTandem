package com.triptandem.ui.main

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.File
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.triptandem.shared.*
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class TripTandemAppTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private fun launchApp(withProfile: Boolean = false) {
        val data = TripTandemRepositories.local()
        if (withProfile) runBlocking {
            data.profile.saveCurrentProfile(SaveTravelerProfileInput(displayName = "Maya", homeRegion = "Indonesia", ageConfirmed = true))
        }
        rule.setContent { TripTandemApp(repositories = data) }
        rule.waitForIdle()
    }
    private fun screenshot(name: String) {
        val bitmap = rule.onAllNodes(isRoot()).onLast().captureToImage().asAndroidBitmap()
        File(rule.activity.getExternalFilesDir(null), "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun signupOpensAccountAndAppleIsHiddenOnAndroid() {
        launchApp()
        screenshot("welcome")
        rule.onNodeWithText("Create a free account").performScrollTo().performClick()
        rule.onNodeWithText("Start planning with friends.").assertExists()
        rule.onNodeWithText("Your traveler profile").assertDoesNotExist()
        rule.onNodeWithText("Continue with Google").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Continue with Apple").assertDoesNotExist()
    }
    @Test fun loginOpensLoginMode() {
        launchApp()
        rule.onNodeWithText("Log in").performScrollTo().performClick()
        rule.onNodeWithText("Welcome back, traveler").assertIsDisplayed()
        screenshot("login")
    }
    @Test fun profileTabOpensSettingsThenEditor() {
        launchApp(true)
        screenshot("dashboard")
        rule.onNodeWithText("Profile").performClick()
        rule.onNodeWithText("Account settings").assertIsDisplayed()
        rule.onNodeWithText("Trips").assertIsDisplayed()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("DANGER ZONE"))
        rule.onNodeWithText("DANGER ZONE").assertIsDisplayed()
        rule.onNodeWithContentDescription("Delete account").assertIsDisplayed()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Log out"))
        rule.onNodeWithText("Log out").assertIsDisplayed()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("TripTandem · Phase 1 MVP · v1.0.1"))
        rule.onNodeWithText("TripTandem · Phase 1 MVP · v1.0.1").assertIsDisplayed()
        screenshot("settings")
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Edit profile"))
        rule.onNodeWithText("Edit profile").performClick()
        rule.onNodeWithText("Your traveler profile").assertIsDisplayed()
        rule.onNodeWithText("Tell us how you travel").assertIsDisplayed()
        screenshot("profile")
        rule.onNodeWithText("Back").performClick()
        rule.onNodeWithText("Account settings").assertIsDisplayed()
    }
    @Test fun createTripShowsErrorsThenCreatesThroughThreeSteps() {
        launchApp(true)
        rule.onNodeWithText("Create a new trip").performClick()
        screenshot("create-trip")
        rule.onNodeWithText("Continue").performClick()
        rule.onNodeWithText("Enter a city or region for your trip.").assertExists()
        rule.onNodeWithText("Choose both dates using the calendar.").assertExists()
        rule.onNodeWithText("Choose destination").performClick()
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("Kyoto, Japan")
        rule.onNodeWithText("Use destination").performClick()
        rule.onNodeWithContentDescription("Start date: Choose date").performClick()
        rule.onNodeWithText("18", useUnmergedTree = true).performClick()
        rule.onNodeWithContentDescription("End date: Choose date").performClick()
        rule.onNodeWithText("21", useUnmergedTree = true).performClick()
        rule.onNodeWithText("Continue").performClick()
        rule.onNodeWithText("STEP 2 OF 3").assertExists()
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("Kyoto with friends")
        rule.onNodeWithText("Continue").performClick()
        rule.onNodeWithText("STEP 3 OF 3").assertExists()
        rule.onNodeWithText("Create private trip").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithText("Kyoto with friends").fetchSemanticsNodes().isNotEmpty() }
        screenshot("itinerary")
        rule.onNodeWithText("Itinerary").performClick()
        rule.onNodeWithText("Add to day 1").performClick()
        rule.onNodeWithText("Add to Day 1").assertIsDisplayed()
        screenshot("add-activity")
        rule.onNodeWithText("×").performClick()
        rule.onNodeWithText("Edit").performClick()
        rule.onNodeWithText("Edit trip").assertIsDisplayed()
        screenshot("trip-settings")
        rule.onNodeWithText("Cancel").performClick()
        rule.onNodeWithText("Members").performClick()
        rule.onNodeWithText("1 people").assertIsDisplayed()
        screenshot("members")
    }

    @Test fun profileNavigationCountryPickerAndSignOutConfirmation() {
        launchApp(true)
        rule.onNodeWithText("Profile").performClick()
        rule.onNodeWithText("Trips").performClick()
        rule.onNodeWithText("Welcome back, Maya").assertIsDisplayed()
        rule.onNodeWithText("Profile").performClick()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Log out"))
        rule.onNodeWithText("Log out").performClick()
        rule.onNodeWithText("Log out of TripTandem?").assertIsDisplayed()
        screenshot("sign-out-confirmation")
        rule.onNodeWithText("Stay signed in").performClick()
        rule.onNodeWithContentDescription("Delete my account").performScrollTo().performClick()
        rule.onNodeWithText("Delete my account?").assertIsDisplayed()
        rule.onNodeWithText("Keep account").performClick()
        rule.waitForIdle()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Edit profile"))
        rule.onNodeWithText("Edit profile").assertIsDisplayed()
        rule.onNodeWithText("Edit profile").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Your traveler profile").assertIsDisplayed()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Home region"))
        rule.onNodeWithContentDescription("Home region: Indonesia").performClick()
        rule.onNodeWithText("Choose your country").assertIsDisplayed()
        rule.onAllNodes(hasSetTextAction()).onLast().performTextInput("Japan")
        screenshot("country-picker")
        rule.onAllNodesWithText("Japan").onLast().performClick()
        rule.onNodeWithContentDescription("Home region: Japan").assertExists()
    }

    @Test fun tripTabsAndTimePickerUseExistingSchedule() {
        val data = TripTandemRepositories.local()
        runBlocking {
            data.profile.saveCurrentProfile(SaveTravelerProfileInput(displayName = "Maya", homeRegion = "Indonesia", ageConfirmed = true))
            val trip = (data.trips.createTrip(CreateTripInput("Lisbon long weekend", "Lisbon, Portugal", "2026-09-18", "2026-09-21", destinationTimezone = "Europe/Lisbon")) as DataResult.Success).value
            data.itinerary.createItem(trip.id, CreateItineraryItemInput(type = ItineraryItemType.Meal, title = "Pastéis & coffee", startTimeEpochMillis = localDateTimeToEpochMillis("2026-09-18", "09:00", "Europe/Lisbon"), startTimeLabel = "09:00", durationMinutes = 60, dayDate = "2026-09-18", flexibleTime = false, place = "Belém", position = 0))
        }
        rule.setContent { TripTandemApp(repositories = data) }
        rule.waitForIdle()
        rule.onNodeWithText("Lisbon long weekend").performClick()
        rule.onNodeWithText("DESTINATION").assertIsDisplayed()
        screenshot("trip-overview")
        rule.onNodeWithText("Itinerary").performClick()
        rule.onNodeWithText("Pastéis & coffee").assertExists()
        screenshot("trip-timeline")
        rule.onNodeWithText("Add to day 1").performClick()
        rule.onNodeWithText("This overlaps", substring = true).assertExists()
        rule.onNodeWithText("Add to itinerary  →").assertIsNotEnabled()
        screenshot("activity-form")
        rule.onNodeWithText("09:00", useUnmergedTree = true).performClick()
        rule.onNodeWithText("Start time").assertIsDisplayed()
        screenshot("time-picker")
        rule.onNodeWithText("Cancel").performClick()
        rule.onNodeWithText("×").performClick()
        rule.onNodeWithText("19").performClick()
        rule.onNodeWithText("No plans yet.", substring = true).assertExists()
        screenshot("empty-day")
        rule.onNodeWithText("Members").performClick()
        rule.onNodeWithText("Invite more people").assertExists()
        screenshot("trip-members")
    }
}
