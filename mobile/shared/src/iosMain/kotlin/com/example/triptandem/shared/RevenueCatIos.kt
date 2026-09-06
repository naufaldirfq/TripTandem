package com.triptandem.shared

import com.revenuecat.purchases.kmp.LogLevel
import com.revenuecat.purchases.kmp.Purchases
import com.revenuecat.purchases.kmp.configure

/**
 * iOS host bootstrap for the shared RevenueCat SDK. The public key is supplied
 * by the iOS app's build configuration; this function never accepts a secret
 * RevenueCat API key.
 */
fun configureRevenueCat(apiKey: String, appUserId: String): RevenueCatCoordinator? {
    if (apiKey.isBlank() || appUserId.isBlank()) return null

    return runCatching {
        val purchases = if (Purchases.isConfigured) {
            Purchases.sharedInstance
        } else {
            // Keep SDK diagnostics out of production logs. Android mirrors
            // this with DEBUG only for debug builds; the iOS host can still
            // enable verbose RevenueCat logging locally when needed.
            Purchases.logLevel = LogLevel.INFO
            Purchases.configure(apiKey) {
                this.appUserId = appUserId
            }
        }
        RevenueCatCoordinator(purchases, appUserId)
    }.getOrNull()
}
