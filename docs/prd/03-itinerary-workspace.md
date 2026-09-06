# PRD 03 — Itinerary Workspace

Phase: 1  
Priority: P0  
Owner: Product + collaboration  
Status: Implemented for Phase 1 MVP; release gates tracked in `docs/phase1-status.md`

## Outcome

Provide one dependable day-by-day plan that members can understand and update without losing work or confusing suggestions with confirmed plans.

## User stories

- As an organizer, I can add, order, edit, and remove itinerary items.
- As a member, I can see the latest plan in destination-local time.
- As an editor, I can update the plan without silently overwriting someone else.
- As a viewer, I can distinguish confirmed items from ideas.
- As an offline traveler, I can read the most recently synchronized itinerary.

## Scope

### P0

- Day list generated from trip dates
- Item types: activity, food, transit, lodging, note
- Add/edit/delete/reorder item
- Start time, flexible-time flag, duration, place label, note, status, visibility
- Optimistic concurrency, audit metadata, cached read
- Confirmed versus idea state

### P1

- Lightweight comments or reactions on items
- Conflict hints for overlapping times
- Link opening in installed map provider

### Non-goals

- Guaranteed route optimization
- Live transit data
- Reservation booking or ticket storage
- Rich-document editing

## Information architecture

- Trip header: destination, date range, sync/offline state.
- Horizontal day selector with local date and item count.
- Vertical itinerary timeline with current-time marker only during the trip.
- Sticky Add action for editors; viewers see no disabled editing controls.
- Item detail sheet for notes, attribution, and privacy.

## Functional requirements

1. Each trip date has one ordered `ItineraryDay` using the destination timezone.
2. Items without a fixed time appear in an “Any time” group before/after timed items according to manual order.
3. Item status is `idea`, `planned`, `booked`, `completed`, or `cancelled`; “booked” is informational and not verified.
4. Item visibility is `members` by default. A safe summary may be marked `public`; exact lodging and meeting locations can never be public.
5. Editors can reorder via drag and an accessible Move action.
6. Every mutation includes the last known revision. A conflicting write is never silently accepted.
7. Delete is soft for 30 seconds with Undo; after that, the server retains only minimal audit information according to policy.
8. The last editor and edit time are visible in item details without creating a public activity leaderboard.
9. URLs are normalized and opened through safe platform mechanisms; scripts and unsupported schemes are rejected.
10. Notes are plain text, limited to 1,000 characters, and sanitized server-side.
11. Overlapping timed items produce a non-blocking conflict warning.
12. Trip date changes offer to move, retain as unscheduled, or delete affected items.

## Collaboration and synchronization

- Initial v1 may use server push or short polling, but two-device convergence must be deterministic.
- Client mutations receive an idempotency key.
- Ordering uses stable fractional/lexicographic positions or an equivalent collision-safe scheme.
- Offline P0 is read-only. P2 queued writes must display pending state and define conflict resolution before activation.
- A reconnect fetches authoritative revisions before enabling edits.

## Empty and error states

- Empty day: “Nothing planned yet” plus Add and Generate actions according to entitlement.
- Empty trip: explain manual entry and AI draft without forcing either.
- Stale revision: show changed fields and actions to Keep latest or Copy my text.
- Deleted by another editor: dismiss detail and explain what happened.
- Permission removed mid-edit: preserve text locally for copying but do not retry unauthorized writes.
- Offline: show last-updated time and disable mutation with explanation.

## Analytics

- `itinerary_item_created(type, entry_method)`
- `itinerary_item_updated(field_group)`
- `itinerary_item_reordered(method)`
- `itinerary_item_deleted`
- `itinerary_conflict_detected(conflict_type)`
- `itinerary_conflict_resolved(choice)`
- `itinerary_day_viewed(relative_day_bucket)`

Never log title, note, URL, place, exact time, trip ID, or editor ID in general analytics.

## Acceptance criteria

- Owner/editor can complete all item operations on both platforms.
- Viewer and applicant API attempts to mutate return authorization errors.
- Two simultaneous edits surface a conflict and preserve both users’ recoverable text.
- Reordering ten items produces the same order on two devices after synchronization.
- Dates render in destination timezone regardless of device timezone.
- Exact lodging/meeting locations never appear in public projections or notifications.
- Screen readers convey day, time, type, title, status, and position; non-drag reordering works.
- Cached itinerary remains readable after app restart without network and clearly displays freshness.

## Dependencies and open decisions

- Depends on trip date/timezone rules in PRD 02 and roles in PRD 05.
- Decide whether comments are worth Phase 1 scope after invite usability testing.
- Map provider integration is optional; place label and coordinates must remain separable for privacy.
