package com.triptandem.shared

import com.revenuecat.purchases.kmp.Purchases
import com.revenuecat.purchases.kmp.ktx.awaitCustomerInfo
import com.revenuecat.purchases.kmp.ktx.awaitLogIn
import com.revenuecat.purchases.kmp.ktx.awaitOfferings
import com.revenuecat.purchases.kmp.ktx.awaitPurchase
import com.revenuecat.purchases.kmp.ktx.awaitRestore
import com.revenuecat.purchases.kmp.ktx.awaitTrialOrIntroPriceEligibility
import com.revenuecat.purchases.kmp.models.IntroEligibilityStatus
import com.revenuecat.purchases.kmp.models.Offerings
import com.revenuecat.purchases.kmp.models.Package
import com.revenuecat.purchases.kmp.models.DiscountPaymentMode
import com.revenuecat.purchases.kmp.models.Period
import com.revenuecat.purchases.kmp.models.PurchasesErrorCode
import com.revenuecat.purchases.kmp.models.PurchasesException
import com.revenuecat.purchases.kmp.models.PurchasesTransactionException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

const val TRIPTANDEM_PRO_ENTITLEMENT = "triptandem_pro"

enum class RevenueCatPackageSelection {
    Monthly,
    Annual,
}

/** Explicit paywall lifecycle states used by the shared UI. */
enum class OrganizerProPurchaseState {
    Idle,
    LoadingProducts,
    Ready,
    Purchasing,
    Pending,
    Active,
    CancelledByUser,
    Failed,
    Unavailable,
}

data class OrganizerProStatus(
    val isActive: Boolean,
    val willRenew: Boolean = false,
    val productIdentifier: String? = null,
    val expirationEpochMillis: Long? = null,
    val isSandbox: Boolean = false,
)

data class OrganizerProPackage(
    val identifier: String,
    val packageType: String,
    val productIdentifier: String,
    val productTitle: String,
    val formattedPrice: String,
    val currencyCode: String,
    val amountMicros: Long,
    /** Store-provided billing period; never inferred from a client price. */
    val billingPeriodLabel: String,
    /** Present only when RevenueCat reports an eligible introductory offer. */
    val introductoryOffer: OrganizerProIntroductoryOffer? = null,
)

data class OrganizerProIntroductoryOffer(
    val formattedPrice: String,
    val periodLabel: String,
    val isFreeTrial: Boolean,
)

data class OrganizerProOffering(
    val identifier: String,
    val packages: List<OrganizerProPackage>,
)

/** Returns true when a store package represents the requested billing option. */
fun OrganizerProPackage.matches(selection: RevenueCatPackageSelection): Boolean = when (selection) {
    RevenueCatPackageSelection.Monthly -> packageType.contains("MONTHLY", ignoreCase = true) || identifier.contains("monthly", ignoreCase = true)
    RevenueCatPackageSelection.Annual -> packageType.contains("ANNUAL", ignoreCase = true) || identifier.contains("annual", ignoreCase = true)
}

/**
 * Shared RevenueCat boundary. Product IDs, prices, and entitlement state come
 * from the RevenueCat offering; no client-side `isPro` flag is trusted by the
 * backend. Platform code is responsible only for configuring [Purchases] once.
 */
class RevenueCatCoordinator(
    private val purchases: Purchases,
    initialAppUserId: String? = null,
) {
    private val identityMutex = Mutex()
    private var expectedAppUserId: String? = initialAppUserId?.trim()?.takeIf { it.isNotEmpty() }

    /** Re-associates the SDK with the stable Firebase UID after account auth changes. */
    suspend fun identify(appUserId: String): DataResult<OrganizerProStatus> = purchasesCall {
        identityMutex.withLock {
            if (appUserId.isBlank()) throw IllegalArgumentException("appUserId")
            expectedAppUserId = appUserId
            if (purchases.appUserID != appUserId) {
                purchases.awaitLogIn(appUserId).customerInfo.toOrganizerProStatus()
            } else {
                purchases.awaitCustomerInfo().toOrganizerProStatus()
            }
        }
    }

    suspend fun refreshEntitlement(): DataResult<OrganizerProStatus> = purchasesCall {
        withCurrentIdentity { purchases.awaitCustomerInfo().toOrganizerProStatus() }
    }

    suspend fun currentOffering(): DataResult<OrganizerProOffering> = purchasesCall {
        withCurrentIdentity {
            val offerings = purchases.awaitOfferings()
            val currentOffering = offerings.current ?: throw OfferingUnavailableException
            val products = currentOffering.availablePackages.map { it.storeProduct }
            val eligibleIntroProducts = if (products.isEmpty()) {
                emptySet()
            } else {
                purchases.awaitTrialOrIntroPriceEligibility(products)
                    .filterValues { it == IntroEligibilityStatus.ELIGIBLE }
                    .keys
                    .map { it.id }
                    .toSet()
            }
            offerings.toOrganizerProOffering(eligibleIntroProducts)
        }
    }

    suspend fun purchase(selection: RevenueCatPackageSelection): DataResult<OrganizerProStatus> = purchasesCall {
        withCurrentIdentity {
            val packageToPurchase = purchases.awaitOfferings().select(selection)
                ?: throw OfferingUnavailableException
            purchases.awaitPurchase(packageToPurchase).customerInfo.toOrganizerProStatus()
        }
    }

    suspend fun restorePurchases(): DataResult<OrganizerProStatus> = purchasesCall {
        withCurrentIdentity { purchases.awaitRestore().toOrganizerProStatus() }
    }

    /**
     * Auth callbacks and Compose lifecycle effects can run concurrently. Keep
     * the UID switch and the first entitlement/catalog read in one critical
     * section so a foreground refresh cannot observe the previous account.
     */
    private suspend fun <T> withCurrentIdentity(block: suspend () -> T): T = identityMutex.withLock {
        val appUserId = expectedAppUserId
        if (appUserId != null && purchases.appUserID != appUserId) {
            purchases.awaitLogIn(appUserId)
        }
        block()
    }
}

private object OfferingUnavailableException : IllegalStateException("No current RevenueCat offering is available")

private suspend fun <T> purchasesCall(block: suspend () -> T): DataResult<T> = try {
    DataResult.Success(block())
} catch (error: CancellationException) {
    throw error
} catch (error: OfferingUnavailableException) {
    DataResult.Failure(TripTandemError.PurchaseUnavailable)
} catch (error: PurchasesTransactionException) {
    if (error.userCancelled) {
        DataResult.Failure(TripTandemError.PurchaseCancelled)
    } else {
        DataResult.Failure(error.toTripTandemError())
    }
} catch (error: PurchasesException) {
    DataResult.Failure(error.toTripTandemError())
} catch (error: Throwable) {
    DataResult.Failure(TripTandemError.Unknown(error::class.simpleName))
}

private fun PurchasesException.toTripTandemError(): TripTandemError = when (code) {
    PurchasesErrorCode.PurchaseCancelledError -> TripTandemError.PurchaseCancelled
    PurchasesErrorCode.PaymentPendingError -> TripTandemError.PurchasePending
    PurchasesErrorCode.ProductNotAvailableForPurchaseError -> TripTandemError.PurchaseUnavailable
    PurchasesErrorCode.NetworkError,
    PurchasesErrorCode.OfflineConnectionError,
    -> TripTandemError.Offline
    else -> TripTandemError.Unknown(code.name)
}

/** Maps platform/store failures to the PRD's non-alarming purchase states. */
fun TripTandemError.toOrganizerProPurchaseState(): OrganizerProPurchaseState = when (this) {
    TripTandemError.PurchasePending -> OrganizerProPurchaseState.Pending
    TripTandemError.PurchaseCancelled -> OrganizerProPurchaseState.CancelledByUser
    TripTandemError.PurchaseUnavailable -> OrganizerProPurchaseState.Unavailable
    else -> OrganizerProPurchaseState.Failed
}

private fun com.revenuecat.purchases.kmp.models.CustomerInfo.toOrganizerProStatus(): OrganizerProStatus {
    val entitlement = entitlements[TRIPTANDEM_PRO_ENTITLEMENT]
    return OrganizerProStatus(
        isActive = entitlement?.isActive == true,
        willRenew = entitlement?.willRenew == true,
        productIdentifier = entitlement?.productIdentifier,
        expirationEpochMillis = entitlement?.expirationDateMillis,
        isSandbox = entitlement?.isSandbox == true,
    )
}

private fun Offerings.toOrganizerProOffering(eligibleIntroProductIds: Set<String> = emptySet()): OrganizerProOffering {
    val currentOffering = current ?: throw OfferingUnavailableException
    return OrganizerProOffering(
        identifier = currentOffering.identifier,
        packages = currentOffering.availablePackages.map { it.toOrganizerProPackage(eligibleIntroProductIds) },
    )
}

private fun Offerings.select(selection: RevenueCatPackageSelection): Package? {
    val currentOffering = current ?: return null
    val canonical = when (selection) {
        RevenueCatPackageSelection.Monthly -> currentOffering.monthly
        RevenueCatPackageSelection.Annual -> currentOffering.annual
    }
    return canonical ?: currentOffering.availablePackages.firstOrNull { it.matches(selection) }
}

private fun Package.matches(selection: RevenueCatPackageSelection): Boolean = when (selection) {
    RevenueCatPackageSelection.Monthly -> packageType.name.contains("MONTHLY", ignoreCase = true)
        || identifier.contains("monthly", ignoreCase = true)
    RevenueCatPackageSelection.Annual -> packageType.name.contains("ANNUAL", ignoreCase = true)
        || identifier.contains("annual", ignoreCase = true)
}

private fun Package.toOrganizerProPackage(eligibleIntroProductIds: Set<String>): OrganizerProPackage {
    val product = storeProduct
    val introductoryOffer = product.introductoryDiscount
        ?.takeIf { product.id in eligibleIntroProductIds }
        ?.let { discount ->
        OrganizerProIntroductoryOffer(
            formattedPrice = discount.price.formatted,
            periodLabel = periodLabel(discount.subscriptionPeriod, discount.numberOfPeriods),
            isFreeTrial = discount.paymentMode == DiscountPaymentMode.FREE_TRIAL,
        )
    }
    return OrganizerProPackage(
        identifier = identifier,
        packageType = packageType.name,
        productIdentifier = product.id,
        productTitle = product.title,
        formattedPrice = product.price.formatted,
        currencyCode = product.price.currencyCode,
        amountMicros = product.price.amountMicros,
        billingPeriodLabel = product.period?.let(::periodLabel) ?: "billing period",
        introductoryOffer = introductoryOffer,
    )
}

private fun periodLabel(period: Period, numberOfPeriods: Long = 1): String {
    val count = (period.value.toLong().coerceAtLeast(1) * numberOfPeriods.coerceAtLeast(1)).coerceAtMost(120)
    val unit = when (period.unit) {
        com.revenuecat.purchases.kmp.models.PeriodUnit.DAY -> "day"
        com.revenuecat.purchases.kmp.models.PeriodUnit.WEEK -> "week"
        com.revenuecat.purchases.kmp.models.PeriodUnit.MONTH -> "month"
        com.revenuecat.purchases.kmp.models.PeriodUnit.YEAR -> "year"
        com.revenuecat.purchases.kmp.models.PeriodUnit.UNKNOWN -> "billing period"
    }
    return "$count $unit${if (count == 1L) "" else "s"}"
}
