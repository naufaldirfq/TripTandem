# Firebase foundation

TripTandem uses the existing Firebase project `triptandem` (project number
`405464191304`). The Android application ID and iOS bundle ID are both
`com.triptandem`.

## Registered apps

- Android: `1:405464191304:android:b4c93e514fc50e909ca37d`
- iOS: `1:405464191304:ios:b99c1f12af9ea5999ca37d`

Configuration files are checked into the platform setup locations:

- Android: `mobile/app/google-services.json`
- iOS: `mobile/iosApp/GoogleService-Info.plist`

The Android debug SHA-256 certificate is registered for local Google Sign-In
testing:
`e596bafdcd0b1e2c556bfc9f701eb300a7ac54ec7a802533f112d929e33ba440`.
A new local release/upload certificate is also registered:
`7c8fafd361b2629d58a3a0462f08e9d15550a9d9bc71dcaa0d47eeceb3b5c710`
(SHA-256) and `f830a2ead1363323bf2b0b1b2150f1f5f63aca6d` (SHA-1). The private
keystore remains outside the repository. The Play App Signing certificate will
be different after the app is created in Play Console and must be added then.

Firebase configuration files contain project identifiers and API keys intended
for client apps. They do not replace server-side authorization; Firestore rules
remain the security boundary.

## Local Firebase configuration

The repository root contains `.firebaserc`, `firebase.json`,
`firestore.rules`, and `firestore.indexes.json`. Authentication is configured
for anonymous development sessions, email/password, and Google Sign-In. The
Android and iOS hosts start an anonymous session on launch so a
backend-authenticated user is available while the required profile flow
restores or is completed; linking email/password keeps that user's profile and
trips on the same Firebase UID.

The shared auth screen now exposes Google and Apple actions. Android uses
Credential Manager for Google ID tokens and Firebase's browser OAuth provider
for Apple; iOS uses the Google Sign-In SDK and native Sign in with Apple with a
cryptographically hashed nonce. Both providers link to the current anonymous
Firebase user when possible, preserving the user's existing profile and trips.
The Apple provider still needs to be enabled in Firebase Console with an Apple
Services ID, Team ID, Key ID, and private key; private signing material must not
be committed to this repository.

The default Firestore database is provisioned in `asia-southeast2` (Jakarta),
Standard edition / Firestore Native mode. The secure rules and indexes are
versioned in this repository and can be deployed after review with:

```bash
npx -y firebase-tools@latest deploy --only firestore --project triptandem
```

The Functions entry in `firebase.json` includes a predeploy hook that runs
`npm --prefix "$RESOURCE_DIR" run build`, so a clean checkout compiles the
TypeScript source before a Functions deployment; ignored local `functions/lib`
artifacts are not required to be present in version control.

Do not use an open or time-limited ruleset. The checked-in rules default to
deny, keep user profiles private, restrict private trips to members, allow
itinerary edits only for owners/editors, validate atomic membership counter
changes, reject counter increments above trip capacity, and route owner
  removals through the authenticated `removeTripMember` callable; direct
  member status/delete mutations and direct trip-root deletes are denied;
  inactive trips are read-only for child writes. The current
source passes the local Firestore/Auth/Functions emulator matrix and was
deployed/live-verified through Firebase MCP as job `1788636174082`; the latest
live rules match the checked-in source byte-for-byte. The live index list also
contains the `members` collection-group composite index on `userId` and
`status`, required by the Trips membership query.

I've set up prototype Security Rules to keep the data in Firestore safe. They
are designed to be secure for this prototype because they deny by default,
enforce owner/member roles, validate allow-listed fields and bounded values,
and protect immutable ownership fields. However, you should review and verify
them before broadly sharing your app. If you'd like, I can help you harden
these rules.

A two-user REST smoke test passed: an authenticated owner could create and read
their own profile/trip, while a second authenticated user received permission
denied for both documents. The full regression matrix is documented in
[`firebase-rules-audit.md`](firebase-rules-audit.md); temporary test data and
accounts were removed.

The shared module exposes profile, trip, itinerary, member, and invite repository
contracts. Android and the iOS host provide native Firebase-backed adapters for
the complete private trip flow; they return typed `DataResult`/`TripTandemError`
values, use server timestamps, enforce optimistic revisions, and keep Firebase
SDK types out of common KMP code. The iOS adapter is in
`mobile/iosApp/TripTandem/FirebaseRepositories.swift`; it uses Firestore
transactions for invite acceptance/member leave and ownership transfer, while
owner removal calls the regional `removeTripMember` transaction.

Phase 1 itinerary records use `dayDate` (an ISO date in the destination
timezone) plus a per-day `position`. A record also carries `flexibleTime`; a
fixed clock entry stores a destination-local epoch derived from `dayDate`, the
entered `HH:mm`, and `destinationTimezone`, while a flexible entry keeps its
label and uses a zero timestamp sentinel for the required Firestore timestamp
field. Legacy records without `dayDate` are rendered on the trip start day.
Trip mutations and itinerary mutations carry a revision and stale writes are
returned as a typed conflict.

Creating an active trip uses the regional `createTrip` callable. It reads the
owner profile, verified RevenueCat billing state, and owner-trip query in one
transaction before writing the trip and owner's active membership, so the free
one-active-trip limit cannot be bypassed by a direct Firestore write. Direct
active-trip client creates are denied by rules; non-active legacy writes remain
validated and `open` visibility is still rejected in Phase 1. Member joins
increment `activeMemberCount` in the same transaction that
creates/reactivates membership and consumes an invite; owner role changes and
ownership transfer remain rules-guarded, while removal uses the authenticated
server transaction and leave remains a caller-owned atomic transaction. Invite
documents are addressed by the SHA-256
hash of the high-entropy token, so clients never enumerate the invite
collection; only the small safe preview is exposed before acceptance.

Trip deletion uses the regional `deleteTrip` callable. Direct trip-root deletes
are denied because Firestore parent deletes do not cascade into nested members,
itinerary, invites, and join requests. The callable locks the trip, marks it
archived/read-only, cancels and settles any generation jobs, drains every known
subcollection and generation lock, removes the root, and is idempotent on retry.
Account deletion requires a recent sign-in and blocks deletion when the user
owns an active shared trip. The owner must first transfer ownership or cancel/
delete the trip; otherwise deleting the user would strand collaborators. Profile
documents cannot be deleted directly through Firestore Rules. The mobile client
calls the regional `deleteAccountProfile` callable, which rechecks ownership on
the server, removes each owned trip root and its known `members`, `itinerary`,
`invites`, and `joinRequests` subcollections, cancels and settles any
generation jobs, removes per-trip generation locks, requester-scoped
generation jobs, and RevenueCat
entitlement/allowance documents in bounded, idempotent batches, then deletes the
profile in a guarded transaction before Firebase Auth is revoked. Firestore
parent deletes do not cascade, so this server boundary owns the complete graph
cleanup and fails closed if a trip appears during the operation. Production
still requires the signed device and abuse-control checks listed in the release
checklist.

RevenueCat is configured separately because it owns subscription products and
entitlements. The Firebase UID is passed as the RevenueCat App User ID after
anonymous authentication succeeds; see [`revenuecat.md`](revenuecat.md).

## Analytics, crash, performance, and rollout services

The shared UI emits a small, privacy-safe event set through
`TripTandemAnalytics`; Android sends these events to Firebase Analytics:

- `screen_view` (`screen_name`: coarse screen ID)
- `auth_anonymous_succeeded`
- `auth_anonymous_failed` (`error_type`: exception class only)
- `auth_session_restored`
- `trip_create_started`
- `trip_draft_continued`
- `invite_share_opened` (`channel_category`: coarse system-share category)

The shared event allowlist now forwards the Phase 2 monetization and AI
generation funnel events (`paywall_viewed`, package selection/purchase/restore
outcomes, entitlement changes, and the generation start/completion/preview/apply
failure events). They remain coarse and privacy-safe; no purchase payload,
receipt, transaction ID, prompt, destination, dates, notes, or generated text
is logged.

Never add itinerary text, exact locations, dates, member biographies, contact
information, or message content to analytics parameters. DebugView can be used
to verify events after enabling Analytics debug logging on a development device.

Android also includes Firebase Crashlytics and Performance Monitoring through
the Firebase BoM. iOS links `FirebaseCrashlytics`, `FirebasePerformance`, and
`FirebaseRemoteConfig` through Swift Package Manager. Crash reports use only
coarse platform/build metadata, and the iOS Release target includes the
Crashlytics dSYM upload phase. The privacy and verification contract is in
[`observability.md`](observability.md).

Remote Config is configured as a fail-closed rollout boundary. The checked-in
template and client defaults are documented in
[`remote-config.md`](remote-config.md). The approved all-disabled template is
deployed as Remote Config version 2 (Firebase MCP job `1788541626530`); all
five live values are Boolean `false` and future value changes still require
the same product and trust/safety review.

## Remaining Phase 0 gates

- Run the iOS host on a signed device and verify anonymous/email/Google/Apple
  Firebase Auth, Analytics,
  Crashlytics, Performance, Remote Config, and Firestore behavior. The
  simulator host, Firebase SPM products, plist, and
  `FirebaseApp.configure()` bootstrap are now present; see [`ios.md`](ios.md).
- Verify the registered debug/local-upload SHA-256 credentials and Analytics
  events in Android DebugView on a connected device. Add the separate Play App
  Signing SHA-256 after the Play app is created.
- Enable Apple in Firebase Authentication and complete Apple Developer Sign in
  with Apple setup (Services ID/return URL, key, capability, team, provisioning,
  and private email relay) before testing the Apple button.
- Verify that signed clients fetch the deployed Remote Config defaults and keep
  high-risk community features disabled until their feature reviews complete.
- Create the privacy-reviewed funnel dashboard and reconcile actual event
  counts; PRODUCT.md launch targets are not measured results.
- Record interview findings and replace the [privacy disclosure
  draft](privacy.md) with a reviewed policy before enabling production
  analytics collection.
- Re-run the emulator matrix and deploy through Firebase MCP after every
  reviewed rules change. The current source is live-verified; the latest
  deployment job is `1788636174082`.
- Keep the Firestore rules smoke test in CI as Phase 1 repositories write real
  user data.
