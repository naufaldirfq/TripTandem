# PRD 06 — Open-Trip Publishing and Discovery

Phase: 3  
Priority: P1; must be feature-flagged until safety gate passes  
Owner: Product + discovery/backend  
Status: Conditional on PRD 08

## Outcome

Allow eligible organizers to publish limited trip information and help travelers find relevant trips without turning the product into an unsafe, engagement-driven social feed.

## User stories

- As an owner, I can preview and publish a trip with open spots.
- As a traveler, I can search by destination/date and filter by practical compatibility criteria.
- As a traveler, I can inspect a public-safe trip page before requesting to join.
- As an owner, I can close discovery instantly without deleting the member trip.

## Scope

### P1

- Open-publishing eligibility checklist and public preview
- Search by destination and overlapping date range
- Filters: budget band, pace, interests, language, available spots
- Relevance sorting with transparent reasons
- Public trip detail
- Save locally/recently viewed only if privacy review permits
- Close/reopen discovery

### P2

- Saved search alerts
- Geographic radius search
- Curated destination collections

### Non-goals

- Infinite algorithmic content feed
- Pay-to-rank listings
- Public member roster or exact meeting/lodging location
- Scraped or fabricated marketplace inventory
- Booking/deposit collection

## Publishing eligibility

An open trip must:

- Belong to an active, non-suspended owner with completed profile essentials.
- Start in the future and have at least one open capacity slot.
- Include destination region, date range, pace, budget band, capacity, language, and expectation note.
- Pass automated content checks and any risk-based review.
- Contain no contact details, exact accommodation, exact meeting location, payment request, or external messaging handle in public text.
- Acknowledge host responsibilities and community rules.

## Public projection

Allowed:

- Trip title, broad destination, dates/flexibility, cover gradient
- Host public profile projection
- Pace, budget band, interests, languages
- Capacity and remaining spots
- Public-safe high-level itinerary summary
- Response-time bucket after sufficient data exists

Never public:

- Exact accommodation or meeting point
- Private itinerary notes and URLs
- Invite tokens
- Applicant/member identity or contact information
- Internal moderation/risk signals
- Precise host home location

## Functional requirements

1. Owner reviews the exact public projection before publishing.
2. Search uses normalized destination identifiers while displaying human-readable labels.
3. Date matching is based on overlap and clearly distinguishes exact versus partial overlap.
4. Default sorting balances date/destination relevance, practical fit, freshness, and availability; it must not infer protected traits.
5. Every result includes plain-language relevance reasons.
6. Search supports pagination/cursors and stable deduplication.
7. Full, cancelled, started, expired, moderated, or closed trips disappear promptly.
8. Closing discovery does not remove approved members; pending requests are handled according to the owner’s explicit choice.
9. A block removes both parties’ trips/profiles from each other’s discovery and direct access.
10. Exact user query and location history are not retained beyond documented operational needs.
11. Empty results provide actions to adjust filters, create a private trip, or invite friends—never fake results.
12. Public pages are authenticated in v1 to reduce scraping and provide block/report controls.

## Discovery ranking v1

Apply hard filters first, then a deterministic weighted score:

- Destination match: required
- Date overlap: 35%
- Pace match: 20%
- Budget-band proximity: 20%
- Interest overlap: 15%
- Language overlap: 10%

Weights are product defaults, not claims of human compatibility. Display reasons, not the formula. Never rank using gender, ethnicity, religion, disability, wealth inference, attractiveness, or popularity.

## Edge cases

- Marketplace has no supply: show private trip creation and invite path.
- Owner changes destination/dates after publishing: temporarily unpublish until the new public preview is confirmed.
- Trip becomes full during browsing: detail remains viewable briefly but request action becomes unavailable.
- Moderation hold: owner sees neutral review status; public listing is removed.
- Unsupported destination: allow broad text label for private trips but require a normalized destination for public publishing.
- Search query contains abuse/contact info: do not persist or reflect unsafe raw strings.

## Analytics

- `open_publish_started`
- `open_publish_completed(capacity_bucket, trip_length_bucket)`
- `discover_search_performed(result_count_bucket, filter_count)`
- `discover_result_opened(rank_bucket, reason_count)`
- `discover_empty_viewed(filter_count)`
- `open_trip_closed(reason_class)`

Do not log destination text, date values, profile attributes, exact ranking score, or stable trip/user IDs to general analytics.

## Acceptance criteria

- Public API tests prove every non-allowlisted field is absent.
- Owner sees an exact public preview before first publication and after material changes.
- Closed/full/cancelled/moderated trips leave search within five seconds.
- Blocked users cannot retrieve each other’s listing through search or direct identifier.
- Filtering and pagination return stable, deduplicated results.
- Every result offers at least one understandable relevance reason.
- Empty results never contain unlabeled sample/fabricated trips.
- Open publishing cannot be enabled while PRD 08’s severity-one requirements fail.

## Dependencies and open decisions

- Requires moderation operations, reporting, blocking, and privacy projections from PRD 08.
- Validate whether all open-trip viewers must authenticate after observing marketplace conversion.
- Saved trips/searches are post-launch unless retention and block propagation are implemented.
