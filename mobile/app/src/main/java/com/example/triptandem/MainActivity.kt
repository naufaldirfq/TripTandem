package com.triptandem

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.triptandem.shared.TripTandemApp
import com.triptandem.shared.TripTandemFeatureFlags
import com.triptandem.shared.TripTandemRepositories
import com.triptandem.shared.PendingInvite
import com.triptandem.shared.RevenueCatCoordinator
import com.triptandem.data.AndroidItineraryGenerationDraftRepository
import com.triptandem.data.AndroidTripDraftRepository

class MainActivity : ComponentActivity() {
  private var featureFlags by mutableStateOf(TripTandemFeatureFlags.SafeDefaults)
  private var pendingInvite by mutableStateOf<PendingInvite?>(null)
  private var revenueCatCoordinator by mutableStateOf<RevenueCatCoordinator?>(null)
  private var firebaseRuntime: FirebaseRuntime? = null
  private var connectivityMonitor: AndroidConnectivityMonitor? = null

  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    if (requestCode == 7309) CommunityPushRuntime.permissionResult(grantResults.firstOrNull() == android.content.pm.PackageManager.PERMISSION_GRANTED)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    pendingInvite = intent.toPendingInvite()

    enableEdgeToEdge()
    val connectivity = AndroidConnectivityMonitor(this)
    connectivityMonitor = connectivity
    val firebase = FirebaseRuntime.initialize(this)
    firebaseRuntime = firebase
    firebase?.auth?.addAuthStateListener { firebaseAuth ->
      // Firebase emits a null currentUser between sign-out and the replacement
      // anonymous session. Keep the old RevenueCat coordinator cleared during
      // that gap; dereferencing the user without checking currentUser would
      // crash the sign-out path and could expose the previous account's
      // entitlement to the next one.
      revenueCatCoordinator = firebaseAuth.currentUser?.uid?.let {
        RevenueCatRuntime.initialize(it)
      }
    }
    firebase?.ensureAnonymousSession { userId ->
      revenueCatCoordinator = RevenueCatRuntime.initialize(userId)
    }
    firebase?.remoteConfig?.fetchAndActivate { flags ->
      featureFlags = flags
    }

    if (intent?.getStringExtra("community_activity") != null) com.triptandem.shared.CommunityNavigation.openActivity()
    setContent {
      TripTandemApp(
        typography = tripTandemAndroidTypography(),
        analytics = FirebaseRuntime.analyticsOrNoOp(firebase),
        featureFlags = featureFlags,
        connectivity = connectivity,
        revenueCat = revenueCatCoordinator,
        repositories = androidx.compose.runtime.remember(firebase) { firebase?.let {
            TripTandemRepositories(
                profile = it.travelerProfileRepository,
                trips = it.tripRepository,
                itinerary = it.itineraryRepository,
                members = it.memberRepository,
                invites = it.inviteRepository,
                generation = it.generationRepository,
                community = com.triptandem.data.FirebaseCommunityRepository(com.google.firebase.functions.FirebaseFunctions.getInstance("asia-southeast2"), this@MainActivity),
                identity = it.identityRepository,
                tripDrafts = AndroidTripDraftRepository(this@MainActivity),
                generationDrafts = AndroidItineraryGenerationDraftRepository(this@MainActivity),
                offlineCache = it.offlineCache,
            )
        }
        },
        onShareInvite = { link ->
          startActivity(
            Intent.createChooser(
              Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, link)
              },
              "Share invite link",
            ),
          )
        },
        onShareExport = { summary ->
          startActivity(
            Intent.createChooser(
              Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, summary)
              },
              "Share itinerary",
            ),
          )
        },
        pendingInvite = pendingInvite,
        onSignOut = {
          lifecycleScope.launch {
            CommunityPushRuntime.unregister(this@MainActivity)
            val currentUid = firebase?.auth?.currentUser?.uid
            if (currentUid != null) {
              firebase?.offlineCache?.clearAll(currentUid, reason = "sign_out")
            }
            firebase?.auth?.signOut()
            firebase?.ensureAnonymousSession { userId -> revenueCatCoordinator = RevenueCatRuntime.initialize(userId) }
          }
        },
        onOpenExternalUrl = { url ->
          startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        },
        onManageSubscription = {
          startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/account/subscriptions")))
        },
      )
    }
  }

  override fun onStart() {
    super.onStart()
    // Re-publish a coordinator on every foreground transition. The shared
    // paywall keys its entitlement refresh to this instance, so a store-side
    // renewal/expiration is reflected in the UI instead of refreshing an
    // unobserved coordinator in the background.
    firebaseRuntime?.auth?.currentUser?.uid?.let { userId ->
      revenueCatCoordinator = RevenueCatRuntime.initialize(userId)
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    if (intent.getStringExtra("community_activity") != null) com.triptandem.shared.CommunityNavigation.openActivity()
    setIntent(intent)
    pendingInvite = intent.toPendingInvite()
  }

  override fun onDestroy() {
    connectivityMonitor?.close()
    connectivityMonitor = null
    super.onDestroy()
  }

  private fun Intent.toPendingInvite(): PendingInvite? {
    val link = data ?: return null
    if (link.scheme != "triptandem" || link.host != "join") return null
    val tripId = link.pathSegments.lastOrNull()?.takeIf { it.isNotBlank() } ?: return null
    val token = link.getQueryParameter("token")?.takeIf { it.isNotBlank() } ?: return null
    return PendingInvite(tripId = tripId, shareToken = token)
  }
}
