# TripTandem mobile project

This is the Android CLI-generated Compose foundation for TripTandem, split according to the current Kotlin Multiplatform structure recommendations.

## Modules

- `app`: Android application entry point (`com.triptandem`). It owns the Android manifest and launches the shared `TripTandemApp()` composable.
- `shared`: Compose Multiplatform shared UI. It targets Android, `iosArm64`, and `iosSimulatorArm64`, and exports a static `TripTandemShared` framework for the iOS entry point.

The shared app includes the Phase 1 vertical slice: welcome/profile → Trips
dashboard → Create/edit a private trip → day-by-day itinerary → members and
secure invite links. Android and the iOS host inject native Firebase-backed
repositories for the private flow. Paywall UI and platform-store purchase
testing remain in the later PRD phases.

The UI bundles Cabinet Grotesk and Satoshi from Fontshare, matching the heading and interface typography in the root [`DESIGN.md`](../DESIGN.md). Android loads the packaged copies from `app/src/main/res/font`; iOS loads the shared Compose resources. See [`FONT_LICENSES.md`](FONT_LICENSES.md) for provenance and the pre-release license review reminder.

## Useful commands

```bash
./gradlew :app:test
./gradlew :app:assembleDebug
./gradlew :shared:compileKotlinIosSimulatorArm64
```

The iOS framework link task additionally requires a full Xcode installation and an iOS SDK. The Swift bridge is in `shared/src/iosMain`.

Firebase setup is documented in [`docs/firebase.md`](../docs/firebase.md). The
Android module uses `com.triptandem` and loads `app/google-services.json`; the
iOS host uses `iosApp/GoogleService-Info.plist` and links Firebase through Swift
Package Manager. Analytics, Crashlytics, Performance Monitoring, and
fail-closed Remote Config are described in
[`docs/analytics.md`](../docs/analytics.md),
[`docs/observability.md`](../docs/observability.md), and
[`docs/remote-config.md`](../docs/remote-config.md). See
[`docs/ios.md`](../docs/ios.md) for generation, build, and simulator commands.

RevenueCat catalog and SDK setup is documented in
[`docs/revenuecat.md`](../docs/revenuecat.md). The debug Android build uses a
Test Store public key; release builds must provide a real platform key with
`-PrevenueCatPublicApiKey=...`.
