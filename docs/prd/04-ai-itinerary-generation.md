# PRD 04 — AI Itinerary Generation

Phase: 2  
Priority: P0 for product differentiation  
Owner: Product + AI/backend  
Status: Phase 2 P0 implementation complete; live provider rollout is gated by
Firebase billing, secrets, and the reviewed Remote Config flag. P1
weather/compatibility enrichment is deferred until an approved data boundary
exists.

Trip and account deletion also cancel and settle outstanding generation jobs
before removing their private drafts, with an idempotent per-job reservation
marker to make worker, purge, and deletion races safe.

## Outcome

Help an organizer create a useful itinerary draft quickly while keeping the user in control, protecting private data, and representing uncertainty honestly.

## User stories

- As an organizer, I can generate a draft from destination, dates, budget, interests, and pace.
- As an organizer, I can preview and edit suggestions before they affect the shared plan.
- As a member, I can tell which entries were AI-assisted and which are confirmed.
- As a user with an existing plan, I can fill only an empty day instead of replacing everything.
- As a user facing failure or quota limits, I keep my existing work and receive a useful next step.

## Scope

### P0

- Whole-trip draft for an empty itinerary
- Single-day draft or fill-empty-days mode
- Structured input review
- Server-side generation job with validated structured output
- Preview, select, edit, and apply
- Clear AI attribution and unverified-information notice
- Free allowance and Pro limits

### P1

- Replan around locked/booked items
- Weather-aware regeneration only with a licensed, current data provider
- Compatibility-aware suggestions based on aggregate group preferences

### Non-goals

- Autonomous booking
- Guaranteed opening hours, availability, pricing, accessibility, or safety
- Scraping unlicensed travel content
- Sending member bios or private notes to the model

## Generation flow

1. Entry point explains whether generation affects the whole trip, one day, or empty slots.
2. Parameter sheet pre-fills destination, local dates, pace, interests, budget band, daily start/end preference, accessibility/diet notes supplied specifically for generation, and locked items.
3. User reviews what will be sent and starts generation.
4. Server creates a job; client may leave and return.
5. Preview displays assumptions, warnings, and selectable items separate from the live itinerary.
6. User edits/selects and taps Apply.
7. Server applies selected items in one idempotent transaction and returns the new revision.

## Functional requirements

1. AI credentials live only on the server.
2. Input uses the minimum required data; never include email, member names, biographies, contact details, exact lodging, private notes, or account identifiers.
3. The model must return a versioned schema. Invalid items are rejected or repaired server-side before display.
4. Output item fields are constrained to known itinerary types, dates within the trip, reasonable time/duration ranges, and safe text lengths.
5. The preview is never synchronized to members until the requester applies it.
6. Existing items remain unchanged unless explicitly selected for replanning.
7. Locked/booked items cannot be removed or moved by generation.
8. Duplicate suggestions are detected against title/place/time similarity and flagged.
9. The UI states that details may be outdated and provides a “Check details” affordance rather than claiming verification.
10. Jobs have `queued`, `running`, `succeeded`, `partially_succeeded`, `failed`, `cancelled`, and `expired` states.
11. A retry reuses normalized input and a new job ID; billing/usage is charged only according to the documented success policy.
12. Prompts and raw outputs have a short, documented retention period and are excluded from general application logs.
13. Safety filters block requests intended to facilitate exploitation, evasion, or harm and return a neutral explanation.

## Entitlement and limits

- Free: one successful whole-trip generation or equivalent limited allowance.
- Pro: a clearly stated monthly allowance or fair-use limit; do not advertise “unlimited” unless it is operationally true.
- Viewing, editing, or deleting AI-created items remains free after entitlement expiry.
- Failed or policy-blocked requests do not consume user allowance.

## Failure behavior

- Timeout/server error: preserve parameters and offer Retry or Plan manually.
- Partial output: show valid days, identify missing days, and allow selective apply.
- Offline: disable generation but keep parameter draft locally.
- App backgrounded: notification/activity item only when completion is meaningful and consent exists.
- Concurrent itinerary edit: preview compares against latest revision before Apply and asks the user to resolve collisions.

## Analytics

- `generation_started(scope, entitlement_state, existing_item_bucket)`
- `generation_completed(scope, latency_bucket, result_status, item_count_bucket)`
- `generation_preview_edited`
- `generation_applied(selected_count_bucket, duplicate_warning)`
- `generation_failed(failure_class)`
- `generation_paywall_viewed(trigger)`

Never log prompts, destinations, dates, accessibility/diet text, generated content, or model raw output to product analytics.

## Acceptance criteria

- Generated data cannot write directly to the live itinerary without an explicit Apply action.
- Schema validation rejects dates outside the trip and unsafe field lengths.
- Existing and locked items survive all generation paths.
- Cancel, timeout, invalid output, quota, offline, and partial-result paths preserve user work.
- A user can understand what data will be sent before generation.
- Free allowance and Pro gating behave identically on Android and iOS after restore.
- Applying the same preview twice produces no duplicates.
- AI disclosure and “verify details” messaging are accessible and visible before apply.

## Dependencies and open decisions

- Select the model/provider and document its data-use terms in Phase 0.
- Decide successful-generation allowance based on measured cost, not guesswork.
- Place enrichment must use an authorized API; model-generated place facts alone are treated as unverified.
