# TripTandem Product Requirements

Status: Draft for implementation  
Owner: Product/engineering  
Target platforms: Android and iOS via Kotlin Multiplatform and Compose Multiplatform  
Target release: Shipaton 2026 submission window

## 1. Product definition

TripTandem helps someone turn a trip idea into a compatible travel group and a shared plan. A user can create a private or open trip, build or generate an itinerary, invite known companions, accept requests from compatible travelers, and coordinate the itinerary together.

Positioning:

> Plan the trip. Find your people. Go together.

The differentiator is not itinerary generation alone. The itinerary becomes a compatibility signal: dates, budget, pace, interests, and expectations help people decide whether they should travel together.

## 2. Problem

Group travel is fragmented across itinerary tools, spreadsheets, messaging apps, and travel-buddy communities. Existing companions struggle to maintain one current plan. Solo travelers who want company must assess strangers with too little context. Trip organizers carry most of the coordination work but receive no purpose-built workflow for screening members and keeping the plan synchronized.

## 3. Target users

### Primary: Independent organizer

- Plans leisure trips for two to eight people.
- Wants one source of truth and less repetitive coordination.
- May invite friends or open a small number of spots to new people.
- Is the most likely Pro purchaser.

### Secondary: Compatible joiner

- Has a destination or date range in mind but no group.
- Wants transparent expectations before requesting to join.
- Values safety, host responsiveness, budget fit, and travel-style fit.

### Tertiary: Invited member

- Was invited by an organizer.
- Needs to view the plan, vote or comment, and receive changes without learning a complex tool.

## 4. Jobs to be done

- When I have a trip idea, help me turn it into a realistic shared plan quickly.
- When my group discusses alternatives, keep decisions and the latest itinerary in one place.
- When I have open spots, help me meet travelers whose expectations fit the trip.
- When I consider joining strangers, give me enough context and control to make a safer decision.
- When plans change, tell the right people without creating notification noise.

## 5. Product principles

1. Plan before socializing: useful even with one user and no marketplace liquidity.
2. Compatibility over popularity: explain fit; do not optimize for follower counts.
3. Consent before connection: joining always requires a request and host approval.
4. Safety is never premium: blocking, reporting, privacy, and approval controls stay free.
5. Organizer-funded collaboration: members can join and collaborate without paying.
6. One calm source of truth: important trip state must not disappear into chat.

## 6. Scope and non-goals

### In scope

- Account and traveler profile
- Private, unlisted, and open trips
- Manual and AI-assisted itineraries
- Invitation links and member roles
- Open-trip search, compatibility, and join requests
- Essential activity notifications
- Reporting, blocking, and privacy controls
- Organizer Pro purchase through RevenueCat
- Android and iOS store releases

### Explicitly out of scope for the hackathon release

- Booking flights, lodging, or activities
- Holding deposits or transferring money between travelers
- Identity-document collection or formal background checks
- Public follower feeds, likes, and popularity rankings
- Full expense splitting
- Live location tracking
- End-to-end encrypted general-purpose chat
- Desktop or web clients

## 7. Success measures

### North-star event

`coordinated_trip`: a trip with at least two members and at least three itinerary items, where two distinct members perform a meaningful action within seven days.

### Activation funnel

1. Account created
2. Profile essentials completed
3. First trip created or joined
4. Three itinerary items created/generated
5. First collaborator invited or first join request sent
6. Collaborator accepts or request is approved

### Launch targets

- At least 60% of new organizers create a trip.
- At least 40% of created trips reach three itinerary items.
- At least 25% of organizers invite someone or publish an open trip.
- At least 30% of accepted invitees perform one collaborative action.
- Crash-free sessions at or above 99.5%.
- Purchase and restore flows succeed on both platforms in production.
- Zero unresolved severity-one privacy or authorization defects.

Metrics are targets for learning, not claims for the submission. Report actual results transparently.

## 8. Phased execution plan

Dates are aggressive because two public store releases are required. Reduce scope before moving a release date.

### Phase 0 — Foundation and validation (September 3–5)

Deliver:

- Confirm primary audience through at least five short interviews.
- Create Compose Multiplatform project and both store identifiers.
- Establish shared navigation, theme, networking, persistence, and error model.
- Create backend environments and row-level authorization rules.
- Register App Store, Play, and RevenueCat products early.
- Define analytics taxonomy and privacy disclosures.

Exit criteria:

- Android and iOS shells run on physical or store-equivalent devices.
- Authenticated test user can read only their authorized backend data.
- RevenueCat Test Store purchase changes a shared entitlement state.
- The top three interview findings and resulting scope decisions are recorded.

Implementation status (September 3): the Android/KMP shell, `com.triptandem`
identifiers, Firebase Auth providers, Analytics boundary, Firestore Native
database in `asia-southeast2`, strict prototype rules, emulator-backed rules
regression matrix, shared profile/trip repository contracts, RevenueCat Test
Store catalog + KMP SDK boundary, and the XcodeGen-managed iOS host are in
place. The iOS host links the shared framework and Firebase products,
initializes Firebase/Auth, forwards shared analytics, packages Compose
resources, configures the RevenueCat iOS boundary, and now injects native
Firebase profile/trip/itinerary/member/invite adapters. Crashlytics, Performance
Monitoring, and fail-closed Remote Config scaffolding are implemented, but still
require signed-device verification and review before production use. The phase
remains open for environment, release, and human-input gates: Play App Signing
credentials and device signing, verifying DebugView and crash symbolication on
connected devices, recording interview findings, reconciling a privacy-reviewed
dashboard, and approving the privacy disclosure. The reviewed Firestore rules
source is deployed and live-verified. A local release/upload certificate is registered; the
separate Play App Signing fingerprint must be added after the Play app is
created. The initial all-disabled Remote Config template is deployed; future
changes remain review-gated. See
[`docs/firebase.md`](docs/firebase.md), [`docs/revenuecat.md`](docs/revenuecat.md),
[`docs/release-checklist.md`](docs/release-checklist.md), and
[`docs/firebase-rules-audit.md`](docs/firebase-rules-audit.md),
[`docs/observability.md`](docs/observability.md), and
[`docs/remote-config.md`](docs/remote-config.md).

### Phase 1 — Private trip coordination MVP (September 6–10)

Implement PRDs 01, 02, 03, and the private/invite subset of PRD 05.

Implementation status (September 3): the shared Compose route, Superdesign
welcome/profile flow, private/unlisted trip creation and editing, local draft
restore, day-specific manual itinerary with destination-local fixed/flexible
times, member roles, secure hashed invite links, native Android/iOS Firebase
adapters, transactional invite/member changes, soft-delete/Undo, account
deletion ownership/recent-auth guards, and loading/error/offline states are
implemented. Android builds, shared iOS framework linking, the Xcode simulator
host build, and the Auth/Firestore emulator regression matrix pass. The
remaining release gates are signed two-device convergence and the manual
Apple/Google signing and universal-link setup. See
[`docs/phase1-status.md`](docs/phase1-status.md).

Deliver:

- Account/profile essentials
- Create and edit a private trip
- Manual day-by-day itinerary
- Invite link, member list, and owner/editor/viewer authorization
- Loading, empty, offline, and recoverable error states

Exit criteria:

- Two real devices on different platforms can join the same trip and observe consistent edits.
- Unauthorized users cannot retrieve private trip data.
- A complete private-trip journey can be demonstrated in under 60 seconds.

### Phase 2 — Intelligence and monetization (September 11–13)

Implement PRDs 04 and 10.

Implementation status (September 6): the server-side generation job, structured
validation, privacy-safe prompt boundary, preview/edit/select/apply flow with a
visible Check details affordance, persisted active-job resume, usage
reservations, cancellation/expiry/idempotency, AI attribution, shared
RevenueCat paywall, purchase/restore states, entitlement refresh, and signed
RevenueCat webhook handler are implemented across Android, iOS, and KMP. The
shared UI and `createTrip`/`updateTrip` callables enforce the free one-active-
trip and six-traveler limits; the callables count owner trips transactionally
and accept a second active trip or a capacity above six only from verified
RevenueCat webhook state. Direct active-trip creates, capacity expansion, and
inactive-to-active reactivation through Firestore are denied. The Firestore
  rules and indexes (including the collection-group membership index) are
  deployed and live-verified (the rules reserve AI attribution for trusted
  Apply writes). The server re-checks the reviewed
Remote Config AI default at job creation and worker execution, and the
seven-day generation-job retention/cleanup policy is documented.
The
phase is rollout-gated rather than production-ready: Firebase Functions could
not be deployed because the `triptandem` project is not on Blaze, so the Gemini
and webhook secrets/URL are not live and `ai_generation_enabled` remains false.
The UI still contains the free-plan path. See
[`docs/phase2-status.md`](docs/phase2-status.md) for the PRD review, test
evidence, and remaining manual gates.

Deliver:

- Structured AI itinerary generation with preview-before-save
- Usage limits and graceful AI failure handling
- Organizer Pro paywall, purchase, restore, and entitlement state
- Analytics for generation and purchase funnel

Exit criteria:

- AI output is validated before storage and can never silently overwrite an itinerary.
- Sandbox purchases, cancellations, and restore work on Android and iOS.
- Free users retain a useful end-to-end trip experience.

### Phase 3 — Open-trip community (September 14–17)

Implement PRDs 06, 07, 08, and 09.

Deliver:

- Publish an eligible trip as open
- Browse/filter open trips
- Compatibility explanation and join-request lifecycle
- Block, report, moderation queue, and sensitive-location protections
- Essential join and itinerary notifications

Exit criteria:

- A blocked user cannot discover, request, or contact the blocker.
- Only approved members receive private itinerary details.
- All reports are persisted with an auditable status.
- Empty marketplace states remain useful via private-trip creation and invitation.

### Phase 4 — Store readiness and first release (September 18–22)

Implement required portions of PRDs 11 and 12.

Deliver:

- Accessibility, localization readiness, performance, and crash fixes
- Offline read cache and shareable itinerary summary
- Privacy policy, terms, support path, account deletion
- Store metadata, screenshots, review notes, and judge access
- Production telemetry with no sensitive itinerary content

Exit criteria:

- Both store builds are submitted; first submission should occur earlier if possible.
- Release checklist passes on an Android device and an iPhone.
- The app is available in the United States.
- No test credentials, development endpoints, or secret keys are shipped.

### Phase 5 — Launch, learn, and submit (September 23–30)

Deliver:

- Release both applications and ship only low-risk corrective updates.
- Run one acquisition experiment and one pricing/paywall experiment.
- Publish build-in-public posts showing concrete learning.
- Record a two-minute-or-shorter demo.
- Submit verified store URLs, metrics, KMP explanation, icon, screenshot, and judge access.

Exit criteria:

- Both public listings and purchase flows work from the United States.
- All Devpost links work in a signed-out browser.
- Reported metrics match source dashboards.
- Final submission is complete before the deadline buffer.

## 9. Cross-feature system rules

### Roles

- Owner: controls visibility, membership, roles, deletion, and billing-dependent organizer features.
- Editor: edits itinerary and trip details but cannot change ownership, billing, or safety settings.
- Viewer: sees member-visible trip data and can participate in lightweight collaboration.
- Applicant: sees only public trip details and their own request.

Authorization is enforced by the backend, never only by hidden UI.

### Visibility

- Private: discoverable only by members; invites allowed.
- Unlisted: accessible through a revocable link; not searchable.
- Open: searchable; exact lodging and meeting details remain members-only.

### Deletion

- Account deletion is self-service.
- Deleting an account anonymizes or deletes personal profile data while preserving only legally or operationally necessary audit records.
- A trip owner must transfer ownership or delete an active shared trip before account deletion.

### AI

- AI suggestions are drafts, not verified travel advice.
- Show assumptions and allow review before saving.
- Do not send private member biographies, contact information, or exact lodging addresses to the model.

### Monetization

- Safety controls, joining, invitation acceptance, and basic collaboration remain free.
- TripTandem does not process real-world travel payments in this release.
- Product access is derived from RevenueCat entitlement state; the backend never trusts a client-supplied `isPro` flag.

## 10. Shared technical model

Core entities:

- `User(id, status, createdAt)`
- `TravelerProfile(userId, displayName, avatarUrl, bio, homeRegion, languages, interests, pace, budgetBand, visibility)`
- `Trip(id, ownerId, title, destination, startDate, endDate, visibility, capacity, status, currency, budgetBand, pace)`
- `TripMember(tripId, userId, role, status, joinedAt)`
- `ItineraryDay(id, tripId, date, position)`
- `ItineraryItem(id, dayId, type, title, startTime, duration, place, note, visibility, position, revision)`
- `Invite(id, tripId, tokenHash, role, expiresAt, maxUses, revokedAt)`
- `GenerationJob(id, tripId, requesterId, inputHash, status, output, failureCode)`
- `JoinRequest(id, tripId, applicantId, message, status, compatibilitySnapshot, createdAt)`
- `Block(blockerId, blockedId, createdAt)`
- `Report(id, reporterId, subjectType, subjectId, category, detail, status, createdAt)`
- `Notification(id, userId, type, entityId, readAt, createdAt)`
- `EntitlementSnapshot(userId, entitlementId, active, source, expiresAt, observedAt)`

All mutable shared records require server timestamps, actor identifiers, and optimistic-concurrency revisions.

## 11. Release decision rules

- If cross-platform collaboration is unstable by September 10, release read-only collaboration first and defer concurrent editing polish.
- If AI reliability is poor, ship template-based generation under the same preview workflow.
- If moderation cannot be operated safely, release private and unlisted trips but keep open publishing behind a feature flag.
- If one store review is delayed, continue resolving it; do not claim eligibility until both public links are live for the Kotlin category.
- Never trade authorization, deletion, reporting, or purchase restoration for visual polish.

## 12. Linked feature PRDs

See [the PRD index](docs/prd/README.md) for ownership, dependencies, detailed requirements, analytics, and acceptance criteria for every feature.
