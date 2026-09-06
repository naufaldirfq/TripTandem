# Crash and performance observability

Phase 0 provisions Firebase Crashlytics and Performance Monitoring so release
owners can find crashes and regressions before enabling community features. The
SDKs are wired in the repository, but console verification on signed devices is
still a release-owner task.

## What is provisioned

### Android

- Firebase Crashlytics Gradle plugin `3.0.7` and Firebase BoM `34.17.0`.
- Firebase Performance Monitoring plugin `2.0.2` and the BoM-managed
  `firebase-perf` dependency.
- `FirebaseRuntime` sets only `platform=android` and the build type as custom
  keys. Authentication and RevenueCat identifiers are not copied into reports.

### iOS

- Firebase SPM products `FirebaseCrashlytics` and `FirebasePerformance` are
  linked in the XcodeGen-managed host, alongside Core, Auth, Analytics,
  Firestore, and Remote Config.
- The Release target runs Firebase's Crashlytics symbol-upload script and
  supplies the archive dSYM and app bundle as input files.
- `DEBUG_INFORMATION_FORMAT` is `dwarf-with-dsym`, so an archive has symbols
  available for symbolication.
- `AppDelegate` sets only `platform=ios` and the app version. Remote Config
  fetch failures are recorded as nonfatal diagnostics without adding payload
  data.

## Telemetry minimization

Crash and performance tools must never receive:

- Firebase UIDs, RevenueCat IDs, invite tokens, or access tokens;
- itinerary text, messages, report text, exact addresses, coordinates, or
  exact travel dates;
- contact details, profile biographies, or uploaded identity documents.

Use coarse screen/flow names and typed error categories only. If a future
feature needs extra diagnostics, document the field, retention, and privacy
impact before adding it. Crashlytics and Remote Config are not substitutes for
Firestore authorization.

## Device verification still required

The repository build verifies that the integrations compile. A release owner
must still complete the external verification:

1. Run a signed Android build and a signed iPhone build with the production
   Firebase configuration.
2. Confirm a controlled test crash reaches Crashlytics and is symbolicated.
   Use a temporary local-only test trigger, remove it before committing, and
   do not ship a crash trigger.
3. Confirm the iOS Release/archive upload completes and the Crashlytics issue
   has readable symbols. A simulator Debug run does not upload production
   dSYMs.
4. Inspect the report breadcrumbs, custom keys, traces, and network metadata
   for the prohibited fields above; record the result in the release notes.
5. Check Performance dashboards for startup/network regressions without
   attaching raw trip or location values to trace attributes.

Until this check is complete, treat crash-free and performance numbers as
unverified rather than as submission claims.
