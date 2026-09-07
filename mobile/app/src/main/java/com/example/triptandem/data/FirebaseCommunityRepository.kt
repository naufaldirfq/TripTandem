package com.triptandem.data

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.triptandem.shared.*
import kotlinx.coroutines.tasks.await

class FirebaseCommunityRepository(private val functions: FirebaseFunctions, private val activity: android.app.Activity) : CommunityRepository {
    override suspend fun execute(operation: String, input: Map<String, String>): DataResult<String> = try {
        var payload = input
        if (operation == "preferences" && input["save"] == "true") {
            if (input["push"] == "true") {
                val granted = com.triptandem.CommunityPushRuntime.enable(activity)
                payload = input + ("push" to granted.toString())
            } else com.triptandem.CommunityPushRuntime.unregister(activity)
        }
        val data = functions.getHttpsCallable("communityAction").call(mapOf("operation" to operation, "input" to payload)).await().data as? Map<*, *>
        val json = data?.get("json") as? String
        if (json == null) DataResult.Failure(TripTandemError.Validation("response")) else DataResult.Success(json)
    } catch (error: FirebaseFunctionsException) {
        DataResult.Failure(when (error.code) {
            FirebaseFunctionsException.Code.UNAUTHENTICATED -> TripTandemError.Unauthenticated
            FirebaseFunctionsException.Code.PERMISSION_DENIED -> TripTandemError.PermissionDenied
            FirebaseFunctionsException.Code.UNAVAILABLE -> TripTandemError.Offline
            else -> TripTandemError.Validation("community")
        })
    } catch (_: Exception) { DataResult.Failure(TripTandemError.Offline) }
}
