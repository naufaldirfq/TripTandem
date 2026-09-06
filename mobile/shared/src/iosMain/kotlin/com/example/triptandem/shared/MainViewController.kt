package com.triptandem.shared

import androidx.compose.ui.window.ComposeUIViewController

fun MainViewController() = MainViewControllerWithAnalytics(null)

fun MainViewControllerWithAnalytics(analytics: TripTandemAnalytics?) = ComposeUIViewController {
    TripTandemApp(
        analytics = analytics ?: NoOpTripTandemAnalytics,
    )
}

/**
 * iOS host entry point used when the native Firebase adapters are available.
 * Keeping the original function preserves source compatibility for previews
 * and older host integrations while allowing the production host to inject
 * the same repositories used by Android.
 */
fun MainViewControllerWithAnalyticsAndRepositories(
    analytics: TripTandemAnalytics?,
    repositories: TripTandemRepositories?,
    onSignOut: (() -> Unit)? = null,
    connectivity: ConnectivityMonitor? = null,
) = ComposeUIViewController {
    TripTandemApp(
        analytics = analytics ?: NoOpTripTandemAnalytics,
        repositories = repositories,
        onSignOut = onSignOut,
        connectivity = connectivity ?: AlwaysOnlineConnectivityMonitor,
    )
}

/** iOS host entry point that preserves an invite deep link across the native
 * app launch/authentication boundary. */
fun MainViewControllerWithAnalyticsAndRepositoriesAndPendingInvite(
    analytics: TripTandemAnalytics?,
    repositories: TripTandemRepositories?,
    pendingInvite: PendingInvite?,
    onSignOut: (() -> Unit)? = null,
    connectivity: ConnectivityMonitor? = null,
) = ComposeUIViewController {
    TripTandemApp(
        analytics = analytics ?: NoOpTripTandemAnalytics,
        repositories = repositories,
        pendingInvite = pendingInvite,
        onSignOut = onSignOut,
        connectivity = connectivity ?: AlwaysOnlineConnectivityMonitor,
    )
}

/** Same iOS entry point with the RevenueCat coordinator owned by the host. */
fun MainViewControllerWithAnalyticsAndRepositoriesAndPendingInviteAndRevenueCat(
    analytics: TripTandemAnalytics?,
    repositories: TripTandemRepositories?,
    pendingInvite: PendingInvite?,
    revenueCat: RevenueCatCoordinator?,
    featureFlags: TripTandemFeatureFlags = TripTandemFeatureFlags(),
    onSignOut: (() -> Unit)? = null,
    onOpenExternalUrl: ((String) -> Unit)? = null,
    onManageSubscription: (() -> Unit)? = null,
    connectivity: ConnectivityMonitor? = null,
) = ComposeUIViewController {
    TripTandemApp(
        analytics = analytics ?: NoOpTripTandemAnalytics,
        repositories = repositories,
        pendingInvite = pendingInvite,
        revenueCat = revenueCat,
        featureFlags = featureFlags,
        onSignOut = onSignOut,
        onOpenExternalUrl = onOpenExternalUrl,
        onManageSubscription = onManageSubscription,
        connectivity = connectivity ?: AlwaysOnlineConnectivityMonitor,
    )
}
