# PRD 08 — Trust, Safety, and Moderation

Phase: 3, designed in Phase 0  
Priority: P0 gate for any open-trip feature  
Owner: Trust & safety + backend + product  
Status: Must pass before open publishing

## Outcome

Reduce predictable harm when strangers discover one another through TripTandem, give users immediate control, minimize exposed data, and provide an operable response process.

## Safety principle

TripTandem can reduce risk but cannot certify that a person or trip is safe. Product language, ranking, and UI must never imply a guarantee.

## Threat model

- Harassment, discrimination, grooming, stalking, impersonation
- Scams, off-platform payment requests, trafficking or exploitation
- Exposure of exact lodging, meeting, contact, or home information
- Spam, link/token enumeration, scraping, ban evasion
- Retaliatory or false reports
- Unsafe itinerary suggestions and emergency misinformation
- Account takeover and unauthorized trip access

## Scope

### Required before open trips

- Block user from profile, request, member list, and report confirmation
- Report user, trip, and public content with categories and optional detail
- Immediate concealment rules for blocker/blockee
- Public/private data projections
- Automated text screening and rate limiting
- Admin moderation queue with statuses, audit trail, and access control
- Account suspension and trip unpublish actions
- Published community guidelines and support/escalation path
- Exact-location and contact-detail restrictions
- Data deletion and retention policy

### Post-launch

- Appeals tooling
- Trusted flagger/risk scoring only after fairness and privacy review
- Government-ID verification only after specialist legal/security assessment

## Blocking requirements

1. Blocking takes effect immediately and does not notify the blocked user.
2. Both parties disappear from each other’s discovery, direct public profiles, invitations, and new join requests.
3. If both are already in a trip, the blocker sees a clear choice: leave, ask owner/support to remove the other user, or temporarily hide non-essential content. Safety controls must not silently corrupt shared itinerary access.
4. Block state is enforced server-side on every affected read and mutation.
5. Unblocking requires an explicit profile/settings action and does not restore previous requests or invitations.

## Reporting requirements

Categories:

- Harassment or hate
- Scam or payment request
- Sexual or exploitative content
- Impersonation
- Dangerous activity
- Privacy/location exposure
- Spam
- Underage-safety concern
- Other

Flow:

1. Choose subject and category.
2. Add optional detail; advise against sharing unnecessary sensitive information.
3. Choose whether to block if not already blocked.
4. Submit and receive reference ID plus realistic response language.
5. For imminent danger, show region-appropriate emergency guidance without claiming TripTandem provides emergency services.

## Moderation lifecycle

`received → triaged → investigating → actioned | no_action → appealed → closed`

Possible actions:

- Remove content
- Unpublish trip
- Restrict community actions
- Temporary suspension
- Permanent suspension
- Preserve evidence under controlled retention/legal process
- Escalate to specialist review

Every admin action records actor, timestamp, policy basis, affected entities, and reversible/irreversible status. Moderator access follows least privilege and is audited.

## Privacy-by-design requirements

- Public itinerary contains broad areas and themes, never exact lodging/meeting coordinates.
- Exif metadata is stripped from uploaded images.
- Contact details and external payment handles are prohibited in public trip text and join introductions.
- Direct object identifiers are non-sequential and never substitute for authorization.
- Sensitive content is encrypted in transit and at rest using managed platform controls.
- Logs redact invite tokens, authentication secrets, report narrative, and exact locations.
- Retention durations are documented per entity; “keep forever” is not acceptable.
- Analytics uses coarse event properties and no free text.

## Content controls

- Validate text length and encoding server-side.
- Detect obvious contact details, payment solicitation, URLs/handles, threats, hate, sexual exploitation, and spam.
- Automated detection may hold content for review; it must not silently make irreversible account decisions.
- Users receive neutral, actionable feedback when content cannot be published.
- Moderation vendors and AI processors require documented data-use and retention terms.

## Operational severity

- Severity 1: credible imminent harm, child safety, trafficking/exploitation, exposed exact sensitive location, active account takeover.
- Severity 2: targeted harassment, scam attempt, impersonation, repeated evasion.
- Severity 3: spam, low-risk policy violation, isolated abusive language.

Before launch, assign response owners and targets, document evidence handling, and test escalation. Do not publish response-time promises that cannot be staffed.

## Analytics

- `safety_control_opened(source)`
- `block_completed(context)`
- `report_submitted(category, subject_type)`
- `public_content_held(reason_class)`
- `moderation_actioned(action_class, severity)`

Report text, evidence, subject/reporter identifiers, locations, and moderator notes stay outside general analytics.

## Acceptance criteria / open-trip launch gate

- Backend tests prove block propagation across discovery, profiles, invites, requests, and direct identifiers.
- Public responses contain none of the prohibited private fields.
- Reports persist reliably, provide a reference, and appear in an access-controlled moderation queue.
- A moderator can unpublish a trip and suspend community mutations within five minutes of triage.
- Exact-location and contact-detail checks run on public trip text and introductions.
- Suspended users cannot evade restrictions using stale sessions.
- Account deletion and safety-record retention follow the documented policy.
- Severity-one tabletop tests pass with named owner and escalation path.
- Community guidelines, privacy policy, terms, and support contact are accessible in-app.

If any gate fails, Open visibility, discovery, and join requests remain disabled remotely while private/unlisted coordination continues.

## Dependencies and open decisions

- Obtain jurisdiction-appropriate legal review before scaling stranger matching.
- Decide initial country/age availability based on support capacity and policy obligations.
- Choose whether v1 uses initials-only avatars to reduce image moderation scope.
