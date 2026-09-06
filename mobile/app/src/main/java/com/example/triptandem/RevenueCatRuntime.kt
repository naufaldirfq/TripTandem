package com.triptandem

import android.util.Log
import com.revenuecat.purchases.kmp.LogLevel
import com.revenuecat.purchases.kmp.Purchases
import com.revenuecat.purchases.kmp.configure
import com.triptandem.shared.RevenueCatCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Configures RevenueCat once an authenticated Firebase App User ID exists.
 * The debug build uses the MCP-created Test Store key; release builds receive
 * an empty key until real store credentials are configured.
 */
internal object RevenueCatRuntime {
    private const val TAG = "RevenueCatRuntime"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun initialize(appUserId: String): RevenueCatCoordinator? {
        if (appUserId.isBlank() || BuildConfig.REVENUECAT_PUBLIC_API_KEY.isBlank()) {
            Log.i(TAG, "RevenueCat is not configured for this build")
            return null
        }

        return runCatching {
            val purchases = if (Purchases.isConfigured) {
                Purchases.sharedInstance
            } else {
                Purchases.logLevel = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.INFO
                Purchases.configure(BuildConfig.REVENUECAT_PUBLIC_API_KEY) {
                    this.appUserId = appUserId
                }
            }
            RevenueCatCoordinator(purchases, appUserId).also { coordinator ->
                // configure() accepts the initial ID; a later Firebase auth
                // change must use RevenueCat logIn so entitlements follow the
                // same account across Android and iOS.
                scope.launch {
                    coordinator.identify(appUserId)
                }
            }
        }.onFailure { error ->
            Log.e(TAG, "RevenueCat configuration is unavailable", error)
        }.getOrNull()
    }

}
