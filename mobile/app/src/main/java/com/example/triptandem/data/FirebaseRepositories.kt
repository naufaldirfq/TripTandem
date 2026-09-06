package com.triptandem.data

import android.app.Activity
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.FirebaseAuthWebException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.triptandem.shared.CreateInviteInput
import com.triptandem.shared.CreateItineraryItemInput
import com.triptandem.shared.BudgetBand
import com.triptandem.shared.AccountStatus
import com.triptandem.shared.AuthSession
import com.triptandem.shared.CreateTripInput
import com.triptandem.shared.DataResult
import com.triptandem.shared.InviteLink
import com.triptandem.shared.InviteRepository
import com.triptandem.shared.ItineraryItem
import com.triptandem.shared.ItineraryItemStatus
import com.triptandem.shared.ItineraryItemType
import com.triptandem.shared.ItineraryRepository
import com.triptandem.shared.ItineraryVisibility
import com.triptandem.shared.MembershipStatus
import com.triptandem.shared.SaveTravelerProfileInput
import com.triptandem.shared.TravelerProfile
import com.triptandem.shared.TripPace
import com.triptandem.shared.TripMember
import com.triptandem.shared.TripMemberRole
import com.triptandem.shared.TripMemberRepository
import com.triptandem.shared.TripRecord
import com.triptandem.shared.TripRepository
import com.triptandem.shared.TripStatus
import com.triptandem.shared.TripTandemError
import com.triptandem.shared.TripVisibility
import com.triptandem.shared.TravelerProfileRepository
import com.triptandem.shared.IdentityRepository
import com.triptandem.shared.UpdateItineraryItemInput
import com.triptandem.shared.UpdateTripInput
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Date

private const val USERS_COLLECTION = "users"
private const val TRIPS_COLLECTION = "trips"
private const val MEMBERS_SUBCOLLECTION = "members"
private const val ITINERARY_SUBCOLLECTION = "itinerary"
private const val INVITES_SUBCOLLECTION = "invites"

/** Firebase Auth adapter. Anonymous sessions can be upgraded without losing
 * the profile/trips created during the first-use flow. */
class FirebaseIdentityRepository(
    private val auth: FirebaseAuth,
    private val activity: Activity?,
) : IdentityRepository {
    private val credentialManager: CredentialManager? by lazy {
        activity?.let(CredentialManager::create)
    }

    override suspend fun currentSession(): DataResult<AuthSession?> =
        DataResult.Success(auth.currentUser?.toAuthSession())

    override suspend fun createOrLinkEmail(email: String, password: String): DataResult<AuthSession> {
        if (!validEmail(email)) return DataResult.Failure(TripTandemError.Validation("email"))
        if (password.length < 6) return DataResult.Failure(TripTandemError.Validation("password"))
        return repositoryCall {
            val user = auth.currentUser
            val credential = EmailAuthProvider.getCredential(email.trim(), password)
            val result = if (user?.isAnonymous == true) {
                user.linkWithCredential(credential).await()
            } else {
                auth.createUserWithEmailAndPassword(email.trim(), password).await()
            }
            result.user?.toAuthSession() ?: throw IllegalStateException("auth user missing")
        }
    }

    override suspend fun signInWithEmail(email: String, password: String): DataResult<AuthSession> {
        if (!validEmail(email)) return DataResult.Failure(TripTandemError.Validation("email"))
        if (password.length < 6) return DataResult.Failure(TripTandemError.Validation("password"))
        return repositoryCall {
            auth.signInWithEmailAndPassword(email.trim(), password).await().user?.toAuthSession()
                ?: throw IllegalStateException("auth user missing")
        }
    }

    override suspend fun signInWithGoogle(): DataResult<AuthSession> {
        val host = activity ?: return DataResult.Failure(TripTandemError.Unknown("auth_unavailable"))
        val manager = credentialManager ?: return DataResult.Failure(TripTandemError.Unknown("auth_unavailable"))
        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                // This is the web/server client ID generated from google-services.json,
                // not the Android OAuth client ID.
                .setServerClientId(host.getString(com.triptandem.R.string.default_web_client_id))
                .setFilterByAuthorizedAccounts(false)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            val result = manager.getCredential(host, request)
            val credential = result.credential
            if (credential !is CustomCredential ||
                credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                throw IllegalStateException("google_credential_unavailable")
            }
            val googleCredential = try {
                GoogleIdTokenCredential.createFrom(credential.data)
            } catch (_: GoogleIdTokenParsingException) {
                throw IllegalStateException("google_token_invalid")
            }
            val firebaseCredential = GoogleAuthProvider.getCredential(googleCredential.idToken, null)
            val current = auth.currentUser
            val authResult = if (current?.isAnonymous == true) {
                current.linkWithCredential(firebaseCredential).await()
            } else {
                auth.signInWithCredential(firebaseCredential).await()
            }
            DataResult.Success(
                authResult.user?.toAuthSession()
                    ?: throw IllegalStateException("auth user missing"),
            )
        } catch (_: GetCredentialCancellationException) {
            DataResult.Failure(TripTandemError.AuthCancelled)
        } catch (error: Throwable) {
            DataResult.Failure(error.toTripTandemError())
        }
    }

    override suspend fun signInWithApple(): DataResult<AuthSession> {
        val host = activity ?: return DataResult.Failure(TripTandemError.Unknown("auth_unavailable"))
        return try {
            val provider = OAuthProvider.newBuilder("apple.com").apply {
                setScopes(listOf("email", "name"))
            }.build()
            // Browser-based OAuth can outlive the Activity. Firebase retains a
            // pending result so a resumed app can finish the original request.
            val authResult = auth.pendingAuthResult?.await()
                ?: auth.currentUser?.takeIf { it.isAnonymous }
                    ?.startActivityForLinkWithProvider(host, provider)?.await()
                ?: auth.startActivityForSignInWithProvider(host, provider).await()
            DataResult.Success(
                authResult.user?.toAuthSession()
                    ?: throw IllegalStateException("auth user missing"),
            )
        } catch (error: Throwable) {
            DataResult.Failure(error.toTripTandemError())
        }
    }

    override suspend fun signOut(): DataResult<Unit> = try {
        auth.signOut()
        DataResult.Success(Unit)
    } catch (error: Throwable) {
        DataResult.Failure(error.toTripTandemError())
    }
}

private fun FirebaseUser.toAuthSession(): AuthSession = AuthSession(
    uid = uid,
    isAnonymous = isAnonymous,
    email = email,
)

private fun validEmail(value: String): Boolean = value.trim().length in 3..160 &&
    value.count { it == '@' } == 1 && value.substringAfter('@').contains('.')

/** Android adapter for the private profile document in the Phase 0 rules. */
class FirebaseTravelerProfileRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
) : TravelerProfileRepository {
    override suspend fun getCurrentProfile(): DataResult<TravelerProfile?> {
        val uid = auth.currentUser?.uid ?: return DataResult.Failure(TripTandemError.Unauthenticated)
        return repositoryCall {
            val snapshot = firestore.collection(USERS_COLLECTION).document(uid).get().await()
            if (!snapshot.exists()) null else snapshot.toTravelerProfile()
        }
    }

    override suspend fun saveCurrentProfile(input: SaveTravelerProfileInput): DataResult<TravelerProfile> {
        val uid = auth.currentUser?.uid ?: return DataResult.Failure(TripTandemError.Unauthenticated)
        input.validationError()?.let { return DataResult.Failure(it) }
        return repositoryCall {
            val reference = firestore.collection(USERS_COLLECTION).document(uid)
            val existing = reference.get().await()
            if (existing.exists()) {
                // Consent, account status, UID, and creation time are
                // immutable under firestore.rules. Updating the full create
                // payload would rewrite consentedAt/accountStatus and make
                // every subsequent profile edit fail closed.
                reference.update(input.toMutableFirestore()).await()
            } else {
                reference.set(input.toFirestore(uid, FieldValue.serverTimestamp())).await()
            }
            reference.get().await().toTravelerProfile()
        }
    }

    override suspend fun deleteCurrentAccount(): DataResult<Unit> {
        val user = auth.currentUser ?: return DataResult.Failure(TripTandemError.Unauthenticated)
        return repositoryCall {
            val lastSignIn = user.metadata?.lastSignInTimestamp
            if (lastSignIn != null && System.currentTimeMillis() - lastSignIn > RECENT_AUTH_WINDOW_MILLIS) {
                throw RecentLoginRequiredException
            }
            val ownedTrips = firestore.collection(TRIPS_COLLECTION)
                .whereEqualTo("ownerId", user.uid)
                .get().await().documents
            if (ownedTrips.any { document ->
                    val status = document.getString("status")
                    val memberCount = document.getLong("activeMemberCount")?.toInt() ?: 1
                    memberCount > 1 && status in ACTIVE_TRIP_STATUSES
                }) {
                // Deleting an account must not strand collaborators. The owner
                // must transfer ownership or delete/cancel the shared trip first.
                throw OwnershipRequiredException
            }
            // Firestore parent deletes do not cascade into subcollections. The
            // authenticated server boundary owns trip-graph cleanup, metadata
            // cleanup, and the guarded profile delete as one account operation.
            functions.getHttpsCallable("deleteAccountProfile")
                .call(mapOf<String, Any>())
                .await()
            try {
                user.delete().await()
            } catch (error: FirebaseAuthRecentLoginRequiredException) {
                throw error
            }
        }
    }
}

/** Android adapter for owner-controlled trip documents in the Phase 0 rules. */
class FirebaseTripRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
) : TripRepository {
    override suspend fun listMyTrips(): DataResult<List<TripRecord>> {
        val uid = auth.currentUser?.uid ?: return DataResult.Failure(TripTandemError.Unauthenticated)
        return repositoryCall {
            val owned = firestore.collection(TRIPS_COLLECTION)
                .whereEqualTo("ownerId", uid)
                .get()
                .await()
                .documents
            val memberTripIds = firestore.collectionGroup(MEMBERS_SUBCOLLECTION)
                .whereEqualTo("userId", uid)
                .whereEqualTo("status", MembershipStatus.Active.wireValue)
                .get()
                .await()
                .documents
                .mapNotNull { it.reference.parent.parent?.id }
            val tripIds = (owned.map { it.id } + memberTripIds).distinct()
            tripIds.mapNotNull { id ->
                val snapshot = if (owned.any { it.id == id }) owned.first { it.id == id }
                else firestore.collection(TRIPS_COLLECTION).document(id).get().await()
                snapshot.takeIf { it.exists() }?.toTripRecord(id)
            }
        }
    }

    override suspend fun createTrip(input: CreateTripInput): DataResult<TripRecord> {
        val uid = auth.currentUser?.uid ?: return DataResult.Failure(TripTandemError.Unauthenticated)
        input.validationError()?.let { return DataResult.Failure(it) }
        return repositoryCall {
            // Active-trip creation is intentionally routed through the
            // server transaction. It evaluates the owner's existing active
            // trips and verified RevenueCat webhook state atomically; the
            // Firestore client cannot bypass the free-plan limit.
            val result = functions.getHttpsCallable("createTrip")
                .call(input.toCallableMap())
                .await()
            val map = result.data as? Map<*, *> ?: throw IllegalStateException("invalid_trip_response")
            val tripId = map["tripId"] as? String ?: throw IllegalStateException("trip_id_missing")
            val reference = firestore.collection(TRIPS_COLLECTION).document(tripId)
            val snapshot = reference.get().await()
            if (!snapshot.exists()) throw MissingDocumentException
            snapshot.toTripRecord(tripId)
        }
    }

    override suspend fun getTrip(tripId: String): DataResult<TripRecord> {
        if (tripId.isBlank()) return DataResult.Failure(TripTandemError.Validation("tripId"))
        if (auth.currentUser == null) return DataResult.Failure(TripTandemError.Unauthenticated)
        return repositoryCall {
            val snapshot = firestore.collection(TRIPS_COLLECTION).document(tripId).get().await()
            if (!snapshot.exists()) throw MissingDocumentException
            snapshot.toTripRecord(tripId)
        }
    }

    override suspend fun updateTrip(tripId: String, input: UpdateTripInput): DataResult<TripRecord> {
        if (tripId.isBlank()) return DataResult.Failure(TripTandemError.Validation("tripId"))
        if (auth.currentUser == null) return DataResult.Failure(TripTandemError.Unauthenticated)
        input.validationError()?.let { return DataResult.Failure(it) }
        return repositoryCall {
            // Trip updates go through the same regional transaction as create:
            // status reactivation, capacity expansion, and the revision check
            // cannot be bypassed by a direct Firestore write.
            val result = functions.getHttpsCallable("updateTrip")
                .call(
                    mapOf(
                        "tripId" to tripId,
                        "expectedRevision" to input.expectedRevision,
                        "trip" to input.toCallableTripMap(),
                    ),
                )
                .await()
            val map = result.data as? Map<*, *> ?: throw IllegalStateException("invalid_trip_response")
            val updatedTripId = map["tripId"] as? String ?: tripId
            if (updatedTripId != tripId) throw IllegalStateException("trip_id_mismatch")
            val reference = firestore.collection(TRIPS_COLLECTION).document(tripId)
            reference.get().await().toTripRecord(updatedTripId)
        }
    }

    override suspend fun deleteTrip(tripId: String): DataResult<Unit> {
        if (tripId.isBlank()) return DataResult.Failure(TripTandemError.Validation("tripId"))
        if (auth.currentUser == null) return DataResult.Failure(TripTandemError.Unauthenticated)
        return repositoryCall {
            // Firestore parent deletes do not cascade into the trip graph. Use
            // the regional callable so the server locks the trip, drains all
            // known subcollections, and only then removes the root.
            val result = functions.getHttpsCallable("deleteTrip")
                .call(mapOf("tripId" to tripId))
                .await()
            val map = result.data as? Map<*, *> ?: throw IllegalStateException("invalid_trip_response")
            val returnedTripId = map["tripId"] as? String ?: throw IllegalStateException("trip_id_missing")
            if (returnedTripId != tripId) throw IllegalStateException("trip_id_mismatch")
        }
    }
}

private object MissingDocumentException : IllegalStateException("Firestore document does not exist")
private object UndoExpiredException : IllegalStateException("itinerary undo window expired")
private object OwnershipRequiredException : IllegalStateException("transfer ownership before deleting account")
private object RecentLoginRequiredException : IllegalStateException("recent authentication required")
private const val RECENT_AUTH_WINDOW_MILLIS = 5 * 60 * 1000L
private val ACTIVE_TRIP_STATUSES = setOf("draft", "planning", "confirmed")

private suspend fun <T> repositoryCall(block: suspend () -> T): DataResult<T> = try {
    DataResult.Success(block())
} catch (error: MissingDocumentException) {
    DataResult.Failure(TripTandemError.NotFound)
} catch (error: InviteRejectedException) {
    DataResult.Failure(TripTandemError.PermissionDenied)
} catch (error: InviteExpiredException) {
    DataResult.Failure(TripTandemError.InviteExpired)
} catch (error: InviteRevokedException) {
    DataResult.Failure(TripTandemError.InviteRevoked)
} catch (error: InviteCapacityException) {
    DataResult.Failure(TripTandemError.CapacityReached)
} catch (error: UndoExpiredException) {
    DataResult.Failure(TripTandemError.NotFound)
} catch (error: OwnershipRequiredException) {
    DataResult.Failure(TripTandemError.Validation("ownership"))
} catch (error: RecentLoginRequiredException) {
    DataResult.Failure(TripTandemError.ReauthenticationRequired)
} catch (error: Throwable) {
    DataResult.Failure(error.toTripTandemError())
}

private fun Throwable.toTripTandemError(): TripTandemError {
    if (this is FirebaseAuthRecentLoginRequiredException) {
        return TripTandemError.ReauthenticationRequired
    }
    if (this is FirebaseAuthInvalidCredentialsException || this is FirebaseAuthWeakPasswordException) {
        return TripTandemError.Validation("credentials")
    }
    if (this is FirebaseAuthUserCollisionException) {
        return TripTandemError.AuthConflict
    }
    if (this is FirebaseAuthWebException && errorCode == "ERROR_WEB_CONTEXT_CANCELED") {
        return TripTandemError.AuthCancelled
    }
    if (this is com.google.firebase.auth.FirebaseAuthException &&
        errorCode == "ERROR_NETWORK_REQUEST_FAILED"
    ) return TripTandemError.Offline
    if (this is FirebaseFirestoreException) {
        return when (code.name) {
            "PERMISSION_DENIED" -> TripTandemError.PermissionDenied
            "NOT_FOUND" -> TripTandemError.NotFound
            "ABORTED", "FAILED_PRECONDITION" -> TripTandemError.Conflict
            "UNAVAILABLE", "DEADLINE_EXCEEDED" -> TripTandemError.Offline
            "INVALID_ARGUMENT" -> TripTandemError.Validation()
            else -> TripTandemError.Unknown(code.name)
        }
    }
    if (this is FirebaseFunctionsException) {
        return when (code) {
            FirebaseFunctionsException.Code.UNAUTHENTICATED -> TripTandemError.Unauthenticated
            FirebaseFunctionsException.Code.PERMISSION_DENIED -> TripTandemError.PermissionDenied
            FirebaseFunctionsException.Code.NOT_FOUND -> TripTandemError.NotFound
            FirebaseFunctionsException.Code.INVALID_ARGUMENT -> TripTandemError.Validation()
            FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> TripTandemError.QuotaExceeded
            FirebaseFunctionsException.Code.FAILED_PRECONDITION -> {
                if (message?.contains("ownership_required", ignoreCase = true) == true) {
                    TripTandemError.Validation("ownership")
                } else if (message?.contains("organizer_pro_required", ignoreCase = true) == true) {
                    TripTandemError.EntitlementRequired
                } else {
                    generationFailureFromFunctionsMessage(message)
                        ?: TripTandemError.Conflict
                }
            }
            FirebaseFunctionsException.Code.ABORTED -> TripTandemError.Conflict
            FirebaseFunctionsException.Code.UNAVAILABLE,
            FirebaseFunctionsException.Code.DEADLINE_EXCEEDED,
            -> TripTandemError.Offline
            else -> TripTandemError.Unknown(code.name)
        }
    }
    return if (message?.contains("network", ignoreCase = true) == true) {
        TripTandemError.Offline
    } else {
        TripTandemError.Unknown(javaClass.simpleName)
    }
}

/** Maps only the server's documented, coarse generation failure messages. */
private fun generationFailureFromFunctionsMessage(message: String?): TripTandemError? {
    val normalized = message?.lowercase() ?: return null
    val failureClass = when {
        "ai_generation_disabled" in normalized -> "feature_disabled"
        "provider_unconfigured" in normalized -> "provider_unconfigured"
        "no_valid_suggestions" in normalized -> "no_valid_suggestions"
        "invalid_provider_output" in normalized -> "invalid_provider_output"
        "access_revoked" in normalized -> "access_revoked"
        "provider_timeout" in normalized -> "provider_timeout"
        "provider_rate_limited" in normalized -> "provider_rate_limited"
        "provider_error" in normalized -> "provider_error"
        "expired" in normalized -> "expired"
        "cancelled" in normalized -> "cancelled"
        else -> return null
    }
    return TripTandemError.GenerationFailed(failureClass)
}

private fun SaveTravelerProfileInput.toFirestore(uid: String, createdAt: Any): Map<String, Any> = buildMap {
    put("uid", uid)
    put("displayName", displayName.trim())
    avatarUrl?.trim()?.takeIf { it.isNotEmpty() }?.let { put("avatarUrl", it) }
    bio?.trim()?.takeIf { it.isNotEmpty() }?.let { put("bio", it) }
    put("homeRegion", homeRegion?.trim().orEmpty())
    put("primaryLanguage", primaryLanguage.trim().ifEmpty { "English" })
    put("ageConfirmed", ageConfirmed)
    pace?.let { put("pace", it.wireValue) }
    budgetBand?.let { put("budgetBand", it.wireValue) }
    put("visibility", visibility.wireValue)
    put("additionalLanguages", additionalLanguages.distinct().take(5))
    put("interests", interests.distinct().take(12))
    put("termsVersion", termsVersion.trim())
    put("privacyVersion", privacyVersion.trim())
    put("consentedAt", FieldValue.serverTimestamp())
    put("accountStatus", AccountStatus.Active.wireValue)
    put("createdAt", createdAt)
    put("updatedAt", FieldValue.serverTimestamp())
}

/** Fields allowed to change after onboarding; immutable consent metadata stays server-owned. */
private fun SaveTravelerProfileInput.toMutableFirestore(): Map<String, Any> = buildMap {
    put("displayName", displayName.trim())
    put("avatarUrl", avatarUrl?.trim()?.takeIf { it.isNotEmpty() } ?: FieldValue.delete())
    put("bio", bio?.trim()?.takeIf { it.isNotEmpty() } ?: FieldValue.delete())
    put("homeRegion", homeRegion?.trim().orEmpty())
    put("primaryLanguage", primaryLanguage.trim().ifEmpty { "English" })
    pace?.let { put("pace", it.wireValue) } ?: put("pace", FieldValue.delete())
    budgetBand?.let { put("budgetBand", it.wireValue) } ?: put("budgetBand", FieldValue.delete())
    put("visibility", visibility.wireValue)
    put("additionalLanguages", additionalLanguages.distinct().take(5))
    put("interests", interests.distinct().take(12))
    put("updatedAt", FieldValue.serverTimestamp())
}

private fun CreateTripInput.toCallableMap(): Map<String, Any?> = buildMap {
    put("title", title.trim())
    put("destination", destination.trim())
    put("startDate", startDate)
    put("endDate", endDate)
    put("destinationTimezone", destinationTimezone)
    put("datesFlexible", datesFlexible)
    put("visibility", visibility.wireValue)
    put("capacity", capacity)
    put("status", status.wireValue)
    currency?.trim()?.takeIf { it.isNotEmpty() }?.let { put("currency", it) }
    budgetBand?.let { put("budgetBand", it.wireValue) }
    pace?.let { put("pace", it.wireValue) }
    expectationNote?.trim()?.takeIf { it.isNotEmpty() }?.let { put("expectationNote", it) }
    put("interests", interests.distinct().take(12))
    coverColor?.trim()?.takeIf { it.isNotEmpty() }?.let { put("coverColor", it) }
}

private fun UpdateTripInput.toCallableTripMap(): Map<String, Any?> = buildMap {
    put("title", title.trim())
    put("destination", destination.trim())
    put("startDate", startDate)
    put("endDate", endDate)
    put("destinationTimezone", destinationTimezone)
    put("datesFlexible", datesFlexible)
    put("visibility", visibility.wireValue)
    put("capacity", capacity)
    put("status", status.wireValue)
    currency?.trim()?.takeIf { it.isNotEmpty() }?.let { put("currency", it) }
    budgetBand?.let { put("budgetBand", it.wireValue) }
    pace?.let { put("pace", it.wireValue) }
    expectationNote?.trim()?.takeIf { it.isNotEmpty() }?.let { put("expectationNote", it) }
    put("interests", interests.distinct().take(12))
    coverColor?.trim()?.takeIf { it.isNotEmpty() }?.let { put("coverColor", it) }
}

private fun SaveTravelerProfileInput.validationError(): TripTandemError? = when {
    displayName.trim().length !in 2..40 -> TripTandemError.Validation("displayName")
    !ageConfirmed -> TripTandemError.Validation("ageConfirmed")
    avatarUrl?.let { it.length !in 1..2048 } == true -> TripTandemError.Validation("avatarUrl")
    bio?.let { it.length > 240 } == true -> TripTandemError.Validation("bio")
    homeRegion?.trim()?.length?.let { it !in 1..80 } ?: true -> TripTandemError.Validation("homeRegion")
    primaryLanguage.trim().length !in 2..80 -> TripTandemError.Validation("primaryLanguage")
    additionalLanguages.size > 5 -> TripTandemError.Validation("additionalLanguages")
    interests.size > 12 -> TripTandemError.Validation("interests")
    termsVersion.trim().length !in 1..40 -> TripTandemError.Validation("termsVersion")
    privacyVersion.trim().length !in 1..40 -> TripTandemError.Validation("privacyVersion")
    else -> null
}

private fun CreateTripInput.validationError(): TripTandemError? = when {
    title.trim().length !in 2..120 -> TripTandemError.Validation("title")
    destination.trim().length !in 1..160 -> TripTandemError.Validation("destination")
    !validIsoDate(startDate) || !validIsoDate(endDate) || startDate > endDate || tripLengthInDays(startDate, endDate) !in 1..60 -> TripTandemError.Validation("dates")
    destinationTimezone.trim().length !in 1..80 -> TripTandemError.Validation("destinationTimezone")
    capacity !in 2..12 -> TripTandemError.Validation("capacity")
    currency?.let { !it.matches(Regex("[A-Z]{3}")) } == true -> TripTandemError.Validation("currency")
    expectationNote?.length?.let { it > 500 } == true -> TripTandemError.Validation("expectationNote")
    interests.size > 12 -> TripTandemError.Validation("interests")
    coverColor?.let { !it.matches(Regex("#[0-9A-Fa-f]{6}")) } == true -> TripTandemError.Validation("coverColor")
    else -> null
}

private fun UpdateTripInput.validationError(): TripTandemError? = when {
    title.trim().length !in 2..120 -> TripTandemError.Validation("title")
    destination.trim().length !in 1..160 -> TripTandemError.Validation("destination")
    !validIsoDate(startDate) || !validIsoDate(endDate) || startDate > endDate || tripLengthInDays(startDate, endDate) !in 1..60 -> TripTandemError.Validation("dates")
    destinationTimezone.trim().length !in 1..80 -> TripTandemError.Validation("destinationTimezone")
    capacity !in 2..12 -> TripTandemError.Validation("capacity")
    currency?.let { !it.matches(Regex("[A-Z]{3}")) } == true -> TripTandemError.Validation("currency")
    expectationNote?.length?.let { it > 500 } == true -> TripTandemError.Validation("expectationNote")
    interests.size > 12 -> TripTandemError.Validation("interests")
    coverColor?.let { !it.matches(Regex("#[0-9A-Fa-f]{6}")) } == true -> TripTandemError.Validation("coverColor")
    else -> null
}

private fun validIsoDate(value: String): Boolean = parseIsoDate(value) != null

private fun tripLengthInDays(start: String, end: String): Int {
    val first = parseIsoDate(start) ?: return 0
    val last = parseIsoDate(end) ?: return 0
    return ordinal(last) - ordinal(first) + 1
}

private data class IsoDate(val year: Int, val month: Int, val day: Int)

private fun parseIsoDate(value: String): IsoDate? {
    if (value.length != 10 || value[4] != '-' || value[7] != '-') return null
    val parts = value.split('-')
    if (parts.size != 3) return null
    val date = runCatching { IsoDate(parts[0].toInt(), parts[1].toInt(), parts[2].toInt()) }.getOrNull() ?: return null
    val daysInMonth = when (date.month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if (date.year % 4 == 0 && (date.year % 100 != 0 || date.year % 400 == 0)) 29 else 28
        else -> 0
    }
    return date.takeIf { it.year in 1..9999 && it.day in 1..daysInMonth }
}

private fun ordinal(date: IsoDate): Int {
    var year = date.year
    if (date.month <= 2) year--
    val era = year / 400
    val yearOfEra = year - era * 400
    val monthPrime = date.month + if (date.month > 2) -3 else 9
    val dayOfYear = (153 * monthPrime + 2) / 5 + date.day - 1
    val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
    return era * 146097 + dayOfEra
}

private fun com.google.firebase.firestore.DocumentSnapshot.toTravelerProfile(): TravelerProfile {
    return TravelerProfile(
        uid = getString("uid") ?: error("profile uid is missing"),
        displayName = getString("displayName") ?: error("profile displayName is missing"),
        avatarUrl = getString("avatarUrl"),
        bio = getString("bio"),
        homeRegion = getString("homeRegion"),
        primaryLanguage = getString("primaryLanguage") ?: "English",
        ageConfirmed = getBoolean("ageConfirmed") ?: false,
        pace = getString("pace")?.let(::paceFromWire),
        budgetBand = getString("budgetBand")?.let(::budgetBandFromWire),
        visibility = getString("visibility")?.let(::profileVisibilityFromWire) ?: com.triptandem.shared.ProfileVisibility.Private,
        additionalLanguages = (get("additionalLanguages") as? List<*>)?.filterIsInstance<String>().orEmpty(),
        interests = (get("interests") as? List<*>)?.filterIsInstance<String>().orEmpty(),
        termsVersion = getString("termsVersion") ?: com.triptandem.shared.PolicyVersions.TERMS,
        privacyVersion = getString("privacyVersion") ?: com.triptandem.shared.PolicyVersions.PRIVACY,
        consentedAtEpochMillis = getTimestamp("consentedAt")?.toEpochMillis(),
        accountStatus = getString("accountStatus")?.let(::accountStatusFromWire) ?: AccountStatus.Active,
        createdAtEpochMillis = getTimestamp("createdAt")?.toEpochMillis(),
        updatedAtEpochMillis = getTimestamp("updatedAt")?.toEpochMillis(),
    )
}

private fun com.google.firebase.firestore.DocumentSnapshot.toTripRecord(id: String): TripRecord {
    return TripRecord(
        id = id,
        ownerId = getString("ownerId") ?: error("trip ownerId is missing"),
        title = getString("title") ?: error("trip title is missing"),
        destination = getString("destination") ?: error("trip destination is missing"),
        startDate = getString("startDate") ?: error("trip startDate is missing"),
        endDate = getString("endDate") ?: error("trip endDate is missing"),
        destinationTimezone = getString("destinationTimezone") ?: "UTC",
        datesFlexible = getBoolean("datesFlexible") ?: false,
        visibility = visibilityFromWire(getString("visibility")),
        capacity = getLong("capacity")?.toInt() ?: error("trip capacity is missing"),
        status = statusFromWire(getString("status")),
        currency = getString("currency"),
        budgetBand = getString("budgetBand")?.let(::budgetBandFromWire),
        pace = getString("pace")?.let(::paceFromWire),
        expectationNote = getString("expectationNote"),
        revision = getLong("revision")?.toInt() ?: 0,
        activeMemberCount = getLong("activeMemberCount")?.toInt() ?: 1,
        interests = (get("interests") as? List<*>)?.filterIsInstance<String>().orEmpty(),
        coverColor = getString("coverColor"),
        createdAtEpochMillis = getTimestamp("createdAt")?.toEpochMillis(),
        updatedAtEpochMillis = getTimestamp("updatedAt")?.toEpochMillis(),
    )
}

private fun Timestamp.toEpochMillis(): Long = toDate().time

private fun visibilityFromWire(value: String?): TripVisibility = TripVisibility.entries.firstOrNull { it.wireValue == value }
    ?: error("unknown trip visibility")

private fun statusFromWire(value: String?): TripStatus = TripStatus.entries.firstOrNull { it.wireValue == value }
    ?: error("unknown trip status")

private fun paceFromWire(value: String): TripPace = when (value) {
    // Accept the prototype values while existing documents are migrated.
    "slow" -> TripPace.Relaxed
    "fast" -> TripPace.Packed
    else -> TripPace.entries.firstOrNull { it.wireValue == value }
        ?: error("unknown trip pace")
}

private fun budgetBandFromWire(value: String): BudgetBand = when (value) {
    // Accept the prototype values while existing documents are migrated.
    "mid" -> BudgetBand.Moderate
    "flexible" -> BudgetBand.Comfort
    else -> BudgetBand.entries.firstOrNull { it.wireValue == value }
        ?: error("unknown budget band")
}

private fun profileVisibilityFromWire(value: String): com.triptandem.shared.ProfileVisibility =
    com.triptandem.shared.ProfileVisibility.entries.firstOrNull { it.wireValue == value }
        ?: error("unknown profile visibility")

private fun accountStatusFromWire(value: String): AccountStatus =
    AccountStatus.entries.firstOrNull { it.wireValue == value } ?: AccountStatus.Active

/** Firestore adapter for the day-by-day Phase 1 itinerary. */
class FirebaseItineraryRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : ItineraryRepository {
    override suspend fun listItems(tripId: String): DataResult<List<ItineraryItem>> {
        if (tripId.isBlank()) return DataResult.Failure(TripTandemError.Validation("tripId"))
        if (auth.currentUser == null) return DataResult.Failure(TripTandemError.Unauthenticated)
        return repositoryCall {
            firestore.collection(TRIPS_COLLECTION).document(tripId)
                .collection(ITINERARY_SUBCOLLECTION)
                .orderBy("position")
                .get().await().documents.map { it.toItineraryItem(tripId) }
                .filter { it.deletedAtEpochMillis == null }
        }
    }

    override suspend fun createItem(tripId: String, input: CreateItineraryItemInput): DataResult<ItineraryItem> {
        val uid = auth.currentUser?.uid ?: return DataResult.Failure(TripTandemError.Unauthenticated)
        input.validationError()?.let { return DataResult.Failure(it) }
        return repositoryCall {
            val reference = firestore.collection(TRIPS_COLLECTION).document(tripId)
                .collection(ITINERARY_SUBCOLLECTION).document()
            reference.set(input.toFirestore(uid)).await()
            reference.get().await().toItineraryItem(tripId)
        }
    }

    override suspend fun updateItem(
        tripId: String,
        itemId: String,
        input: UpdateItineraryItemInput,
    ): DataResult<ItineraryItem> {
        val uid = auth.currentUser?.uid ?: return DataResult.Failure(TripTandemError.Unauthenticated)
        input.validationError()?.let { return DataResult.Failure(it) }
        return repositoryCall {
            val reference = firestore.collection(TRIPS_COLLECTION).document(tripId)
                .collection(ITINERARY_SUBCOLLECTION).document(itemId)
            reference.update(input.toUpdateMap(uid)).await()
            reference.get().await().toItineraryItem(tripId)
        }
    }

    override suspend fun deleteItem(tripId: String, itemId: String): DataResult<Unit> {
        val uid = auth.currentUser?.uid ?: return DataResult.Failure(TripTandemError.Unauthenticated)
        return repositoryCall {
            val reference = firestore.collection(TRIPS_COLLECTION).document(tripId)
                .collection(ITINERARY_SUBCOLLECTION).document(itemId)
            val current = reference.get().await()
            if (!current.exists() || current.getTimestamp("deletedAt") != null) throw MissingDocumentException
            val now = System.currentTimeMillis()
            reference.update(
                mapOf(
                    "deletedAt" to FieldValue.serverTimestamp(),
                    "undoExpiresAt" to Timestamp(Date(now + 30_000L)),
                    "deletedBy" to uid,
                    "revision" to ((current.getLong("revision") ?: 0L) + 1L),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
        }
    }

    override suspend fun restoreItem(tripId: String, itemId: String): DataResult<ItineraryItem> {
        val uid = auth.currentUser?.uid ?: return DataResult.Failure(TripTandemError.Unauthenticated)
        return repositoryCall {
            val reference = firestore.collection(TRIPS_COLLECTION).document(tripId)
                .collection(ITINERARY_SUBCOLLECTION).document(itemId)
            val current = reference.get().await()
            if (!current.exists()) throw MissingDocumentException
            if (current.getTimestamp("deletedAt") == null) return@repositoryCall current.toItineraryItem(tripId)
            val expiresAt = current.getTimestamp("undoExpiresAt")?.toDate()?.time
                ?: throw MissingDocumentException
            if (System.currentTimeMillis() > expiresAt) throw UndoExpiredException
            val revision = current.getLong("revision") ?: 0L
            reference.update(
                mapOf(
                    "deletedAt" to FieldValue.delete(),
                    "undoExpiresAt" to FieldValue.delete(),
                    "deletedBy" to FieldValue.delete(),
                    "revision" to revision + 1L,
                    "lastEditedBy" to uid,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
            reference.get().await().toItineraryItem(tripId)
        }
    }
}

/** Firestore adapter for member roles and removal. */
class FirebaseTripMemberRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
) : TripMemberRepository {
    override suspend fun listMembers(tripId: String): DataResult<List<TripMember>> {
        if (tripId.isBlank()) return DataResult.Failure(TripTandemError.Validation("tripId"))
        if (auth.currentUser == null) return DataResult.Failure(TripTandemError.Unauthenticated)
        return repositoryCall {
            firestore.collection(TRIPS_COLLECTION).document(tripId)
                .collection(MEMBERS_SUBCOLLECTION).get().await().documents.map { it.toTripMember(tripId) }
        }
    }

    override suspend fun getCurrentMember(tripId: String): DataResult<TripMember?> {
        val uid = auth.currentUser?.uid ?: return DataResult.Failure(TripTandemError.Unauthenticated)
        if (tripId.isBlank()) return DataResult.Failure(TripTandemError.Validation("tripId"))
        return repositoryCall {
            val snapshot = firestore.collection(TRIPS_COLLECTION).document(tripId)
                .collection(MEMBERS_SUBCOLLECTION).document(uid).get().await()
            if (!snapshot.exists()) null else snapshot.toTripMember(tripId)
        }
    }

    override suspend fun updateRole(tripId: String, userId: String, role: TripMemberRole): DataResult<TripMember> {
        if (auth.currentUser == null) return DataResult.Failure(TripTandemError.Unauthenticated)
        return repositoryCall {
            val reference = firestore.collection(TRIPS_COLLECTION).document(tripId)
                .collection(MEMBERS_SUBCOLLECTION).document(userId)
            reference.update(mapOf("role" to role.wireValue, "updatedAt" to FieldValue.serverTimestamp())).await()
            reference.get().await().toTripMember(tripId)
        }
    }

    override suspend fun removeMember(tripId: String, userId: String): DataResult<Unit> {
        auth.currentUser ?: return DataResult.Failure(TripTandemError.Unauthenticated)
        if (tripId.isBlank() || userId.isBlank()) return DataResult.Failure(TripTandemError.Validation("member"))
        return repositoryCall {
            // A parent trip counter cannot be safely paired with an arbitrary
            // member path in client-side Firestore Rules. The regional callable
            // performs the owner check and both writes in one Admin transaction.
            functions.getHttpsCallable("removeTripMember")
                .call(mapOf("tripId" to tripId, "userId" to userId))
                .await()
        }
    }

    override suspend fun leaveTrip(tripId: String): DataResult<Unit> {
        val uid = auth.currentUser?.uid ?: return DataResult.Failure(TripTandemError.Unauthenticated)
        if (tripId.isBlank()) return DataResult.Failure(TripTandemError.Validation("tripId"))
        return repositoryCall {
            val tripReference = firestore.collection(TRIPS_COLLECTION).document(tripId)
            val memberReference = tripReference.collection(MEMBERS_SUBCOLLECTION).document(uid)
            firestore.runTransaction { transaction ->
                val trip = transaction.get(tripReference)
                val member = transaction.get(memberReference)
                if (!trip.exists() || !member.exists()) throw MissingDocumentException
                if (member.getString("role") == TripMemberRole.Owner.wireValue) throw InviteRejectedException
                if (member.getString("status") != MembershipStatus.Active.wireValue) throw MissingDocumentException
                val currentCount = trip.getLong("activeMemberCount")?.toInt() ?: 1
                transaction.update(
                    tripReference,
                    mapOf(
                        "activeMemberCount" to (currentCount - 1).coerceAtLeast(1),
                        "revision" to (trip.getLong("revision") ?: 0L) + 1L,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                )
                transaction.update(memberReference, mapOf("status" to MembershipStatus.Removed.wireValue, "updatedAt" to FieldValue.serverTimestamp()))
                null
            }.await()
        }
    }

    override suspend fun transferOwnership(tripId: String, newOwnerId: String): DataResult<Unit> {
        val uid = auth.currentUser?.uid ?: return DataResult.Failure(TripTandemError.Unauthenticated)
        if (tripId.isBlank() || newOwnerId.isBlank() || uid == newOwnerId) return DataResult.Failure(TripTandemError.Validation("ownership"))
        return repositoryCall {
            val tripReference = firestore.collection(TRIPS_COLLECTION).document(tripId)
            val oldOwnerReference = tripReference.collection(MEMBERS_SUBCOLLECTION).document(uid)
            val newOwnerReference = tripReference.collection(MEMBERS_SUBCOLLECTION).document(newOwnerId)
            firestore.runTransaction { transaction ->
                val trip = transaction.get(tripReference)
                val oldOwner = transaction.get(oldOwnerReference)
                val newOwner = transaction.get(newOwnerReference)
                if (!trip.exists() || !oldOwner.exists() || !newOwner.exists()) throw MissingDocumentException
                if (trip.getString("ownerId") != uid || oldOwner.getString("role") != TripMemberRole.Owner.wireValue || newOwner.getString("status") != MembershipStatus.Active.wireValue) {
                    throw InviteRejectedException
                }
                transaction.update(
                    tripReference,
                    mapOf(
                        "ownerId" to newOwnerId,
                        "revision" to (trip.getLong("revision") ?: 0L) + 1L,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                )
                transaction.update(oldOwnerReference, mapOf("role" to TripMemberRole.Editor.wireValue, "updatedAt" to FieldValue.serverTimestamp()))
                transaction.update(newOwnerReference, mapOf("role" to TripMemberRole.Owner.wireValue, "updatedAt" to FieldValue.serverTimestamp()))
                null
            }.await()
        }
    }
}

/** Secure-link adapter. The raw token is generated and returned once, while only its hash is persisted. */
class FirebaseInviteRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : InviteRepository {
    override suspend fun createInvite(tripId: String, input: CreateInviteInput): DataResult<InviteLink> {
        val uid = auth.currentUser?.uid ?: return DataResult.Failure(TripTandemError.Unauthenticated)
        if (input.role == TripMemberRole.Owner || input.expiresInHours !in 1..(24 * 30) || input.maxUses !in 1..20) {
            return DataResult.Failure(TripTandemError.Validation("invite"))
        }
        return repositoryCall {
            val tripSnapshot = firestore.collection(TRIPS_COLLECTION).document(tripId).get().await()
            if (!tripSnapshot.exists()) throw MissingDocumentException
            val token = secureToken()
            val hash = sha256(token)
            // Use the token hash as the document id so resolving an invite is
            // a direct, non-enumerable read. The raw token is still returned
            // once and is never persisted or logged.
            val id = hash
            val expiresAt = Date(System.currentTimeMillis() + input.expiresInHours * 60L * 60L * 1000L)
            val reference = firestore.collection(TRIPS_COLLECTION).document(tripId)
                .collection(INVITES_SUBCOLLECTION).document(id)
            reference.set(
                mapOf(
                    "tokenHash" to hash,
                    "role" to input.role.wireValue,
                    "expiresAt" to com.google.firebase.Timestamp(expiresAt),
                    "maxUses" to input.maxUses,
                    "uses" to 0,
                    "revokedAt" to null,
                    "createdBy" to uid,
                    // These fields are a deliberately small, non-sensitive
                    // preview so an invitee can review the offer before
                    // membership is created. They are not an itinerary read.
                    "tripTitle" to (tripSnapshot.getString("title") ?: "Trip"),
                    "previewDestination" to (tripSnapshot.getString("destination") ?: ""),
                    "previewStartDate" to (tripSnapshot.getString("startDate") ?: ""),
                    "previewEndDate" to (tripSnapshot.getString("endDate") ?: ""),
                    "inviterDisplayName" to (auth.currentUser?.displayName ?: "Trip host"),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
            InviteLink(
                id = id,
                tripId = tripId,
                role = input.role,
                expiresAtEpochMillis = expiresAt.time,
                maxUses = input.maxUses,
                shareToken = token,
                tripTitle = tripSnapshot.getString("title"),
                destination = tripSnapshot.getString("destination"),
                startDate = tripSnapshot.getString("startDate"),
                endDate = tripSnapshot.getString("endDate"),
                inviterDisplayName = auth.currentUser?.displayName ?: "Trip host",
            )
        }
    }

    override suspend fun revokeInvite(tripId: String, inviteId: String): DataResult<Unit> {
        if (auth.currentUser == null) return DataResult.Failure(TripTandemError.Unauthenticated)
        return repositoryCall {
            firestore.collection(TRIPS_COLLECTION).document(tripId).collection(INVITES_SUBCOLLECTION)
                .document(inviteId).update(mapOf("revokedAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp())).await()
        }
    }

    override suspend fun resolveInvite(tripId: String, shareToken: String): DataResult<InviteLink> {
        if (auth.currentUser == null) return DataResult.Failure(TripTandemError.Unauthenticated)
        if (shareToken.isBlank()) return DataResult.Failure(TripTandemError.Validation("token"))
        return repositoryCall {
            val document = firestore.collection(TRIPS_COLLECTION).document(tripId).collection(INVITES_SUBCOLLECTION)
                .document(sha256(shareToken)).get().await()
            if (!document.exists()) throw MissingDocumentException
            val invite = document.toInviteLink(tripId, shareToken)
            when {
                invite.revoked -> throw InviteRevokedException
                invite.expiresAtEpochMillis <= System.currentTimeMillis() -> throw InviteExpiredException
                invite.uses >= invite.maxUses -> throw InviteCapacityException
                else -> invite
            }
        }
    }

    override suspend fun acceptInvite(tripId: String, inviteId: String, role: TripMemberRole): DataResult<TripMember> {
        val uid = auth.currentUser?.uid ?: return DataResult.Failure(TripTandemError.Unauthenticated)
        if (role == TripMemberRole.Owner) return DataResult.Failure(TripTandemError.Validation("role"))
        return repositoryCall {
            val tripReference = firestore.collection(TRIPS_COLLECTION).document(tripId)
            val memberReference = firestore.collection(TRIPS_COLLECTION).document(tripId)
                .collection(MEMBERS_SUBCOLLECTION).document(uid)
            val inviteReference = firestore.collection(TRIPS_COLLECTION).document(tripId)
                .collection(INVITES_SUBCOLLECTION).document(inviteId)
            firestore.runTransaction { transaction ->
                val tripSnapshot = transaction.get(tripReference)
                val inviteSnapshot = transaction.get(inviteReference)
                val memberSnapshot = transaction.get(memberReference)
                if (!tripSnapshot.exists() || !inviteSnapshot.exists()) throw MissingDocumentException
                val invite = inviteSnapshot.toInviteLink(tripId, shareToken = "")
                if (invite.revoked) throw InviteRevokedException
                if (invite.expiresAtEpochMillis <= System.currentTimeMillis()) throw InviteExpiredException
                if (invite.role != role) throw InviteRejectedException
                if (invite.uses >= invite.maxUses) throw InviteCapacityException
                val existingStatus = memberSnapshot.getString("status")
                val existingRole = memberSnapshot.getString("role")
                if (existingStatus == MembershipStatus.Active.wireValue) {
                    return@runTransaction TripMember(
                        tripId = tripId,
                        userId = uid,
                        displayName = memberSnapshot.getString("displayName") ?: "Traveler",
                        role = TripMemberRole.entries.firstOrNull { it.wireValue == existingRole } ?: role,
                        status = MembershipStatus.Active,
                        inviteId = memberSnapshot.getString("inviteId"),
                        joinedAtEpochMillis = memberSnapshot.getTimestamp("joinedAt")?.toDate()?.time,
                        updatedAtEpochMillis = memberSnapshot.getTimestamp("updatedAt")?.toDate()?.time,
                    )
                }
                val activeCount = tripSnapshot.getLong("activeMemberCount")?.toInt() ?: 1
                val capacity = tripSnapshot.getLong("capacity")?.toInt() ?: 1
                if (activeCount >= capacity) throw InviteCapacityException
                val now = FieldValue.serverTimestamp()
                val memberData = buildMap<String, Any> {
                    put("userId", uid)
                    put("displayName", auth.currentUser?.displayName ?: "Traveler")
                    put("role", role.wireValue)
                    put("status", MembershipStatus.Active.wireValue)
                    put("inviteId", inviteId)
                    put("joinedAt", if (memberSnapshot.exists()) memberSnapshot.getTimestamp("joinedAt") ?: now else now)
                    put("createdAt", if (memberSnapshot.exists()) memberSnapshot.getTimestamp("createdAt") ?: now else now)
                    put("updatedAt", now)
                }
                transaction.set(memberReference, memberData)
                transaction.update(
                    tripReference,
                    mapOf(
                        "activeMemberCount" to activeCount + 1,
                        "revision" to (tripSnapshot.getLong("revision") ?: 0L) + 1L,
                        "updatedAt" to now,
                    ),
                )
                transaction.update(inviteReference, mapOf("uses" to invite.uses + 1, "updatedAt" to now))
                TripMember(
                    tripId = tripId,
                    userId = uid,
                    displayName = auth.currentUser?.displayName ?: "Traveler",
                    role = role,
                    status = MembershipStatus.Active,
                    inviteId = inviteId,
                )
            }.await()
        }
    }
}

private object InviteRejectedException : IllegalStateException("invite rejected")
private object InviteExpiredException : IllegalStateException("invite expired")
private object InviteRevokedException : IllegalStateException("invite revoked")
private object InviteCapacityException : IllegalStateException("invite capacity reached")

private fun CreateItineraryItemInput.validationError(): TripTandemError? = when {
    title.trim().isEmpty() || title.length > 160 -> TripTandemError.Validation("title")
    durationMinutes !in 0..1440 -> TripTandemError.Validation("durationMinutes")
    !flexibleTime && startTimeEpochMillis == null -> TripTandemError.Validation("startTime")
    startTimeLabel?.length?.let { it > 32 } == true -> TripTandemError.Validation("startTimeLabel")
    place?.length?.let { it > 200 } == true -> TripTandemError.Validation("place")
    note?.length?.let { it > 1000 } == true -> TripTandemError.Validation("note")
    else -> null
}

private fun UpdateItineraryItemInput.validationError(): TripTandemError? = when {
    title.trim().isEmpty() || title.length > 160 -> TripTandemError.Validation("title")
    durationMinutes !in 0..1440 -> TripTandemError.Validation("durationMinutes")
    position !in 0..500 -> TripTandemError.Validation("position")
    !flexibleTime && startTimeEpochMillis == null -> TripTandemError.Validation("startTime")
    startTimeLabel?.length?.let { it > 32 } == true -> TripTandemError.Validation("startTimeLabel")
    else -> null
}

private fun CreateItineraryItemInput.toFirestore(uid: String): Map<String, Any> = buildMap {
    put("type", type.wireValue)
    put("title", title.trim())
    put("startTime", com.google.firebase.Timestamp(Date(startTimeEpochMillis ?: 0L)))
    put("flexibleTime", flexibleTime)
    startTimeLabel?.trim()?.takeIf { it.isNotEmpty() }?.let { put("startTimeLabel", it) }
    put("durationMinutes", durationMinutes)
    place?.trim()?.takeIf { it.isNotEmpty() }?.let { put("place", it) }
    note?.trim()?.takeIf { it.isNotEmpty() }?.let { put("note", it) }
    put("status", status.wireValue)
    put("visibility", visibility.wireValue)
    put("position", position)
    put("revision", 0)
    put("lastEditedBy", uid)
    dayDate?.trim()?.takeIf { it.isNotEmpty() }?.let { put("dayDate", it) }
    put("createdAt", FieldValue.serverTimestamp())
    put("updatedAt", FieldValue.serverTimestamp())
}

private fun UpdateItineraryItemInput.toUpdateMap(uid: String): Map<String, Any> = buildMap {
    put("type", type.wireValue)
    put("title", title.trim())
    put("startTime", com.google.firebase.Timestamp(Date(startTimeEpochMillis ?: 0L)))
    put("flexibleTime", flexibleTime)
    put("startTimeLabel", startTimeLabel?.trim()?.takeIf { it.isNotEmpty() } ?: FieldValue.delete())
    put("durationMinutes", durationMinutes)
    put("place", place?.trim()?.takeIf { it.isNotEmpty() } ?: FieldValue.delete())
    put("note", note?.trim()?.takeIf { it.isNotEmpty() } ?: FieldValue.delete())
    put("status", status.wireValue)
    put("visibility", visibility.wireValue)
    put("position", position)
    put("revision", expectedRevision + 1)
    put("lastEditedBy", uid)
    put("dayDate", dayDate?.trim()?.takeIf { it.isNotEmpty() } ?: FieldValue.delete())
    put("updatedAt", FieldValue.serverTimestamp())
}

private fun com.google.firebase.firestore.DocumentSnapshot.toItineraryItem(tripId: String): ItineraryItem {
    val timestamp = getTimestamp("startTime")?.toDate()?.time ?: 0L
    return ItineraryItem(
        id = id,
        tripId = tripId,
        type = ItineraryItemType.entries.firstOrNull { it.wireValue == getString("type") } ?: ItineraryItemType.Activity,
        title = getString("title") ?: "Untitled item",
        startTimeEpochMillis = timestamp.takeIf { it > 0L },
        flexibleTime = getBoolean("flexibleTime") ?: (timestamp <= 0L),
        startTimeLabel = getString("startTimeLabel"),
        durationMinutes = getLong("durationMinutes")?.toInt() ?: 0,
        place = getString("place"),
        note = getString("note"),
        status = ItineraryItemStatus.entries.firstOrNull { it.wireValue == getString("status") } ?: ItineraryItemStatus.Planned,
        visibility = ItineraryVisibility.entries.firstOrNull { it.wireValue == getString("visibility") } ?: ItineraryVisibility.Members,
        position = getLong("position")?.toInt() ?: 0,
        revision = getLong("revision")?.toInt() ?: 0,
        lastEditedBy = getString("lastEditedBy"),
        updatedAtEpochMillis = getTimestamp("updatedAt")?.toDate()?.time,
        dayDate = getString("dayDate"),
        deletedAtEpochMillis = getTimestamp("deletedAt")?.toEpochMillis(),
        undoExpiresAtEpochMillis = getTimestamp("undoExpiresAt")?.toEpochMillis(),
        generatedBy = getString("generatedBy"),
        generationJobId = getString("generationJobId"),
    )
}

private fun com.google.firebase.firestore.DocumentSnapshot.toTripMember(tripId: String): TripMember = TripMember(
    tripId = tripId,
    userId = getString("userId") ?: id,
    displayName = getString("displayName") ?: "Traveler",
    role = TripMemberRole.entries.firstOrNull { it.wireValue == getString("role") } ?: TripMemberRole.Viewer,
    status = MembershipStatus.entries.firstOrNull { it.wireValue == getString("status") } ?: MembershipStatus.Active,
    inviteId = getString("inviteId"),
    joinedAtEpochMillis = getTimestamp("joinedAt")?.toDate()?.time,
    updatedAtEpochMillis = getTimestamp("updatedAt")?.toDate()?.time,
)

private fun com.google.firebase.firestore.DocumentSnapshot.toInviteLink(tripId: String, shareToken: String): InviteLink = InviteLink(
    id = id,
    tripId = tripId,
    role = TripMemberRole.entries.firstOrNull { it.wireValue == getString("role") } ?: TripMemberRole.Viewer,
    expiresAtEpochMillis = getTimestamp("expiresAt")?.toDate()?.time ?: 0L,
    maxUses = getLong("maxUses")?.toInt() ?: 1,
    uses = getLong("uses")?.toInt() ?: 0,
    revoked = getTimestamp("revokedAt") != null,
    shareToken = shareToken,
    createdAtEpochMillis = getTimestamp("createdAt")?.toEpochMillis(),
    tripTitle = getString("tripTitle"),
    destination = getString("previewDestination"),
    startDate = getString("previewStartDate"),
    endDate = getString("previewEndDate"),
    inviterDisplayName = getString("inviterDisplayName"),
)

private fun secureToken(): String {
    val bytes = ByteArray(32)
    SecureRandom().nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it) }
}

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }
