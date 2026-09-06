# PRD 05 — Invitations and Collaboration

Phase: 1  
Priority: P0  
Owner: Product + collaboration/backend  
Status: Implemented for Phase 1 MVP; release gates tracked in `docs/phase1-status.md`

## Outcome

Allow an organizer to bring known companions into a trip safely and give each member an appropriate level of control over the shared plan.

## User stories

- As an owner, I can invite someone using a revocable link.
- As an invitee, I can understand which trip and role I am accepting before I join.
- As an owner, I can change roles or remove a member.
- As an editor, I can update the itinerary and see when someone else changed it.
- As a member, I can leave a trip and stop receiving updates.

## Scope

### P0

- Create, share, revoke, and expire invite links
- Invite acceptance after authentication
- Member list and owner/editor/viewer roles
- Role change, removal, leave, and ownership transfer
- Basic last-editor attribution in the itinerary
- Cross-platform deep links

### P1

- In-app invitation to an existing user
- Lightweight itinerary reactions or comments
- Member activity digest

### Non-goals

- Contact-book upload
- General-purpose direct messaging
- Public friend graph
- Invisible presence or read receipts

## Invitation flow

1. Owner selects role, expiry (24 hours, 7 days, or custom up to 30), and max uses.
2. Server creates a cryptographically random token and stores only its hash.
3. Share sheet sends a universal/app link without private itinerary data in its preview.
4. Invitee sees public-safe trip summary, inviter display name, offered role, expiry, and Join/Decline.
5. After authentication, acceptance is revalidated transactionally against expiry, revocation, block state, trip status, capacity, and existing membership.
6. Successful acceptance creates membership and invalidates single-use links.

## Functional requirements

1. Private trips are never discoverable through token enumeration; invite tokens carry sufficient entropy and are rate-limited.
2. Invite links may be revoked individually or all at once.
3. A role is shown before acceptance and cannot exceed the inviter’s grant permission.
4. Only the owner can grant/revoke editor, remove members, transfer ownership, or manage link settings.
5. Editors cannot invite editors in P0; owners may create viewer or editor links.
6. Capacity is checked in the same transaction that creates membership.
7. Existing members opening an invite go directly to the trip with an explanatory message.
8. Removed members lose access immediately and cached private data is cleared on the next app activation or push signal.
9. A member may leave unless they are the sole owner.
10. Ownership transfer requires the recipient to be an active member and to accept the transfer.
11. A blocked relationship invalidates invitations in both directions.
12. Universal links must continue after install/authentication using a short-lived, secure pending-link mechanism.

## Role matrix

| Action | Owner | Editor | Viewer |
|---|---:|---:|---:|
| View member itinerary | Yes | Yes | Yes |
| Add/edit itinerary | Yes | Yes | No |
| Edit trip description/style | Yes | Yes | No |
| Invite viewers/editors | Yes | No | No |
| Publish/open trip | Yes | No | No |
| Approve join request | Yes | No | No |
| Change roles/remove members | Yes | No | No |
| Delete/transfer ownership | Yes | No | No |

## Edge cases

- Expired/revoked/full trip: reveal only safe summary and a precise non-sensitive reason.
- Same link accepted simultaneously at last capacity: exactly one succeeds.
- Owner removes a member who is editing: mutation fails authorization and local draft remains copyable.
- Owner is suspended: trip becomes read-only until ownership is transferred through support/admin policy.
- Link appears in analytics/referrer logs: links must avoid embedded personal data and backend logs must redact token values.
- Invite declined: no membership; inviter sees aggregate/appropriate status only if invitation was directly addressed.

## Analytics

- `invite_created(role, expiry_bucket, max_use_bucket)`
- `invite_share_opened(channel_category)`
- `invite_opened(auth_state)`
- `invite_accepted(role)`
- `invite_failed(reason_class)`
- `member_role_changed(from, to)`
- `member_left`
- `member_removed`
- `ownership_transferred`

Never record invite tokens, recipient contacts, trip IDs, names, or share-sheet content.

## Acceptance criteria

- Invite acceptance works through cold start, install/auth, and warm start on Android and iOS.
- Revoked and expired links cannot create membership through direct API calls.
- Capacity remains correct under simultaneous invite and join-request acceptance.
- Role authorization is enforced server-side for every mutation.
- Removed/left users cannot read new private trip data and local protected cache is cleared.
- A sole owner cannot leave or delete their account without resolving ownership.
- Screen readers announce trip, inviter, offered role, and consequences before acceptance.

## Dependencies and open decisions

- Depends on identity, trip roles, and itinerary revisions.
- Select universal-link domain and configure platform association files in Phase 0.
- Decide whether direct addressed invitations are necessary before launch; secure links are sufficient for P0.
