# TripTandem

TripTandem is a Kotlin Multiplatform mobile app for planning a trip, finding compatible travel companions, and organizing the journey together.

## Product documentation

- [Product plan](PRODUCT.md)
- [Design system](DESIGN.md)
- [Feature PRD index](docs/prd/README.md)
- [Firebase foundation](docs/firebase.md)
- [Analytics taxonomy](docs/analytics.md)
- [Crash and performance observability](docs/observability.md)
- [Remote Config rollout controls](docs/remote-config.md)
- [RevenueCat monetization setup](docs/revenuecat.md)
- [Judge and demo guide](docs/judge-guide.md)
- [Phase 0 release checklist](docs/release-checklist.md)
- [Phase 1 implementation status](docs/phase1-status.md)
- [Phase 2 implementation status](docs/phase2-status.md)
- [Manual end-to-end test guide (HTML)](docs/manual-e2e-guide.html)
- [Interview guide and findings log](docs/interviews.md)
- [Privacy disclosure draft](docs/privacy.md)
- [Generated app icon](assets/brand/triptandem-app-icon-1024.png)

The product plan is intentionally phased for the RevenueCat Shipaton 2026 deadline. A phase starts only after the previous phase's exit criteria are met.

## App foundation

The Android CLI-generated project lives in [`mobile`](mobile). It is already split into platform entry points and shared UI so the implementation can stay eligible for the Kotlin Multiplatform category:

- `mobile/app` — Android application entry point and manifest.
- `mobile/shared` — Compose Multiplatform UI and shared code for Android and iOS targets.
- `mobile/iosApp` — XcodeGen-managed iOS host, Firebase SPM links, and shared
  framework entry point.
- `mobile/shared/src/iosMain` — `MainViewController()` bridge and iOS
  RevenueCat bootstrap.

The Android application ID and future iOS bundle ID are `com.triptandem`.

From the `mobile` directory:

```bash
./gradlew :app:test
./gradlew :app:assembleDebug
```

The iOS common source set can be checked without Xcode with:

```bash
./gradlew :shared:compileKotlinIosSimulatorArm64
```

For a release build, pass the platform's RevenueCat public API key from the
build environment rather than committing it:

```bash
./gradlew :app:assembleRelease -PrevenueCatPublicApiKey="$REVENUECAT_ANDROID_PUBLIC_KEY"
```

The current debug build intentionally uses the RevenueCat Test Store key. It
is for local verification only; production store keys and store credentials
remain a release-gate task.

The iOS host build and simulator workflow is documented in
[`docs/ios.md`](docs/ios.md). It requires the full Xcode toolchain on macOS;
set `DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer` for commands if
the machine-wide developer directory still points at Command Line Tools.
