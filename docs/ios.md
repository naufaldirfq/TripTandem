# iOS host and simulator workflow

TripTandem now includes a generated iOS host at `mobile/iosApp`. The host uses
the shared Compose Multiplatform framework, Firebase's Swift Package Manager
products, and the iOS RevenueCat KMP bootstrap. Its bundle identifier is
`com.triptandem`, and its deployment target is iOS 16.

## What is checked in

- [`project.yml`](../mobile/iosApp/project.yml) is the XcodeGen source of truth.
- [`TripTandem.xcodeproj`](../mobile/iosApp/TripTandem.xcodeproj) is the generated
  Xcode project.
- [`TripTandemApp.swift`](../mobile/iosApp/TripTandem/TripTandemApp.swift)
  configures Firebase, starts anonymous auth, forwards shared analytics,
  forwards Google OAuth callback URLs, clears the Google session on sign-out,
  configures Crashlytics and Remote Config, and configures RevenueCat after
  Firebase supplies the user ID.
- [`FirebaseRepositories.swift`](../mobile/iosApp/TripTandem/FirebaseRepositories.swift)
  adapts Firebase Auth/Firestore to the shared profile, trip, itinerary,
  member, and invite repository contracts, including transactional membership
  changes.
- [`Info.plist`](../mobile/iosApp/TripTandem/Info.plist) contains the app
  metadata, the Firebase Google reversed-client-ID URL scheme, and the
  local-only RevenueCat Test Store public key.
- [`TripTandem.entitlements`](../mobile/iosApp/TripTandem/TripTandem.entitlements)
  declares the Sign in with Apple capability; the Apple Developer team and
  provisioning profile still have to be supplied by the release owner.
- `GoogleService-Info.plist` is the Firebase iOS client configuration for the
  `triptandem` project.
- `shared/src/iosMain` contains the Compose view-controller bridge and the
  iOS RevenueCat bootstrap.

The host links Firebase Core, Auth, Analytics, Firestore, Crashlytics,
Performance, and Remote Config plus the official Google Sign-In SDK through
Swift Package Manager. Release builds
run the Crashlytics symbol-upload phase and include the archive dSYM as an
input. Remote Config starts with all rollout flags disabled and preserves those
safe defaults when the fetch is unavailable. See
[`observability.md`](observability.md) and
[`remote-config.md`](remote-config.md).

The Xcode target's pre-build script links
`:shared:linkDebugFrameworkIosSimulatorArm64` (or the device/release equivalent)
and runs the matching `ios*AggregateResources` task. Its post-build script then
copies the aggregated Compose Multiplatform resources into the
`compose-resources/composeResources` path expected by the generated resource
accessors. This makes a fresh checkout reproducible and keeps the Cabinet
Grotesk and Satoshi fonts available on iOS.

## Prerequisites

- Xcode 26.6 with an iOS Simulator runtime.
- Full Xcode selected for command-line builds. The repository does not require
  changing the machine-wide `xcode-select` value:

  ```bash
  export DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer
  ```

- XcodeGen when the project specification changes:

  ```bash
  brew install xcodegen
  ```

## Generate, resolve, and build

From `mobile/iosApp`:

```bash
export DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer
xcodegen generate --spec project.yml
xcodebuild -resolvePackageDependencies \
  -project TripTandem.xcodeproj \
  -scheme TripTandem
xcodebuild \
  -project TripTandem.xcodeproj \
  -scheme TripTandem \
  -configuration Debug \
  -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath /tmp/triptandem-ios-derived \
  CODE_SIGNING_ALLOWED=NO \
  build
```

The generated project already contains its Firebase package references and
`Package.resolved`. Run `xcodegen generate` again only when `project.yml`
changes; XcodeGen rewrites the project, so Firebase products must then be
relinked through Swift Package Manager (or the Firebase Xcode project setup
helper) before building.

The build invokes Gradle automatically to produce the shared framework. The
Firebase package dependency is resolved through Swift Package Manager and the
Firebase setup helper in the Firebase Xcode project skill; `-ObjC` is kept in
the target's linker flags.

## Install and run on a simulator

List available devices, boot one, and open the Simulator app:

```bash
export DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer
xcrun simctl list devices available
open -a Simulator
```

Then install and launch the built app (replace the device ID with a booted
simulator from the previous command):

```bash
xcrun simctl install <device-id> \
  /tmp/triptandem-ios-derived/Build/Products/Debug-iphonesimulator/TripTandem.app
xcrun simctl launch <device-id> com.triptandem
```

The Phase 0 host was built, installed, and launched successfully on the iPhone
17 iOS 26.5 simulator on September 3, 2026. The dashboard rendered with the
custom typography and no new crash report after the resource-path fix.

The Phase 2 host was rebuilt and launched again on the booted iPhone 17
simulator on September 5, 2026 from the current `com.triptandem` bundle. The
welcome route rendered successfully. Interactive iOS Test Store
purchase/cancellation/restore and all signed App Store flows remain release
owner checks; Android Test Store states are recorded in
[`revenuecat.md`](revenuecat.md).

## Remaining iOS release gates

- Configure an Apple Developer team, signing, provisioning, and Keychain
  capabilities; the unsigned simulator build is not a store-equivalent test.
- In Firebase Console, enable Apple Authentication and provide the Apple
  Services ID, Team ID, Key ID, and private key. Register the Firebase Auth
  handler URL (`https://triptandem.firebaseapp.com/__/auth/handler`) as an
  Apple return URL; never commit the private key.
- Add the production App Store public RevenueCat key only after App Store
  Connect products and RevenueCat store credentials are configured. The key in
  `Info.plist` is a Test Store key and must never ship.
- Verify anonymous/email/Google/Apple auth, Analytics DebugView, Firestore
  reads, and purchase/restore behavior on a signed device build. Google needs
  the reversed client-ID URL scheme already checked into `Info.plist`; Apple
  needs the capability and a matching provisioning profile.
- Verify Crashlytics/performance data and dSYM symbolication on a signed device
  while confirming sensitive itinerary, location, invite, and report data are
  scrubbed. The integrations are already wired in the host; this is the
  external verification gate.
