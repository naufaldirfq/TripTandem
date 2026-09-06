# Phase 0 release and validation checklist

This checklist records what can be completed in the repository and what still
requires an external credential, device, or human decision. It is intentionally
kept next to the implementation so a future release owner can close the gates
without guessing.

## Completed in the repository

- [x] Android application ID and iOS bundle ID are `com.triptandem`.
- [x] Firebase project `triptandem` is selected; Firestore is in
      `asia-southeast2`.
- [x] Anonymous, email/password, and Google sign-in providers are configured;
      shared Android/iOS provider flows are implemented. Apple provider
      credentials remain an external Firebase/Apple Developer gate.
- [x] Firebase Analytics wrapper and privacy-safe event taxonomy exist.
- [x] Firestore rules and indexes were deployed and live-verified through
      Firebase MCP after the Phase 1/2 review. The Auth/Firestore/Functions emulator
      matrix also passes, including the AI-attribution spoofing and retained
      generation-preview reauthorization regressions.
      See [the rules audit](firebase-rules-audit.md).
- [x] Active-trip creation is server-authoritative: the regional Functions
      transaction counts owner trips and checks verified RevenueCat webhook
      state; direct active-trip Firestore writes are denied. The combined
      Firestore/Auth/Functions emulator matrix covers the free second-trip
      rejection.
- [x] RevenueCat Test Store project, entitlement, offering, products, and
      shared KMP coordinator are configured. See [RevenueCat setup](revenuecat.md).
- [x] RevenueCat Test Store monthly and annual products expose a one-week
      `never_purchased` trial for local judge/demo verification; production
      trial offers remain store-credential gates.
- [x] A single [judge and demo guide](judge-guide.md) documents the verified
      local trial path and keeps signed-store/production gates explicit.
- [x] Android CLI Test Store verification loaded the localized monthly/annual
      packages, showed the one-week trial phase, activated and restored the
      simulated `triptandem_pro` entitlement, and handled a cancelled purchase
      with a neutral free-plan message. Signed Play/App Store purchase tests
      remain an external release gate.
- [x] Android debug APK, Android unit tests, and iOS simulator common-code
      compilation pass with Gradle.
- [x] The Android debug APK was installed and launched on the booted API 36
      emulator; the dashboard rendered after an emulator System UI warning
      was dismissed, and no `AndroidRuntime` crash was logged.
- [x] `:shared:linkDebugFrameworkIosSimulatorArm64` produces
      `TripTandemShared.framework` with Xcode 26.6 when `DEVELOPER_DIR` points
      at the installed Xcode toolchain.
- [x] XcodeGen-managed iOS host target exists at `mobile/iosApp`, with bundle
      ID `com.triptandem`, Firebase SPM products, and the shared framework
      bridge, including native Firebase repository adapters.
- [x] iOS simulator build, install, and launch were verified on the iPhone 17
      iOS 26.5 runtime. Compose resources and custom fonts are copied into the
      app bundle at the path expected by generated resource accessors.
- [x] `docs/privacy.md` is present as a clearly labeled product-development
      disclosure draft.
- [x] Xcode 26.6 is installed on the development host.
- [x] Firebase Crashlytics and Performance Monitoring SDKs are wired for
      Android and iOS, with privacy-minimized custom keys and an iOS Release
      dSYM upload phase. See [observability](observability.md).
- [x] Account deletion invokes the authenticated regional
      `deleteAccountProfile` boundary before revoking Auth. The server
      rechecks active shared-trip ownership, removes each owned trip root and
      its known subcollections, requester-scoped generation jobs and jobs on
      owned trip graphs, per-trip generation locks, and RevenueCat
      entitlement/allowance metadata, and
      deletes the profile only through the guarded callable; the emulator smoke
      test verifies direct delete denial, ownership blocking, full trip-graph
      cleanup, and idempotent retries.
- [x] Trip deletion invokes the authenticated regional `deleteTrip` callable;
      direct Firestore root deletes are denied, generation reservations/jobs and
      nested trip data are drained before the root is removed, and cancelled
      trips are read-only to clients.
- [x] A fail-closed Remote Config template, platform defaults, and client
      fetch boundaries are checked in, and the approved all-disabled template
      is deployed as Firebase Remote Config version 2 (MCP job
      `1788541626530`). See [Remote Config](remote-config.md).
- [x] A local Android release/upload certificate was generated outside the
      repository and its SHA-256/SHA-1 fingerprints were registered in the
      Firebase Android app. The Play App Signing fingerprint remains a later
      Play Console gate.

The following PRD 12 growth/release controls still need an external review,
device, or data source; the repository implementation is otherwise present:

- [ ] On signed Android and iOS builds, trigger a controlled test crash,
      confirm symbolication/performance data, and verify that auth tokens,
      invite URLs, exact locations, itinerary text, and report content are
      scrubbed. See [observability](observability.md).
- [ ] Reconcile install → activation → collaboration → purchase funnel metrics
      and invite attribution from a privacy-reviewed dashboard. See
      [analytics](analytics.md).
- [x] Deploy and live-verify the current checked-in Firestore rules after
      review. Firebase MCP deployment job `1788636174082` succeeded and the
      live rules match the checked-in source byte-for-byte; a rules-only dry
      run also compiled successfully.

## Release certificate and Firebase

The debug SHA-256 certificate is registered for local Google Sign-In, and a
local release/upload certificate is now registered as well. Do not register a
made-up release fingerprint. The private keystore is outside the repository;
the public fingerprints are recorded in [`firebase.md`](firebase.md). When the
app is created in Play Console, obtain the separate Play App Signing
certificate and register its SHA-256 too. To inspect a local keystore, run:

```bash
keytool -list -v -keystore <release-keystore> -alias <release-alias> | rg "SHA256"
```

Register that exact SHA-256 in the Firebase Android app, add the matching SHA-1
for Google Sign-In, then repeat sign-in from a clean install. Keep the keystore
and passwords outside the repository and CI logs.

## Analytics DebugView

On a connected Android development device:

```bash
adb shell setprop debug.firebase.analytics.app com.triptandem
adb logcat -c
adb shell am force-stop com.triptandem
adb shell monkey -p com.triptandem 1
adb logcat -d -v brief -s FA FA-SVC FirebaseRuntime
```

Confirm `screen_view` and the anonymous-auth result appear with only the
allow-listed coarse parameters. Do not paste full logcat output into public
Devpost material.

The event inventory, privacy contract, and planned funnel are recorded in
[`analytics.md`](analytics.md). Dashboard reconciliation remains a manual step
because this workspace has no production user dataset.

## iOS host

The repository now includes an XcodeGen source spec, generated
`mobile/iosApp/TripTandem.xcodeproj`, Firebase SPM links, the shared
`MainViewController()` bridge, and native Firebase repository adapters. Xcode
26.6 is installed; `DEVELOPER_DIR` can be used because `xcode-select` still
points at `/Library/Developer/CommandLineTools`.
The simulator portion is complete. A release owner must:

1. Complete Xcode's first-launch components/license if needed and use the
   commands in [`ios.md`](ios.md) to regenerate or build the host.
2. Configure an Apple Developer team, signing, provisioning, Keychain and Sign
   in with Apple capabilities. Enable Apple in Firebase Authentication with the
   Services ID, Team ID, Key ID, private key, and the Firebase Auth handler URL.
   The Google reversed-client-ID URL scheme is already in `Info.plist`.
3. Run the signed app on a store-equivalent iPhone and verify anonymous, email,
   Google, and Apple auth, Analytics, Firestore, and Test Store
   purchase/restore behavior.
4. Replace the local Test Store key with the production App Store public key
   only after App Store Connect products and RevenueCat store credentials are
   configured.

Crashlytics, Performance Monitoring, and Remote Config are now provisioned in
the source project. Device verification and dSYM inspection still require the
release owner. Future Remote Config changes must go through the review process
in [`remote-config.md`](remote-config.md); the initial all-disabled template is
already published. See [`observability.md`](observability.md) and
[`remote-config.md`](remote-config.md).

## Human and policy gates

- [ ] Conduct at least five short user interviews using [the interview guide](interviews.md).
- [ ] Record the top three findings and resulting scope decisions in that log.
- [ ] Product/legal owner reviews and replaces the [privacy disclosure draft](privacy.md).
- [ ] Decide the audience age gate, retention windows, support contact, and
      account-deletion copy before store submission.
- [ ] Complete store metadata, subscription disclosures, Terms, and Privacy
      links from clean signed-out sessions.

Until these boxes are checked, Phase 0 is not marked complete and high-risk
open-trip discovery remains behind its safety gate.
