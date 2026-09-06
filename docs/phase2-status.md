# Phase 2 implementation status

Updated: 2026-09-06

Phase 2 covers PRD 04 (AI itinerary generation) and PRD 10 (Organizer Pro and
RevenueCat). The product code and cross-platform boundaries are implemented,
but production rollout is intentionally gated: the Firebase project is not on
Blaze, the server functions cannot be deployed yet, and the Remote Config AI
flag remains `false`.

The current Firebase MCP environment still reports `Billing Enabled: No`, and
the live project has no deployed Functions. This is an external project-state
gate, not a missing local implementation; Firestore rules/indexes remain live.

## PRD 04 review

| PRD requirement | Implementation status |
| --- | --- |
| Structured whole-trip, single-day, and fill-empty-days generation | Implemented in a server-created job with bounded, versioned JSON output. Fill-empty validation only accepts dates with no active itinerary items. |
| Minimum-data, server-only provider boundary | Implemented. Prompts contain trip planning fields and explicit generation notes only; member names, emails, bios, contact details, exact lodging, private notes, and account identifiers are excluded. The Gemini key is a Functions secret. |
| Preview before save and explicit Apply | Implemented. Preview items are sanitized, editable, and selectable; Apply writes canonical itinerary fields in one transaction. Existing items are never replaced silently. Retained preview reads re-check the active profile and owner/editor membership, so a removed or suspended requester cannot read private generated content. |
| Locked/booked items and concurrent edits | Implemented. Generation never mutates existing items; every active booked item is enforced as a server-side constraint (the client list is disclosure only), the server sends only coarse booked windows (date, time label, duration, and type), rejects definite overlaps, discloses booked IDs in the parameter sheet, and Apply checks the trip revision plus the complete bounded itinerary document set and every item revision, catching additions, removals, and edits. |
| Validation, duplicate warnings, attribution, and uncertainty | Implemented. Dates, types, labels, durations, text lengths, duplicate title/place/time similarity, and organizer edits are validated server-side. Applied entries carry `generatedBy=triptandem_ai`; the UI shows an AI draft badge, assumptions, warnings, an unverified-information notice, and a visible “Check details” action. |
| Usage and failure behavior | Implemented. A transaction reserves one free or monthly Pro slot, failed/cancelled jobs release it, successful jobs consume it, and jobs expire after seven days. Quota, provider, safety, timeout, cancellation, and partial-result states preserve the existing plan. |
| Retry/cancel/idempotency | Implemented. Retry uses the preserved parameter draft, cancellation is race-safe, generation creation uses a per-user/per-trip server lock with stale-lock recovery, and Apply uses a caller idempotency key plus deterministic item IDs. |
| Worker authorization revalidation | Implemented. After claiming a queued job, the worker rechecks the active age-confirmed profile, trip status, owner/editor membership, job identifiers, and schema version immediately before reading itinerary context or calling Gemini. Revoked access fails closed and leaves the existing itinerary unchanged. |
| Analytics and privacy | Implemented. Generation funnel events use only scope, entitlement state, coarse counts, latency/result buckets, and failure class. Prompts, destinations, dates, notes, and generated text are not analytics values. |

The PRD's P1 weather-aware and compatibility-aware enrichment remains
intentionally deferred: no licensed current-weather provider or reviewed
aggregate compatibility projection is configured. The Phase 2 path does not
claim either capability, so enabling the AI flag cannot accidentally imply
fresh weather or member-profile reasoning.

## PRD 10 review

| PRD requirement | Implementation status |
| --- | --- |
| Shared KMP RevenueCat boundary and stable identity | Implemented. Android and iOS configure the public SDK with the authenticated Firebase UID, explicitly re-identify on auth changes, refresh on launch/foreground, and share the `triptandem_pro` entitlement contract. |
| Organizer Pro paywall | Implemented. The shared paywall loads the current RevenueCat offering, displays localized store prices and RevenueCat billing period/introductory-offer eligibility, supports monthly/annual selection, close, purchase, restore, Terms, and Privacy, and keeps the free experience usable when products are unavailable. |
| Purchase state handling | Implemented. Pending and user-cancelled purchases map to distinct, non-alarming states; entitlement is granted only when RevenueCat reports it active. Purchase/restore/entitlement analytics are coarse and identifier-free. |
| Value-triggered gating | Implemented in the shared UI and server boundary for a second active trip, a free-plan six-traveler capacity cap, and additional AI allowance. Active-trip creation is a Functions transaction that counts owner trips and checks only verified RevenueCat webhook state; direct active-trip Firestore writes are denied. Joining, invites, manual planning, safety, and existing content remain free. |
| Settings upgrade entry point | Implemented. Existing users can open Organizer Pro from Profile settings; the first-use flow does not show a paywall, and joined trips never consume the organizer's Free active-trip allowance. |
| Server-side entitlement source | Implemented in code through an idempotent RevenueCat webhook that filters to `triptandem_pro`, verifies the configured authorization header or RevenueCat HMAC signature, handles source/destination `TRANSFER` aliases, ignores duplicate/out-of-order deliveries, and preserves access through cancellation, billing-issue, or pause events until the store-reported expiration. No client `isPro` flag is trusted by the generation worker or active-trip creation transaction. Deployment and webhook configuration are still gated by Firebase billing and store setup. |
| Organizer-funded trip transfer | Implemented. The transfer confirmation warns that organizer-only limits follow the new owner; the trip and existing members remain available while new Free-plan actions are gated. |

The optional one-trip Organizer Pass is also deferred; monthly and annual Pro
are the only catalog packages, avoiding ambiguous entitlement duration or
restore semantics before the signed-store configuration is complete.

The verified local Test Store flow and the owner handoff for a signed judge
build are consolidated in [`docs/judge-guide.md`](judge-guide.md). It keeps
the one-week local trial instructions separate from the production credential
and store-verification gates.

The capability table in PRD 10 also names advanced compatibility filters and
enhanced offline/export as Organizer Pro value areas. Those are intentionally
not Phase 2 implementation claims: compatibility filtering is delivered with
the open-trip discovery work in PRD 06 (Phase 3), while encrypted offline
cache and export are scoped to PRD 11 (Phase 4). The current Phase 2 paywall
therefore does not show a misleading export/filter trigger, and no Pro access
is granted for capabilities that are not yet present.

## Verification completed

- `functions/npm test`: 28 validation, booked-window, concurrency, provider-safety, webhook,
  editing, and active-trip entitlement tests pass and TypeScript builds cleanly.
- `mobile/./gradlew :shared:compileAndroidMain :app:testDebugUnitTest
  :app:assembleDebug :app:assembleRelease
  :shared:linkDebugFrameworkIosSimulatorArm64
  :shared:linkReleaseFrameworkIosSimulatorArm64`: passed when the installed
  Xcode toolchain is selected with `DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer`.
  The XcodeGen host script now also runs the target-specific Compose resource
  aggregation task so a clean iOS checkout packages the shared fonts reliably.
  The exact Debug framework-plus-resource target pair was rerun from a missing
  aggregation directory and completed successfully.
- The Debug APK (`mobile/app/build/outputs/apk/debug/app-debug.apk`) and the
  default Release build both pass; Release keeps the public RevenueCat key
  empty until the signed production pipeline injects one.
- The current Debug APK installed and launched on the `Medium_Phone_API_36.1`
  emulator with `com.triptandem/.MainActivity` focused and no startup crash in
  recent logcat. On 2026-09-06, the standard
  `./gradlew :app:connectedDebugAndroidTest` task passed the connected
  shared-app instrumentation smoke tests (2 tests, 0 failures): the
  welcome/profile route and the fail-closed AI flag boundary. The remaining
  signed-store and full user-journey acceptance still require manual device
  verification.
- The shared welcome route-marker composition was visually rechecked on the
  Android `Medium_Phone_API_36.1` emulator and iPhone 17 Simulator after the
  compact-height adjustment; the eyebrow, headline, and supporting copy remain
  fully legible without decorative-marker overlap.
- Shared choice chips now expose selected/not-selected state to screen readers,
  and group-capacity increment/decrement controls expose action labels on both
  Android and iOS. Android shared compilation, Debug packaging, and the iOS
  Simulator build were rerun after this accessibility hardening.
- The Android Release APK was checked for the local Debug/Test Store key and
  does not contain it; a production public key still must be supplied by the
  signed release pipeline.
- The current clean `xcodebuild` Debug and Release Simulator app targets both
  passed with `DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer` and
  `CODE_SIGNING_ALLOWED=NO`; the shared KMP framework/resource link and the
  Release bundle's empty RevenueCat key also passed. These are unsigned
  simulator checks; signed-store purchase and account-lifecycle verification
  remain release-owner gates.
- Firebase MCP Firestore deployment job `1788636174082` completed successfully
  (100% progress; rules and indexes deployment).
  A follow-up live fetch is an exact byte-for-byte match with the checked-in
  `firestore.rules` (36,345 bytes) and confirms callable-only profile deletion,
  active shared-trip ownership blocking, and immutable AI attribution
  fields, the direct active-trip create guard, direct capacity and
  inactive-to-active reactivation guards, the membership-transition counter
  guard, direct owner/member status and delete denial, direct trip-root delete
  denial, cancelled-trip read-only child writes, and the trusted
  owner-removal boundary plus the membership-transition trip-field allowlist
  (SHA-256
  `3901ca9f8e03f76cb13b1a27f344c12b7c37171fc4d2f88f58a087dc2804da68`); the
  checked-in index configuration is deployed as well, including the
  collection-group `members` query index. A rules-only Firebase CLI dry-run
  also compiled the source successfully after that deployment.
- Android CLI live-project verification on `Medium_Phone_API_36.1` reloaded the
  authenticated profile after the index became ready and rendered the expected
  `0 plans` / `No trips yet` state with no permission or conflict banner.
- Firestore, Auth, and Functions emulator smoke tests pass for owner, private/
  open-trip, server-authoritative active-trip creation and Pro gate, invite,
  counter, role, itinerary, report, AI-metadata, and owner/membership query
  boundaries (including denial of unscoped queries). The matrix now also covers
  direct trip-root delete denial, cancelled-trip read-only child writes, and
  nested graph cleanup plus idempotent retry through `deleteTrip`.
- A GitHub Actions workflow at `.github/workflows/verify.yml` now runs the
  Functions build/tests and the same Firestore/Auth/Functions emulator smoke
  matrix on every push and pull request, keeping the authorization regression
  gate repeatable in CI. Its function-readiness probe was exercised locally;
  the full CI-equivalent smoke run passed after the Functions emulator finished
  loading its regional callable definitions.
- The final emulator run also loaded the regional Functions and passed the
  direct-write, Pro-gate, membership-counter, invite, report, and AI metadata
  checks after the RevenueCat webhook and provider-failure hardening.
- The trusted `removeTripMember` callable now rejects cancelled, completed, and
  archived trips with `trip_read_only`, matching the read-only child-write
  rules and PRD 02's promise that cancelling preserves a read-only record.
  The emulator smoke matrix covers this boundary before exercising idempotent
  trip deletion.
- Active generation job IDs persist in the platform draft stores; returning to a
  trip rehydrates queued/running jobs and resumes foreground polling without
  caching prompts or provider output.
- The iOS XcodeGen spec and generated project link Firebase Performance;
  iOS analytics screen names are allowlisted and nonfatal Crashlytics
  diagnostics strip SDK-localized payloads before recording. The Debug
  Simulator build passed after these observability/privacy hardening changes.
- AppDelegate authentication lifecycle events now use the same iOS analytics
  bridge as the shared KMP UI, so session-restored, anonymous-success, and
  anonymous-failure events cannot bypass the event/parameter allowlist. Both
  Debug and Release Simulator builds pass after this parity fix.
- The approved `itinerary_day_viewed.relative_day_bucket` analytics field is
  now forwarded by both platform wrappers; the shared event taxonomy and
  platform allowlists no longer disagree.
- The unused link-copy event was removed, and
  `scripts/check_analytics_parity.mjs` now verifies that the shared taxonomy,
  Android allowlist, iOS allowlist, and parameter schemas stay aligned in CI.
- `scripts/check_remote_config_parity.mjs` now verifies all five independent
  rollout flags, their Boolean false defaults, and both platform readers in CI.
- Android's strict Remote Config parser has unit coverage for malformed values;
  the refreshed Android unit/connected smoke suites pass (2 connected tests),
  and the current Debug APK launches on `emulator-5554` with a live process and
  no startup exception in recent logcat.
- iOS Remote Config now opts a capability in only for an explicit `true`
  string; values accepted by Foundation's broader `boolValue` conversion (for
  example `Y` or `1`) fail closed like the server gate and Android boundary.
- iOS RevenueCat configuration is now split by build type: Debug uses the
  local Test Store key, while Release defaults to an empty key and requires an
  explicitly injected production public key. Both Debug and Release Simulator
  builds passed after the release logging hardening; the Release bundle
  contains no Test Store key.
- iOS Firebase callable failures now use the SDK's `FunctionsErrorDomain`
  (`com.firebase.functions`) and map the documented Pro-gate, duplicate, and
  coarse AI failure codes—including the neutral safety refusal—to the same
  shared error types as Android. Debug and Release Simulator builds pass after
  this cross-platform parity fix.
- The shared paywall now exposes only billing options present in the current
  RevenueCat offering; a partial or empty catalog cannot display a misleading
  fallback price or attempt a missing package, and the free experience remains
  available.
- RevenueCat catalog reads now ask the store for trial/introductory-price
  eligibility before displaying an introductory offer. Purchase selection also
  falls back to matching monthly/annual package identifiers when a catalog does
  not populate RevenueCat's canonical package slots.
- The RevenueCat Test Store monthly and annual products now each expose a
  one-week `never_purchased` trial, giving judges a functioning local trial
  path while platform-store credentials and production offers remain gated.
- The server-side AI rollout boundary now re-reads the reviewed Remote Config
  template default before both job creation and worker execution. Missing,
  conditional-only, malformed, or unavailable values fail closed; the helper
  has dedicated unit coverage. The client flag remains a presentation aid and
  never grants server authorization.
- The selected Gemini Developer API provider/model, minimum prompt fields,
  seven-day TripTandem retention boundary, and provider data-use terms are
  documented in [`docs/privacy.md`](privacy.md). Production enablement remains
  gated on billing, secret provisioning, and product/legal review.
- The Functions worker sends the Gemini key in the `x-goog-api-key` header,
  keeping it out of provider URL query strings and typical access logs.
- The Gemini 3.x request omits deprecated sampling parameters and keeps
  structured JSON generation schema-constrained for the selected model.
- The active-generation duplicate guard now inspects the complete
  requester-scoped `queued`/`running` query, so a later concurrent request
  cannot evade the one-job-per-trip check by falling outside a small result
  page.
- Generation creation now also reserves a deterministic per-user/per-trip
  server lock in the same Firestore transaction as the new job. This closes
  the concurrent-create race, recovers locks whose referenced job is terminal
  or missing, and releases them after completion/cancellation/apply. Account
  cleanup and the scheduled retention purge remove orphaned locks.
- Verified Pro billing now fails closed when the webhook mirror has no valid
  store-reported expiration timestamp; the emulator matrix covers a malformed
  active entitlement and confirms it cannot unlock a second active trip.
- Profile edits on Android and iOS now update only mutable fields, preserving
  immutable consent/account metadata required by the Firestore rules; the
  rules smoke matrix covers a post-onboarding profile update.
- Firestore rules and server callables now require an age-confirmed active
  profile for trip/community/generation mutations; the age-confirmation value
  is immutable after onboarding and the emulator matrix covers rejection of an
  unconfirmed profile.
- The generation worker revalidates stored job input immediately before
  constructing a provider prompt, so legacy or manually repaired jobs fail
  closed without crossing the minimum-data boundary.
- Retained generation preview reads now revalidate the requester's active,
  age-confirmed profile and current owner/editor membership before returning
  destination or suggestion data; removed, suspended, archived, or malformed
  job contexts fail closed at the callable boundary. The emulator smoke matrix
  exercises an active editor read plus suspension and membership-removal
  revocation cases.
- The Firestore-triggered generation worker treats an empty or malformed event
  payload as a no-op, so a repaired/removed document cannot crash the worker or
  trigger a provider call without a complete queued job.
- RevenueCat purchase and transfer webhook decisions now fail closed on missing
  expiry for the time-bounded TripTandem catalog, matching the server Pro-gate
  contract and preventing UI/backend entitlement drift.
- The scheduled retention purge now continues into the orphan-lock sweep even
  when its expired-job page is empty, so stale locks cannot survive a quiet job
  collection indefinitely.
- Public generation-job responses now rebuild failure messages from a bounded
  allowlist and sanitize preview fields, so legacy or malformed provider text
  cannot escape through the callable. The safety prefilter also covers the
  user-controlled daily start/end labels before a provider request.
- Generation Apply rechecks the retention deadline inside its Firestore
  transaction, so a preview cannot be applied after it expires during review.
  Public job states, counters, dates, time labels, and item fields also fail
  closed when a legacy or malformed job falls outside the versioned contract.
- Generation cancellation, expiry, feature-disabled, and provider-failure
  transitions are now transactionally guarded so a concurrent cancellation
  cannot be overwritten by a terminal failure state. Offline hosts disable
  new generation requests and Retry while preserving the reviewed parameter
  draft locally (Android `ConnectivityManager`, iOS `NWPathMonitor`).
- The generation worker allows 90 seconds for the bounded provider timeout and
  its final allowance transaction, reducing the chance that a provider timeout
  leaves a reservation unsettled.
- Purchase failures and restore failures now emit stable coarse analytics
  result classes rather than Kotlin/SDK class names; restore completion is
  recorded for both success and failure paths, with `active=false` on a failed
  restore so the event shape remains consistent across platforms.
- Entitlement refresh now re-publishes the coordinator on Android foreground
  transitions and rebuilds the iOS shared host after a successful foreground
  refresh, so store-side renewal/expiration changes reach Pro-gated UI without
  navigation.
- RevenueCat identity switching and entitlement/catalog reads are serialized in
  the shared coordinator, so an auth-change or foreground refresh cannot read
  the previous Firebase UID's entitlement while `logIn` is still completing.
- Android and iOS clear the previous RevenueCat coordinator when Firebase Auth
  signs out, and both republish the authenticated UID on foreground transitions;
  the shared paywall clears its cached Pro status before loading the replacement
  coordinator. This prevents a previous account's entitlement from leaking into
  the next auth session.
- Android's Firebase auth-state callback now reads the nullable
  `FirebaseAuth.currentUser` explicitly and clears the RevenueCat coordinator
  during the sign-out gap before the replacement anonymous session is ready;
  this avoids a null-user crash at the account handoff boundary.
- RevenueCat `TRANSFER` webhooks now process `transferred_from` and
  `transferred_to` aliases (including payloads without `app_user_id`) and move
  only verified Pro access; malformed path IDs are rejected. HMAC-signed
  deliveries are verified against the exact raw request body with a bounded
  timestamp tolerance.
- Paywall-view analytics waits for the initial RevenueCat catalog request to
  settle, preventing a transient loading frame from recording an incorrect
  `offering_id=unavailable` result.
- Generation quota counters now accept only bounded non-negative integers;
  malformed legacy usage documents fail closed instead of granting extra AI
  allowance.
- A queued generation job is re-authorized immediately before the provider
  call. The worker requires an existing active, age-confirmed profile and a
  draft/planning/confirmed trip with owner or active editor access; revoked
  access is recorded as a coarse `access_revoked` failure and the existing
  itinerary is never changed. The shared Android and iOS adapters map that
  failure without exposing provider or identity data.
- Gemini structured output now requires the exact versioned response contract
  before item validation, so an older or malformed provider schema is rejected
  as a safe provider failure.
- Generation previews now render the organizer's edited title/time/place/note
  values immediately and keep the server-side preview available after closing
  the dialog, with a visible “Draft ready to review” affordance before Apply.
- The generation preview editor now opens the displayed original suggestion on
  its first edit (not only suggestions already present in the edited-items map),
  so every preview row's Edit action is functional before Apply.
- Public generation responses fail closed for legacy terminal jobs whose job or
  preview schema version is not the current supported contract.
- The latest verification reran Android shared compilation, unit tests,
  Debug/Release packaging, and the iOS Debug/Release Simulator builds after
  the account-cleanup, polling-window, RevenueCat identity/catalog, and
  generation-error mapping changes; all completed successfully. The unsigned
  Android Release APK still contains no Test Store key.
- The Organizer Pro paywall copy explicitly describes its AI benefit as
  monthly fair-use generation, matching the PRD's requirement not to imply
  unlimited usage; Android and iOS Debug/Release builds passed after this
  shared KMP UI change.
- The Home organizer gate now counts only active trips owned by the signed-in
  user; joined trips remain visible and usable without consuming the Free
  organizer allowance. Profile settings expose an explicit Organizer Pro
  entry point without showing a paywall during first-use onboarding, and a
  regression test covers the owner-only count.
- A fresh Firestore/Auth/Functions emulator run on 2026-09-06 passed the
  server-authoritative active-trip/Pro gates, capacity and membership-counter
  invariants, invite flow, private-read boundaries, AI-attribution spoofing,
  and retained-generation-preview reauthorization regressions.
- The same rules/callable matrix was rerun directly with the repository's ESM
  smoke runner after starting the Auth, Firestore, and regional Functions
  emulators: all owner/member query constraints, direct-write denials, Pro and
  capacity gates, invite and itinerary flows, AI-attribution protection,
  retained-preview suspension/removal denial, inactive-membership counter
  denial, account cleanup, and cleanup idempotency checks passed. The first
  `emulators:exec` wrapper attempt was not a product failure; the bundled CLI
  incorrectly loaded the `.mjs` runner through `require()`, so the equivalent
  `emulators:start` plus direct Node invocation was used.
- The rules audit now also covers malformed and reversed ISO dates, over-capacity
  new trips, out-of-trip itinerary dates, invalid fixed clock labels, and
  unknown flexible time labels on direct trip/itinerary writes; each denial or
  compatibility case passed in the emulator and the corrected rules were
  redeployed and live-verified through Firebase MCP. The matrix also confirms
  that an owner may lower capacity below an unchanged member count without
  evicting members, as required by PRD 02.
- Owner member removal now uses the regional `removeTripMember` callable. The
  rules deny counter-only parent writes and direct owner/member status/delete
  mutations,
  while the callable validates an active profile, owner membership, target
  status, and counter/revision integrity in one Admin transaction. Android and
  iOS adapters call the same boundary, and the emulator matrix covers
  non-owner denial, successful removal, rejoin, and leave.
- iOS Remote Config flag publication now marshals Firebase's completion
  callback onto the main queue before updating the SwiftUI `@Published`
  feature flags. This preserves the fail-closed defaults without background
  thread publication during cold start; the iOS Debug Simulator host build was
  rerun successfully after the change.
- A fresh Xcode Debug Simulator build was installed and launched on the booted
  iPhone 17 (iOS 26.5) simulator after reclaiming stale generated caches. The
  welcome route rendered successfully from the current `com.triptandem`
  bundle. Interactive iOS Test Store purchase, cancellation, and restore still
  require a deliberate simulator/manual-store run; signed App Store
  verification remains an external release gate.
- The Functions suite was rerun after the credential-boundary and schema
  hardening: all 28 tests pass and TypeScript compilation is clean.
- Generation callable path IDs now reject slash-containing document IDs before
  Firestore reference construction, and Apply rejects retained jobs whose
  schema or trip path is malformed, including already-applied states. The
  Apply transaction also revalidates the job schema and trip ID after its
  initial read, preventing a repaired job from being applied to a different
  trip while the preview is open. The emulator matrix covers the nested-ID
  denial.
- Account deletion now validates active member documents in addition to the
  `activeMemberCount` aggregate. A counter that says one but has two active
  members fails closed with `ownership_required`; the emulator matrix covers
  this corrupt-aggregate case while preserving the normal owner-only path.
- The generation worker now fails closed when any active booked item has
  malformed or incomplete scheduling metadata instead of silently omitting it
  from the provider's lock context. Valid booked windows remain coarse and
  server-enforced; no booked title, place, note, or member data is sent.
- After the preview-edit fix, the Android shared compile, unit tests, Debug and
  Release APK packaging, and the iOS Debug and Release Simulator builds all
  pass again.
- After the auth-state handoff hardening, Android `:app:testDebugUnitTest`,
  `:app:compileDebugKotlin`, `:app:assembleDebug`, and the connected emulator
  smoke suite pass again (2 tests, 0 failures) on
  `Medium_Phone_API_36.1`.
- Trip deletion invokes the authenticated regional `deleteTrip` callable. Direct
  trip-root deletes are denied because Firestore parent deletes do not cascade;
  the callable locks and archives the trip, cancels and settles generation jobs,
  drains `members`, `itinerary`, `invites`, and `joinRequests` plus per-trip
  generation locks, removes the root, and is idempotent on retry.
- Generation allowance settlement is marked on each job in the same transaction
  as the usage decrement, so cancellation, worker failure, purge, account
  deletion, and trip deletion cannot release the same reservation twice. Job
  creation rechecks the current editor membership, trip status, and deletion
  lock inside its transaction.
- Account deletion invokes the authenticated regional `deleteAccountProfile`
  boundary before revoking Firebase Auth. Direct profile deletes are denied by
  Firestore Rules; the callable rechecks active shared-trip ownership, removes
  each owned trip root and its known subcollections, requester-owned generation
  jobs (including any jobs on owned trip graphs), per-trip generation locks, and
  `users/{uid}/billing/*`
  entitlement/allowance metadata in bounded, idempotent batches, then deletes
  the profile in a guarded transaction. Both Android and iOS deletion bridges
  invoke it. The emulator smoke test covers direct-delete denial, ownership
  blocking, full trip-graph and server-owned metadata cleanup, profile
  deletion, authentication, and repeat-call/idempotence paths.
- After the account-deletion trip-graph hardening, `functions/npm test` remains
  green (28/28), Android `:app:testDebugUnitTest`, `:app:compileDebugKotlin`,
  and `:app:assembleRelease` pass, and the iOS Debug and Release Simulator
  targets both build successfully with Xcode 26.6. The iOS Release build
  required reclaiming only the project-specific generated DerivedData cache;
  no source or user data was removed.
- Client generation polling now waits up to 120 attempts (about two minutes),
  matching the Functions worker's 90-second provider/allowance timeout. Android
  maps the server's documented coarse generation failure classes to the shared
  `GenerationFailed` errors, keeping retry/cancel/safety messaging consistent
  with iOS.
- `firebase.json` now runs `npm --prefix "$RESOURCE_DIR" run build` as the
  Functions predeploy hook, so a clean checkout compiles the TypeScript source
  before deployment instead of depending on ignored local `functions/lib`
  output; the build and all 28 tests pass after this release-pipeline fix.
- AI privacy documentation now specifies that normalized planning parameters
  and sanitized previews are retained for up to seven days, with cleanup at
  least every 24 hours; provider prompts and raw responses are transient.
- The approved all-disabled Remote Config template was redeployed through
  Firebase MCP as job `1788541626530` (success, 100%); live values for all five
  rollout flags are Boolean `false` and match the checked-in template.
- A RevenueCat MCP catalog audit on 2026-09-05 confirms the active/current
  `triptandem_pro` offering contains monthly and annual packages attached to
  the shared `triptandem_pro` entitlement for the Test Store, Android, and iOS
  catalog entries. The Android and iOS entries use `com.triptandem`; platform
  store credentials are still unconfigured and no webhook integration exists,
  so this evidence does not substitute for signed-store purchase or live
  webhook verification.
- Android CLI verification on `Medium_Phone_API_36.1` exercised that Test Store
  offering from the Profile Organizer Pro entry: monthly (`IDR95,000.00`,
  one-month period) and annual (`IDR475,000.00`, one-year period) packages
  loaded with the configured localized catalog data. The purchase dialog
  exposed the one-week introductory phase; a simulated valid purchase made
  the Organizer Pro card active, Restore reactivated it, and cancelling a
  subsequent simulated purchase showed “Purchase cancelled. Your free plan is
  still available.” This is local Test Store evidence only, not signed-store
  billing or server-webhook verification.

## Remaining production gates

These items are deliberately not hidden behind a “done” label:

1. Upgrade `triptandem` to Firebase Blaze. The finalized Functions deployment
   was attempted through Firebase MCP as job `1788561593210` and failed because
   Artifact Registry requires billing (the predeploy TypeScript build hook ran
   before this gate). Until this is enabled, AI generation, the RevenueCat
   webhook, scheduled job cleanup, and the `deleteAccountProfile` deletion
   boundary cannot run in the live project.
2. Create the server secrets `GEMINI_API_KEY` and
   `REVENUECAT_WEBHOOK_SECRET`, deploy all Functions (including
   `deleteAccountProfile`), and configure the matching RevenueCat webhook URL
   and authorization value. Keep
   `ai_generation_enabled=false` until this and the prompt/data-use review are
   complete.
3. Configure Play Console and App Store Connect credentials/products in
   RevenueCat, replace the debug Test Store keys in release builds, and provide
   a judge trial/promo path for the signed store build (the local Test Store
   trial is already configured). Test purchase, pending, cancellation, restore,
   expiration, refund, and account-transfer behavior on signed devices.
4. Deploy and exercise the server-authoritative `createTrip` and `updateTrip`
   callables in the live project after Blaze billing is enabled. They count
   owner trips in a transaction and check only the verified RevenueCat webhook
   document; direct active-trip creates, capacity expansion, and
   inactive-to-active reactivation are denied by Firestore rules. The member-
   cap invariant is also enforced for every counter-increment transaction
   (including invite acceptance and reactivation).
5. Verify signed Android/iOS sessions, Crashlytics symbolication, Analytics
   DebugView, and cross-platform entitlement convergence. These require the
   owner's device signing and store credentials.
6. Publish the Terms and Privacy URLs used by the paywall before production
   submission; the shared paywall now exposes a platform-appropriate Manage
   subscription action.
7. Add an approved background notification/activity path for meaningful job
   completion, with consent, before promising push delivery in a release build.
   Active job IDs and parameter drafts persist now, and returning to a trip
   resumes foreground polling; Phase 3 owns notification delivery.
8. Reauthorize the RevenueCat MCP connection before the next live catalog or
   webhook audit; its saved OAuth refresh token currently returns `invalid_grant`.
   No RevenueCat configuration was changed by this failed read-only check.

The code is ready for Phase 3 feature work while these rollout gates remain
open; enabling AI or claiming production purchase support before the gates pass
would contradict PRDs 04 and 10.
