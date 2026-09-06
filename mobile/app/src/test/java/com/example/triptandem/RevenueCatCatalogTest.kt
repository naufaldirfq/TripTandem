package com.example.triptandem

import com.triptandem.shared.OrganizerProPackage
import com.triptandem.shared.RevenueCatPackageSelection
import com.triptandem.shared.matches
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RevenueCatCatalogTest {
    @Test
    fun packageSelectionDoesNotPretendAnUnavailableBillingOptionExists() {
        val monthly = OrganizerProPackage(
            identifier = "monthly",
            packageType = "MONTHLY",
            productIdentifier = "triptandem_pro_monthly",
            productTitle = "TripTandem Pro",
            formattedPrice = "$5.99",
            currencyCode = "USD",
            amountMicros = 5_990_000,
            billingPeriodLabel = "1 month",
        )

        assertTrue(monthly.matches(RevenueCatPackageSelection.Monthly))
        assertFalse(monthly.matches(RevenueCatPackageSelection.Annual))
    }
}
