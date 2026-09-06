# PRD 02 — Trip Creation and Privacy

Phase: 1  
Priority: P0  
Owner: Product + trips/backend  
Status: Implemented for Phase 1 MVP; release gates tracked in `docs/phase1-status.md`

## Outcome

Let an organizer create a useful, privacy-safe trip in under three minutes and retain explicit control over who can discover or access it.

## User stories

- As an organizer, I can create a trip with destination, dates, expectations, and capacity.
- As an organizer, I can keep a trip private, share it by link, or publish it openly when eligible.
- As a member, I understand who can see each class of trip information.
- As an organizer, I can archive, cancel, or delete a trip without leaving members confused.

## Scope

### P0

- Create/edit destination, dates, title, cover, timezone, currency, pace, budget band, interests, capacity
- Draft and active status
- Private and unlisted visibility
- Privacy preview and visibility explanation
- Archive/cancel/delete operations
- Owner/editor/viewer roles

### P1

- Open visibility, gated by PRDs 06 and 08
- Multi-city destination labels

### Non-goals

- Booking inventory or pricing
- Exact transportation/accommodation import
- Recurring trips
- Public map of member locations

## Creation flow

1. Destination: searchable place result plus editable trip title.
2. Dates: start/end, destination timezone, and “dates flexible” indicator.
3. Style: pace, interests, budget band, and optional short expectation note.
4. Access: Private or Unlisted in Phase 1; capacity defaults to six and ranges from two to twelve.
5. Review: show what members and non-members can see; create trip.

Progress is saved locally after each step. Server drafts are created only after authentication and the first explicit Continue action.

## Functional requirements

1. Start date cannot be after end date; past trips may be imported later but cannot be newly published as open.
2. Trip length is 1–60 days for the hackathon release.
3. Destination timezone is captured independently of device timezone.
4. Currency is an ISO 4217 code and always displayed with its code where symbols are ambiguous.
5. Visibility values are `private`, `unlisted`, and `open`; default is `private`.
6. Changing from private/unlisted to open invokes all publishing checks in PRD 06.
7. Changing from open to private immediately removes discovery results and invalidates pending join requests with a notification.
8. Member-only fields include exact lodging, exact meeting point, private notes, invite tokens, and member roster details.
9. Public data is served from an allowlisted projection, not by hiding fields on the client.
10. Capacity counts approved members including the owner; accepted membership cannot exceed capacity transactionally.
11. An owner cannot leave without transferring ownership or deleting the trip.
12. Canceling preserves a read-only record for current members; deleting removes access and follows retention requirements.
13. Cover imagery must be user-owned/licensed or selected from an approved source; destination color gradients are the safe default.

## State model

`draft → active → completed → archived`

- `draft → deleted`
- `active → cancelled → archived`
- Completed derives from end date but may be corrected by owner.
- Open discovery is allowed only when status is active and dates are in the future.

## Permissions

| Action | Owner | Editor | Viewer | Applicant/public |
|---|---:|---:|---:|---:|
| View member fields | Yes | Yes | Yes | No |
| Edit description/style | Yes | Yes | No | No |
| Change dates/destination | Yes | Yes | No | No |
| Change visibility/capacity | Yes | No | No | No |
| Cancel/delete/transfer | Yes | No | No | No |

## Edge cases

- Date change creates itinerary days only after confirmation and prompts before removing populated days.
- Destination timezone change shows which itinerary times will remain local clock time versus absolute time.
- Capacity lowered below current membership: allow saving but mark full; do not evict members.
- Offline draft creation: save locally; show unsynced state and reconcile once online.
- Concurrent owner/editor update: reject stale revision and show field-level comparison where possible.
- Trip URL opened by unauthorized user: show a neutral inaccessible message without confirming a private trip exists.

## Analytics

- `trip_creation_started(source)`
- `trip_creation_step_completed(step)`
- `trip_created(visibility, length_bucket, capacity_bucket)`
- `trip_visibility_changed(from, to)`
- `trip_cancelled(member_count_bucket)`
- `trip_archived`

Do not capture destination, title, exact dates, note, member identity, or invite link.

## Acceptance criteria

- An authenticated user can create a private trip in under three minutes.
- Date and timezone behavior passes tests around midnight and daylight-saving transitions.
- Anonymous, applicant, member, editor, and owner responses contain only authorized fields.
- Switching an open trip to private removes it from discovery within five seconds.
- Capacity cannot be exceeded by simultaneous approvals.
- Stale edits never silently overwrite a newer revision.
- Cancel/delete dialogs state member impact and require proportional confirmation.
- All states defined in DESIGN.md are implemented on both platforms.

## Dependencies and open decisions

- Place-search provider and terms must be approved in Phase 0.
- Open visibility remains unavailable until PRD 08 passes its launch gate.
- Decide whether editors can change destination/dates after launch based on user testing.
