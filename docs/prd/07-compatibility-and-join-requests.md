# PRD 07 — Compatibility and Join Requests

Phase: 3  
Priority: P1  
Owner: Product + matching/community  
Status: Conditional on PRDs 06 and 08

## Outcome

Help a traveler decide whether a trip fits and let an owner approve membership deliberately, with sufficient context and without presenting a compatibility score as a guarantee of safety.

## User stories

- As a traveler, I can understand why a trip may fit my dates, pace, budget, interests, and language.
- As a traveler, I can introduce myself and request to join.
- As an applicant, I can withdraw a pending request.
- As an owner, I can review one request at a time and approve, decline, report, or block.
- As an approved traveler, I receive member access without needing a second invitation.

## Scope

### P1

- Evidence-based compatibility summary
- One pending request per applicant/trip
- Optional 20–300 character introduction
- Owner inbox with approve/decline/report/block
- Withdraw and request expiration
- Capacity-safe approval transaction
- Request status visible to applicant

### P2

- Structured pre-trip questions
- Waitlist
- Group-level compatibility simulation

### Non-goals

- Safety or personality certification
- Dating-style swiping
- Applicant auctions or paid priority
- Automated approval/decline
- Protected-trait filtering

## Compatibility model v1

Use explicit user inputs only:

- Date overlap
- Travel pace
- Budget band proximity
- Interest overlap
- Shared language

Results use labels such as Strong, Mixed, or Limited fit, followed by reasons. If profile information is missing, say “Not enough information” instead of lowering the person’s score. Compatibility never incorporates reports, private moderation signals, gender, age beyond eligibility, nationality, ethnicity, religion, disability, inferred income, or popularity.

## Request lifecycle

`pending → approved | declined | withdrawn | expired | invalidated`

- Only the applicant may withdraw.
- Only the owner may approve or decline.
- System expiration default: seven days or trip start, whichever is sooner.
- `invalidated` covers trip closure, cancellation, full capacity without waitlist, block, or moderation.
- A declined applicant may retry only after a cooldown and material trip/profile change; v1 default is no retry for that trip.

## Functional requirements

1. Request confirmation shows the public profile fields and introduction the host will receive.
2. Applicant must acknowledge that compatibility is informational and follow safe-meeting guidance.
3. Introduction text is sanitized and checked for contact details, payment requests, external handles, and abuse.
4. An applicant may have only one active request per trip.
5. Owners cannot approve their own alternate account through client-side assumptions; backend enforces identity and abuse controls.
6. Approval transaction checks trip status, open visibility, capacity, blocks, suspension, and current request status, then creates membership atomically.
7. Approval exposes member-only data only after transaction success.
8. Decline does not require a reason. Optional broad feedback categories are private and non-accusatory.
9. Request lists do not sort by appearance or popularity. Default is oldest first with risk holds separated for moderation.
10. The UI never says “safe match,” “verified compatible,” or equivalent guarantee.
11. Rate limits apply per account, device risk signal, and trip; exact abuse thresholds remain server-side.
12. Applicant and owner can block/report at any point, immediately restricting access.

## Edge cases

- Last spot approved concurrently: one request succeeds; others stay pending or invalidate according to owner choice.
- Applicant edits profile while pending: owner sees current profile plus compatibility snapshot time; material changes recalculate fit.
- Owner edits trip criteria: pending requests show updated fit and applicant receives a material-change notice.
- Trip starts before decision: request expires and public/member-only details remain inaccessible.
- Owner suspended: approvals freeze and applicants receive a neutral unavailable status.
- Notification arrives after withdrawal: opening it resolves to current authoritative state.

## Analytics

- `compatibility_viewed(label, reason_count, missing_field_count)`
- `join_request_started`
- `join_request_submitted(intro_length_bucket)`
- `join_request_withdrawn(age_bucket)`
- `join_request_reviewed(decision, age_bucket, capacity_bucket)`
- `join_request_invalidated(reason_class)`

Never log introduction text, profile values, exact score, destination, applicant/owner identifiers, or decline notes.

## Acceptance criteria

- Compatibility always includes textual evidence and handles missing data without penalizing it.
- Direct API tests prevent duplicate active requests and self-approval.
- Simultaneous approvals never exceed trip capacity.
- Withdrawing, declining, expiring, blocking, and moderation immediately prevent approval.
- Applicant sees exactly which profile fields and text will be shared before submission.
- Member-only data is inaccessible until approval transaction commits.
- No protected trait appears in ranking, filtering, analytics, or compatibility inputs.
- All lifecycle states and stale-notification paths display correctly on both platforms.

## Dependencies and open decisions

- Requires profile visibility, open public projection, block/report, and notifications.
- Validate the no-retry-after-decline policy through interviews.
- Do not add numeric compatibility percentages until users demonstrate they understand the limitations.
