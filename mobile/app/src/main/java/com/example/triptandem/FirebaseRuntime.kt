package com.triptandem

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.triptandem.data.FirebaseInviteRepository
import com.triptandem.data.FirebaseIdentityRepository
import com.triptandem.data.FirebaseItineraryRepository
import com.triptandem.data.FirebaseItineraryGenerationRepository
import com.triptandem.data.FirebaseTravelerProfileRepository
import com.triptandem.data.FirebaseTripMemberRepository
import com.triptandem.data.FirebaseTripRepository
import com.triptandem.shared.NoOpTripTandemAnalytics
import com.triptandem.shared.TravelerProfileRepository
import com.triptandem.shared.TripTandemAnalytics
import com.triptandem.shared.TripRepository
import com.triptandem.shared.ItineraryRepository
import com.triptandem.shared.TripMemberRepository
import com.triptandem.shared.InviteRepository

/**
 * Android Firebase boundary. Keeping platform SDK objects here prevents the
 * shared KMP UI from depending on Android-only Firebase APIs.
 */
internal class FirebaseRuntime private constructor(
    val analytics: TripTandemAnalytics,
    val auth: FirebaseAuth,
    val firestore: FirebaseFirestore,
    val travelerProfileRepository: TravelerProfileRepository,
    val tripRepository: TripRepository,
    val itineraryRepository: ItineraryRepository,
    val memberRepository: TripMemberRepository,
    val inviteRepository: InviteRepository,
    val generationRepository: com.triptandem.shared.ItineraryGenerationRepository,
    val identityRepository: com.triptandem.shared.IdentityRepository,
    val remoteConfig: RemoteConfigRuntime?,
) {
    fun ensureAnonymousSession(onUserReady: (String) -> Unit = {}) {
        if (auth.currentUser != null) {
            analytics.logEvent(TripTandemAnalytics.Events.AUTH_SESSION_RESTORED)
            auth.currentUser?.uid?.let(onUserReady)
            return
        }

        auth.signInAnonymously()
            .addOnSuccessListener {
                analytics.logEvent(TripTandemAnalytics.Events.AUTH_ANONYMOUS_SUCCEEDED)
                auth.currentUser?.uid?.let(onUserReady)
            }
            .addOnFailureListener { error ->
                Log.w(TAG, "Anonymous Firebase session could not be started", error)
                analytics.logEvent(
                    TripTandemAnalytics.Events.AUTH_ANONYMOUS_FAILED,
                    parameters = mapOf("error_type" to (error::class.simpleName ?: "unknown")),
                )
            }
    }

    companion object {
        private const val TAG = "FirebaseRuntime"

        fun initialize(context: Context): FirebaseRuntime? {
            return runCatching {
                FirebaseApp.initializeApp(context)
                val auth = FirebaseAuth.getInstance()
                val firestore = FirebaseFirestore.getInstance()
                val functions = FirebaseFunctions.getInstance("asia-southeast2")
                FirebaseCrashlytics.getInstance().apply {
                    // Keep diagnostics useful without putting account IDs,
                    // invite tokens, locations, or itinerary text in reports.
                    setCustomKey("platform", "android")
                    setCustomKey("build_type", BuildConfig.BUILD_TYPE)
                }
                FirebaseRuntime(
                    analytics = FirebaseAnalyticsTracker(FirebaseAnalytics.getInstance(context)),
                    auth = auth,
                    firestore = firestore,
                    travelerProfileRepository = FirebaseTravelerProfileRepository(auth, firestore, functions),
                    tripRepository = FirebaseTripRepository(auth, firestore, functions),
                    itineraryRepository = FirebaseItineraryRepository(auth, firestore),
                    memberRepository = FirebaseTripMemberRepository(auth, firestore, functions),
                    inviteRepository = FirebaseInviteRepository(auth, firestore),
                    generationRepository = FirebaseItineraryGenerationRepository(auth, functions),
                    identityRepository = FirebaseIdentityRepository(
                        auth = auth,
                        activity = context as? android.app.Activity,
                    ),
                    remoteConfig = RemoteConfigRuntime.initialize(),
                )
            }.onFailure { error ->
                Log.e(TAG, "Firebase configuration is unavailable", error)
            }.getOrNull()
        }

        fun analyticsOrNoOp(runtime: FirebaseRuntime?): TripTandemAnalytics =
            runtime?.analytics ?: NoOpTripTandemAnalytics
    }
}
