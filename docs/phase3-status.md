# TripTandem Phase 3 status

Phase 3 implements the community, compatibility, trust/safety, and activity
work described by PRDs 06–09. The implementation is intentionally rollout
gated: the checked-in Remote Config defaults for publishing, discovery, join
requests, and push are all `false`, and server callables also require an
approved, expiring `communityConfig/readiness` record. Private and unlisted
trip coordination continues to work while the community gate is closed.

## Implemented

- Owners can build an allowlisted public preview from a future, active, open
  trip with a completed profile, open capacity, normalized destination, dates,
  pace, budget, languages, interests, and expectation text. Public projections
  contain initials and broad planning data only; exact lodging, meeting points,
  contact details, invite tokens, member identities, and private itinerary
  fields are excluded.
- Publishing enters a moderation hold. A moderator with the
  `communityModerator` custom claim can inspect the queue, publish a reviewed
  projection, unpublish a listing, suspend an account, and move reports through
  the audited lifecycle. Owners can close discovery and submit a fresh reviewed
  preview to reopen it.
- Discovery applies destination/date/pace/budget/language/interest/spot
  filters, deterministic cursor pagination, stable ordering, and an
  evidence-based compatibility explanation. Empty results offer private-trip
  creation and invitation paths.
- Join requests use one server-owned document per applicant/trip. The server
  validates profile sharing, safety acknowledgement, screening, rate limits,
  expiry, bilateral blocks, current capacity, suspension, listing state, and
  membership count in the approval transaction. Applicant withdrawal and owner
  approval/decline are lifecycle operations; stale requests cannot approve.
- Blocking is bilateral, immediate, and server-enforced across projections,
  request approval, and invitation redemption. Unblocking uses a tombstone so
  previous requests and invitations are not restored. Reports are idempotent,
  referenceable, retained with restricted audit records, and inaccessible to
  end-user reads.
- Activity is a durable, paginated in-app record. Source-triggered activity
  writes are idempotent and itinerary mutations are grouped into one daily
  digest. Optional push uses generic lock-screen text, category preferences,
  quiet hours, installation/token ownership, and an authenticated activity
  deep link. Sign-out and account deletion remove installation data and token
  ownership.
- Android and iOS share the community callable contract and screens. The UI
  follows the cream/coral/sage system, uses public-safe copy, explicit status
  words, accessible controls, empty/error/offline states, and an actionable
  activity badge.

## Verification

- `npm --prefix functions test`: 35 passing tests and one emulator-only test
  skipped without the emulator.
- Community emulator suite: 7 passing tests covering public projection
  privacy, last-slot approval races, duplicate requests, block propagation,
  moderation states, report idempotency, and rollout denial.
- Firestore rules dry-run compiles successfully; the complete private-trip
  authorization/deletion smoke matrix passes.
- Android `:app:compileDebugKotlin`, unit tests, and debug APK packaging pass.
- Android connected instrumentation passes on `Medium_Phone_API_36.1`: 9/9
  tests, including the three Phase 3 community cases for rollout gating,
  discovery-to-join consent, and activity read state.
- iOS Debug Simulator build passes using the cached Firebase package checkout.
- `scripts/check_analytics_parity.mjs` reports 73 shared events and 55 aligned
  parameter schemas across Android and iOS.

## Required rollout gates

The feature is code-complete but not production-enabled until the operator:

1. upgrades Firebase to Blaze and deploys the regional Functions, including
   the moderation, activity, scheduled retention, and push triggers;
2. creates the community readiness record only after a staffed moderation queue,
   response owner, escalation path, legal/community-guideline review, and a
   support URL are approved;
3. configures App Check, Play Integrity, App Attest, APNs, and signed-device
   delivery tests; and
4. verifies the retention, account-deletion, push opt-out, stale-link, and
   cross-platform activity flows before changing any Remote Config flag to
   `true`.
