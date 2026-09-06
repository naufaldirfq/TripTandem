# PRD 12 — Growth Analytics and Release

Phase: 0–5  
Priority: P0  
Owner: Product + engineering + growth  
Status: Begins immediately

## Outcome

Release qualifying Android and iOS applications early enough to learn, produce trustworthy Shipaton evidence, and improve acquisition and activation without collecting sensitive travel data.

## User stories

- As the product team, we can see where the activation and purchase funnels fail.
- As a participant, we can report real growth, revenue, and KMP implementation evidence.
- As a new user, I can invite companions through a clear, privacy-safe growth loop.
- As a judge, I can install, understand, test, and purchase/restore the app as described.

## Scope

### P0

- Privacy-safe event taxonomy from all PRDs
- Crash/performance monitoring
- Install → activation → collaboration → purchase funnel
- Invite/referral attribution at coarse channel level
- Feature flags and remote kill switches for AI/open trips
- Store configuration, metadata, review notes, support, privacy/terms, account deletion
- English two-minute demo and Devpost materials
- KMP code-sharing evidence and public build posts

### P1

- One acquisition experiment
- One paywall/pricing-message experiment
- Retention cohort dashboard

### Non-goals

- Fingerprinting or cross-app tracking
- Buying fake installs/reviews/revenue
- Uploading contact books
- Dark-pattern referral prompts
- Reporting vanity metrics without definitions

## Metric definitions

- `activated_organizer`: creates a trip with at least three itinerary items within 24 hours of signup.
- `activated_joiner`: accepts an invitation or submits a join request and views the relevant trip within 24 hours.
- `collaborative_trip`: two distinct members perform meaningful trip/itinerary actions within seven days.
- `payer`: RevenueCat reports an active paid entitlement, excluding promotional judge access where separable.
- D1/D7 retained: user performs a meaningful trip action one/seven calendar days after first activation; define timezone consistently.
- Revenue: use RevenueCat/store reporting and disclose refunds, trials, and measurement window.

## Functional requirements

1. Analytics schema is allowlisted; free-text fields and exact trip/profile data are rejected at the client wrapper.
2. User-facing privacy disclosure and consent behavior match applicable platform and regional rules.
3. Anonymous/pre-auth events use short-lived installation identifiers and are not merged beyond the stated policy.
4. Feature flags can disable AI generation, open publishing, discovery, join requests, and push independently without an app update.
5. Kill switches fail closed for unsafe community functions and fail open to basic manual/private trip use.
6. Referral links use invite attribution only with consent and never expose inviter personal data in analytics.
7. Crash reports scrub authentication tokens, invite URLs, exact locations, itinerary text, profile text, and report content.
8. Dashboards distinguish Android/iOS, version, acquisition channel category, activation persona, and entitlement state.
9. Experiments assign deterministically, document hypothesis and primary metric, and stop if safety/support metrics worsen.
10. Store listing accurately represents shipped behavior and includes required subscription disclosures.
11. Both applications are publicly accessible in the United States; testing tracks do not count.
12. Judge trial/promo access, support contact, and testing steps are verified from a clean account.

## Build-in-public plan

Publish at least three substantive English posts:

1. Problem and KMP architecture: audience, shared-code plan, why Compose Multiplatform.
2. Mid-build learning: user feedback, safety/scope decision, and RevenueCat integration evidence.
3. Launch/iteration: real metrics, what changed after feedback, and the next experiment.

Do not publish user data, unredacted dashboards, secret identifiers, private store links, or inflated metrics.

## Two-minute demo outline

- 0:00–0:12 — Problem and one-sentence promise.
- 0:12–0:40 — Create a trip and generate/review itinerary.
- 0:40–1:02 — Invite a member and collaborate across Android/iOS.
- 1:02–1:30 — Publish safe open summary, review compatibility, request/approve.
- 1:30–1:47 — Organizer Pro paywall and successful RevenueCat entitlement.
- 1:47–2:00 — KMP shared-code proof, launch traction, and closing value.

If open trips are feature-flagged off, replace that segment with private collaboration and safety architecture; the video must match the released app.

## Release checklist

- Unique app IDs, signing, production endpoints, privacy manifests/forms
- RevenueCat production keys and products for both stores
- Purchase/restore/cancel/pending/expiry tested
- Account deletion and support path tested
- Universal links tested after fresh install
- Accessibility, localization, dark mode, low-memory, offline, timezone tests
- 1024×1024 icon and required no-frame screenshot
- Store descriptions and screenshots match production
- No demo credentials, debug menu, secret keys, or development data
- Public links tested signed out and from US availability configuration
- Devpost draft contains description, icon, screenshot, video, store URLs, RevenueCat project ID, category explanations, KMP devlogs, and judge access

## Analytics

This PRD owns schema governance rather than additional events. Every event must have:

- Owner and purpose
- Trigger definition
- Allowed properties and retention
- Platform parity test
- Dashboard/query consuming it
- Deletion/consent behavior

Unused events are removed rather than collected “just in case.”

## Acceptance criteria

- Funnel counts can be reconciled from event definitions without sensitive payload inspection.
- Crash reports and analytics samples contain none of the prohibited data.
- Remote flags disable each high-risk feature in a production-like build.
- Both public store URLs, trial/promo access, and purchase restoration pass from clean accounts.
- Three public build artifacts and an honest KMP sharing explanation are ready.
- Demo is public/unlisted-viewable, in English, under two minutes, and matches the released app.
- Reported download, active-user, conversion, and revenue values match their named source and time window.

## Dependencies and open decisions

- Choose analytics and crash providers based on privacy, KMP support, and implementation time.
- Confirm platform privacy declarations before first submission, not after analytics integration.
- Set a submission buffer and owner for every store/Devpost artifact.
