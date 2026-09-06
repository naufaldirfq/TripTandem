# Firestore rules audit (Phase 0 + Phase 2)

The Firestore rules are the authorization boundary for the TripTandem data
model. The checked-in source is exercised locally against the Firestore/Auth/
Functions emulators and has been deployed and live-verified through Firebase
MCP. The latest rules/indexes deployment is job `1788636174082` (success); a
rules-only dry run also compiled the source successfully.

## Rules model

- User profiles are owner-only at `/users/{uid}`.
- A trip owner controls `/trips/{tripId}` and its member documents.
- Active members can read a trip and its subcollections; only owners and
  editors can write itinerary items.
- Applicants can create and withdraw their own join requests; only a trip owner
  can approve or decline them.
- Reports are write-only for signed-in users until a trusted moderation service
  is introduced.
- Open-trip discovery is intentionally denied. A public, sanitized projection
  must be designed and reviewed before it is exposed.

Every create and update validator checks the complete post-write document for
required fields, an allow-list of keys, bounded strings/integers, enums, and
timestamp types. Immutable identity and creation fields are protected on
update. Itinerary updates require a monotonic revision increment.

## Attack checks

`scripts/firestore_rules_smoke.mjs` creates two temporary anonymous users and
removes them and their temporary documents in a `finally` block. The latest
Auth/Firestore/Functions emulator run on 2026-09-06 passed:

| Check | Result |
| --- | --- |
| Owner creates and reads profile | Allowed (HTTP 200) |
| Owner deletes profile directly through Firestore | Denied (HTTP 403; callable-only account boundary) |
| Owner deletes a trip root directly through Firestore | Denied (HTTP 403; graph cleanup is callable-only) |
| `deleteTrip` receives a slash-containing document ID | Denied (HTTP 400; callable path validation) |
| Owner updates mutable profile fields without changing consent metadata | Allowed (HTTP 200) |
| Profile age gate rejects an unconfirmed profile | Denied (HTTP 403) |
| Invitee creates an age-confirmed profile | Allowed (HTTP 200) |
| Regional `createTrip` callable creates an active trip plus owner membership transactionally | Allowed (HTTP 200) |
| Owner `trips` collection query constrained by `ownerId` | Allowed (HTTP 200) |
| Unscoped `trips` collection query | Denied (HTTP 403) |
| Active `members` collection-group query constrained by `userId` and `status` | Allowed (HTTP 200) |
| `members` collection-group query without caller `userId` filter | Denied (HTTP 403) |
| Free organizer requests a second active trip through the callable | Denied (HTTP 400; `organizer_pro_required`) |
| Direct client active-trip create or trip/member batch | Denied (HTTP 403) |
| Free organizer reactivates an inactive trip through the callable | Denied (HTTP 400; `organizer_pro_required`) |
| Direct inactive-to-active trip reactivation | Denied (HTTP 403) |
| Free organizer requests capacity above six through the callable | Denied (HTTP 400; `organizer_pro_required`) |
| Direct client trip create with capacity above six | Denied (HTTP 403) |
| Server blocks a counter increment above trip capacity | Denied (HTTP 403) |
| Trip with `open` visibility | Denied (HTTP 403) |
| Second user reads first user’s profile | Denied (HTTP 403) |
| Second user reads first user’s trip | Denied (HTTP 403) |
| Extra trip field | Denied (HTTP 403) |
| Invalid trip capacity type | Denied (HTTP 403) |
| Mixed-type profile interests list | Denied (HTTP 403) |
| Owner creates a trip for another UID | Denied (HTTP 403) |
| Owner changes profile UID | Denied (HTTP 403) |
| Second user updates owner’s trip | Denied (HTTP 403) |
| Owner creates an owner membership | Allowed (HTTP 200) |
| Invitee tries to increment the counter with an inactive membership | Denied (HTTP 403; join transition requires an active membership document) |
| Invitee accepts with atomic counter and invite use | Allowed (HTTP 200) |
| Invitee bundles a trip-detail mutation into the join transaction | Denied (HTTP 403; membership transition may change only its counter/revision) |
| Invite collection enumeration | Denied (HTTP 403) |
| Active member reads the trip | Allowed (HTTP 200) |
| Account deletion while owning an active shared trip | Denied (HTTP 400; `ownership_required`) |
| Viewer changes membership or itinerary | Denied (HTTP 403) |
| Owner promotes/demotes an editor and direct owner removal without count update | First two allowed; status change and direct delete denied (HTTP 403) |
| Owner-only counter mutation without a member mutation | Denied (HTTP 403) |
| Non-owner remove callable | Denied (HTTP 403) |
| Owner remove callable updates member and aggregate atomically | Allowed (HTTP 200) |
| Removed member rejoin and subsequent leave | Allowed (HTTP 200) |
| Owner creates and member reads itinerary | Allowed (HTTP 200) |
| Owner tries to spoof AI attribution on a client-created itinerary item | Denied (HTTP 403) |
| Active editor reads a retained generation preview | Allowed (HTTP 200; current profile and membership revalidated) |
| Suspended requester or removed editor reads a retained generation preview | Denied (HTTP 403; callable reauthorization) |
| Member leaves with atomic counter decrement | Allowed (HTTP 200) |
| Member bundles a trip-detail mutation into the leave transaction | Denied (HTTP 403; membership transition may change only its counter/revision) |
| Left member reads private trip | Denied (HTTP 403) |
| Applicant creates and owner approves join request | Allowed (HTTP 200) |
| Account deletion with a corrupt one-member aggregate and two active member documents | Denied (HTTP 400; `ownership_required`) |
| Cancelled trip itinerary write | Denied (HTTP 403; inactive trips are read-only) |
| `deleteTrip` callable drains nested trip data and is idempotent | Allowed (HTTP 200; root + member + itinerary + generation job/lock removed, reservation released, repeat returns `deleted=false`) |
| Unauthenticated trip read | Denied (HTTP 403) |
| End-user report read | Denied (HTTP 403) |
| Account profile deletion after shared membership is removed | Allowed through `deleteAccountProfile` callable (HTTP 200); profile, all owned trip roots, and known trip subcollections are removed |

Run it from the repository root against local emulators:

```bash
npx firebase-tools@latest emulators:exec --only firestore,auth,functions \
  'node scripts/firestore_rules_smoke.mjs'
```

The latest live fetch is an exact byte-for-byte match with the checked-in
`firestore.rules` (36,345 bytes; SHA-256
`3901ca9f8e03f76cb13b1a27f344c12b7c37171fc4d2f88f58a087dc2804da68`). It also
confirms that profile deletion is callable-only, the server account-deletion
boundary blocks active shared-trip ownership, removes each owned trip root and
known trip subcollections, cancels and settles generation reservations, and
removes the profile only after that check, direct trip-root deletes are denied,
the `deleteTrip` callable owns graph cleanup, AI
attribution fields are immutable after Apply, cannot be
written by a client-created item, active-trip client creates are denied,
capacity expansion and inactive-to-active reactivation are denied, direct
owner/member status changes and member deletes are denied, and every member
counter increment is tied to the appropriate membership transition and bounded
by trip capacity; owner removals use the server transaction. The live
index list
contains the required `members` collection-group composite index on `userId`
and `status`. The emulator regression also denies malformed/reversed ISO trip
dates, over-capacity new trips, out-of-trip itinerary dates, invalid fixed
clock labels, and unknown flexible time labels on direct writes; it preserves
the PRD-approved ability to lower an existing trip's capacity below an
unchanged member count.

These checks are regression coverage, not a complete security review. Before
launch, add emulator-backed tests for every entity and query shape, review
moderation and abuse controls, verify App Check, and repeat the review when the
public discovery projection is implemented.
