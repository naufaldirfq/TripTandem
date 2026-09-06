# RevenueCat setup

TripTandem now has a RevenueCat project and a working Kotlin Multiplatform SDK
boundary. The catalog was created with the RevenueCat MCP on September 3,
2026. The local Android build uses RevenueCat Test Store so the purchase state
can be exercised without Play Console or App Store Connect credentials.

## Catalog created

| Resource | Identifier | RevenueCat ID | Status |
|---|---|---|---|
| Project | TripTandem | `proj8b05ec7d` | Active |
| Test Store app | Test Store | `app3fef6072fb` | Active; local verification |
| Android app placeholder | `com.triptandem` | `appfdf7251f21` | Active; store credentials pending |
| iOS app placeholder | `com.triptandem` | `app9cd340929c` | Active; store credentials pending |
| Entitlement | `triptandem_pro` | `entlef1c674e48` | Active |
| Current offering | `triptandem_pro` | `ofrng19a0c057be` | Active/current |

The Android and iOS app entries are catalog placeholders. Their platform store
credentials are intentionally not configured until the owner has the Play
Console service account and App Store Connect keys. No secret RevenueCat key is
checked into this repository.

## Test Store products

| Package | Product store identifier | Product ID | Period | Indicative prices |
|---|---|---|---|---|
| `$rc_monthly` | `triptandem_pro_monthly` | `prod1c3d85aa3a` | P1M | USD 5.99; IDR 95,000 |
| `$rc_annual` | `triptandem_pro_annual` | `prod4d56a507d1` | P1Y | USD 29.99; IDR 475,000 |

Both products are attached to `triptandem_pro`. The current offering contains
the monthly package at position 0 and annual package at position 1. The app
must display the localized price returned by RevenueCat; the values above are
catalog fixtures, not client-side price constants.

The Test Store monthly and annual products each expose a one-week trial for
users who have never purchased (`offer.trial.duration=P1W`). This is a local
judge/demo path only; production trial eligibility must be configured and
verified independently in App Store Connect and Google Play.

The platform catalog entries are also registered and attached to the matching
packages:

| Platform | Store identifier | RevenueCat product ID |
|---|---|---|
| Google Play | `triptandem_pro_monthly:monthly` | `prodec6db73158` |
| Google Play | `triptandem_pro_annual:annual` | `prod0e0e2e33f4` |
| App Store | `triptandem_pro_monthly` | `prod0537eba5cc` |
| App Store | `triptandem_pro_annual` | `prod0fda1764ab` |

RevenueCat generated public keys for the platform app entries. They are
recorded here as client configuration values; store credentials and products
must still be configured before they can validate real purchases:

- Android production key: `goog_vIEXXObsYwyhIWkSGcteFUMnxJd`
- iOS production key: `appl_eCPfFniDjcbfObuRYgQmViQOftl`
- Test Store debug key: `test_DmhECcKFidQzMMdcpVdNqdRzYTz` (never submit)
- Android/iOS store credentials: not configured yet

## Client integration

- `mobile/shared` depends on `com.revenuecat.purchases:purchases-kmp-core:3.7.0`.
- [`RevenueCatCoordinator.kt`](../mobile/shared/src/commonMain/kotlin/com/example/triptandem/shared/RevenueCatCoordinator.kt) is the shared boundary for loading offerings, purchasing monthly/annual packages, restoring purchases, and refreshing `triptandem_pro` entitlement state.
- [`RevenueCatRuntime.kt`](../mobile/app/src/main/java/com/example/triptandem/RevenueCatRuntime.kt) configures the platform SDK once Firebase has supplied the authenticated App User ID.
- [`RevenueCatIos.kt`](../mobile/shared/src/iosMain/kotlin/com/example/triptandem/shared/RevenueCatIos.kt) provides the equivalent iOS KMP bootstrap, called by [`TripTandemApp.swift`](../mobile/iosApp/TripTandem/TripTandemApp.swift).
- The iOS host reads `RevenueCatPublicAPIKey` from the build configuration;
  Debug uses the local Test Store key, while Release defaults to an empty value
  and only receives a production public key when the signed release pipeline
  injects one. The key is passed to the public RevenueCat SDK only after
  Firebase anonymous authentication.
- The Firebase UID is used as the RevenueCat App User ID so the same account can be recognized across platforms after identity behavior is tested.
- `mobile/app` keeps the Test Store public SDK key in the **debug** BuildConfig only. Release builds default to an empty key and require `-PrevenueCatPublicApiKey=...` from the build pipeline.

RevenueCat public SDK keys are client identifiers, not server secrets. Never put
RevenueCat secret API keys, webhook authorization values, store private keys, or
receipt credentials in the app or repository.

## Local verification

Build and install the debug app, then use the Test Store environment to verify:

1. An authenticated Firebase session configures RevenueCat once; later auth
   changes call RevenueCat `logIn`/`identify` with the new Firebase UID.
2. The current offering exposes `$rc_monthly` and `$rc_annual`.
3. Purchase success returns an active `triptandem_pro` entitlement.
4. Cancellation, pending, offline, unavailable, and restore states map to the
   shared `TripTandemError` model.
5. A second app start restores the same App User ID and entitlement state.

On September 5, 2026, the Android CLI emulator completed this local flow with
the Test Store catalog: both monthly and annual packages loaded with localized
prices, the monthly purchase dialog exposed the configured one-week trial,
valid purchase activated `triptandem_pro`, Restore kept the entitlement active,
and cancelling a subsequent simulated purchase showed the neutral free-plan
message. This is development-only evidence; signed Play/App Store purchases
and the server webhook still require the production gates below.

The shared paywall, purchase buttons, restore flow, and entitlement refresh are
implemented in Phase 2. The backend verifier is also checked in as the
`revenueCatWebhook` Cloud Function and is the only path that can populate the
server-side billing document used by generation quotas. It is not live until
the Firebase project is upgraded to Blaze and the webhook secret/URL are
configured. The verifier accepts the configured authorization header or
RevenueCat's HMAC signature, rejects stale/replayed deliveries, and handles
`TRANSFER` payloads by moving verified Pro access across the
`transferred_from`/`transferred_to` aliases. Server-side Pro checks also fail
closed when the webhook mirror has no valid store-reported expiration timestamp;
the supported subscription and trial paths are time-bounded.

## MCP catalog verification

The catalog was re-read through RevenueCat MCP on September 5, 2026. The
`triptandem_pro` offering is active/current, its monthly and annual packages
are attached to the active `triptandem_pro` entitlement, and matching products
are registered for the Test Store, Android (`com.triptandem`), and iOS
(`com.triptandem`). The Android and iOS app entries still report that store
credentials are not configured, and the project has no webhook integration;
those are deliberate production gates rather than missing client code.

The latest read-only RevenueCat MCP check now requires OAuth reauthorization
(`invalid_grant` while refreshing the saved token). No RevenueCat catalog or
webhook configuration was changed; reauthorize the MCP connection before the
next live catalog/webhook audit.

## Production gate

Before a public store build:

- Configure the Play Store service account on `appfdf7251f21` and the App Store
  Connect credentials on `app9cd340929c`.
- Create the real subscription products and base plans in both stores, then
  register their exact store identifiers in RevenueCat and attach them to the
  same entitlement/offering.
- Replace the debug Test Store key with the matching platform public key in the
  release pipeline. The Test Store key must never be submitted to Google Play
  or the App Store.
- Test purchase, pending, cancellation, restore, expiration, refund/revocation,
  account transfer, and account deletion behavior on clean accounts.
- Deploy the checked-in server-side RevenueCat webhook verifier before any
  backend operation is gated by Pro. Firestore rules must not trust a
  client-supplied `isPro` field. The current Firebase project needs Blaze
  billing before Functions can be deployed.

See [PRD 10](prd/10-organizer-pro-and-revenuecat.md) for the full paywall,
entitlement, safety, and judge-access requirements.
