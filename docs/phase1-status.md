# Phase 1 implementation status

Updated: 2026-09-03

Phase 1 is the private trip coordination MVP described in `PRODUCT.md` and
PRDs 01, 02, 03, and the private/invite subset of PRD 05. Android and iOS now
share the Compose UI and expose native Firebase-backed adapters for the private
trip flow. Open-trip discovery remains disabled by design.

## Implemented

- Superdesign welcome route with bundled Cabinet Grotesk/Satoshi typography,
  warm cream/coral/sage palette, route-map hero, and profile CTA.
- Anonymous Firebase session bootstrap with profile essentials (display name,
  age gate, region, language) and optional bio, pace, budget, visibility, and
  profile preview.
- Private and unlisted trip creation with title, destination, ISO date range,
  destination timezone, pace, capacity (2–12), and validation. Trips can be
  edited with optimistic revision checks, cancelled/archived, or deleted by the
  owner.
- Day-specific itinerary items with activity/food/transit/lodging/note types,
  destination-local fixed times or explicitly flexible labels, duration, place,
  note, status, visibility, manual ordering, accessible move actions, edit,
  delete confirmation, soft-delete/Undo, overlap warnings, and stale revision
  errors. Fixed times are converted from the trip's destination timezone rather
  than the device timezone.
- Owner/editor/viewer member records plus cryptographically random invite links.
  Only token hashes are stored in Firestore; links support preset/custom
  expiry, can be revoked, and are accepted after authentication. Invite lookup
  uses the token hash as the document id so the invite collection cannot be
  enumerated. Android `triptandem://join/...` links now land on a role/expiry
  review screen before acceptance.
- Firestore allow-listed fields, role checks, private-trip reads, revision
  checks, a server-transactional trip-plus-owner-membership create callable,
  transaction-safe invite capacity/member counters, owner-only role changes
  and ownership transfer, soft-delete/Undo, and fail-closed Remote Config
  flags. Open-trip writes are rejected server-side. Closed trips are
  read-only, including the trusted owner member-removal boundary. The
  checked-in rules pass the emulator regression matrix; the current source was
  deployed and live-verified through Firebase MCP on 2026-09-05.
- Account deletion checks recent authentication and refuses to remove an owner
  who still has an active shared trip, preventing collaborators from being
  stranded. The authenticated server boundary removes the owned trip graph,
  server metadata, and profile atomically across the account workflow. Sign-out
  restores a fresh anonymous session for the local shell; auth and invite cold
  starts retry briefly while Firebase restores a session.
- Loading, empty, offline, permission, not-found, conflict, and recoverable
  error states in the shared UI. Analytics events are coarse and do not contain
  itinerary text, exact dates/locations, member identity, or invite tokens.

## Verification completed

- Android unit tests, `:app:assembleDebug`, and shared Android compilation pass.
- KMP `iosSimulatorArm64` shared framework linking passes with Xcode 26.6.
- Xcode simulator build passes with `CODE_SIGNING_ALLOWED=NO`; the native iOS
  Firebase Auth/Firestore repositories compile and are injected into the
  shared host.
- Firestore/Auth emulator rules smoke fixture covers owner/member reads,
  invitation acceptance, open-trip write blocking, direct active-trip write
  blocking, server callable trip/member create, capacity counters, invite non-enumeration, member leave, role/count
  enforcement, itinerary access (including AI-attribution spoof prevention),
  join-request boundaries, report privacy, and rejection of member removal on a
  cancelled trip.
  Run it with
  `npx firebase-tools@latest emulators:exec --only firestore,auth,functions 'node scripts/firestore_rules_smoke.mjs'`.

## Remaining gates before calling Phase 1 release-ready

These are environment or product-review gates rather than blockers for
continuing implementation:

1. Run the private-trip journey on one signed Android device and one signed iOS
   device, including a cold-start invite link, role enforcement, member leave,
   ownership transfer, and removal.
2. Configure HTTPS universal-link association files and Google/Apple provider
   signing details manually; the custom `triptandem://` scheme is present for
   local Android/iOS testing and Android handles its pending invite payload.
3. Keep open visibility, discovery, join requests, and AI generation disabled
   until their Phase 2/launch safety gates are approved.

The remaining items do not prevent work on Phase 2 UI or monetization, but the
cross-platform verification and safety review must be completed before a public
Phase 1 release.
