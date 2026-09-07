package com.triptandem

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object CommunityPushRuntime {
    private var permission: CompletableDeferred<Boolean>? = null
    private fun preferences(context: Context) = context.getSharedPreferences("community_push", Context.MODE_PRIVATE)
    private fun installation(context: Context): String {
        val prefs = preferences(context)
        return prefs.getString("installation", null) ?: java.util.UUID.randomUUID().toString().also { prefs.edit().putString("installation", it).apply() }
    }
    fun permissionResult(granted: Boolean) { permission?.complete(granted); permission = null }
    suspend fun enable(activity: Activity): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= 33 && ActivityCompat.checkSelfPermission(activity, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            val pending = CompletableDeferred<Boolean>(); permission = pending
            ActivityCompat.requestPermissions(activity, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 7309)
            if (!pending.await()) return false
        }
        if (!NotificationManagerCompat.from(activity).areNotificationsEnabled()) return false
        FirebaseMessaging.getInstance().isAutoInitEnabled = true
        preferences(activity).edit().putString("owner", FirebaseAuth.getInstance().currentUser?.uid).apply()
        register(activity, FirebaseMessaging.getInstance().token.await())
        preferences(activity).edit().putBoolean("enabled", true).apply()
        return true
    }
    suspend fun register(context: Context, token: String) {
        if (FirebaseAuth.getInstance().currentUser?.uid != preferences(context).getString("owner", null) || FirebaseAuth.getInstance().currentUser == null) return
        FirebaseFunctions.getInstance("asia-southeast2").getHttpsCallable("communityAction").call(mapOf("operation" to "register_installation", "input" to mapOf("installationId" to installation(context), "token" to token, "platform" to "android"))).await()
    }
    suspend fun unregister(context: Context) {
        preferences(context).edit().putBoolean("enabled", false).apply()
        FirebaseMessaging.getInstance().isAutoInitEnabled = false
        runCatching { FirebaseFunctions.getInstance("asia-southeast2").getHttpsCallable("communityAction").call(mapOf("operation" to "unregister_installation", "input" to mapOf("installationId" to installation(context)))).await() }
        runCatching { FirebaseMessaging.getInstance().deleteToken().await() }
        NotificationManagerCompat.from(context).cancelAll()
    }
    fun enabled(context: Context) = preferences(context).getBoolean("enabled", false)
}
class CommunityMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        if (CommunityPushRuntime.enabled(this)) CoroutineScope(Dispatchers.IO).launch { runCatching { CommunityPushRuntime.register(this@CommunityMessagingService, token) } }
    }
    override fun onMessageReceived(message: RemoteMessage) {
        // Foreground delivery stays in the authoritative activity center; no second popup.
    }
}
