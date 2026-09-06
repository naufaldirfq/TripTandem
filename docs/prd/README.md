# TripTandem Feature PRD Index

Each document is independently testable but must be executed in the order established by [PRODUCT.md](../../PRODUCT.md). “P0” means required for the first public release; “P1” means required for the intended Shipaton demonstration but may be feature-flagged if safety or release timing is at risk; “P2” means post-launch enhancement.

| PRD | Feature | Phase | Priority | Depends on |
|---|---|---:|---:|---|
| [01](01-identity-and-traveler-profile.md) | Identity and traveler profile | 1 | P0 | Foundation |
| [02](02-trip-creation-and-privacy.md) | Trip creation and privacy | 1 | P0 | 01 |
| [03](03-itinerary-workspace.md) | Itinerary workspace | 1 | P0 | 02 |
| [04](04-ai-itinerary-generation.md) | AI itinerary generation | 2 | P0 | 03 |
| [05](05-invitations-and-collaboration.md) | Invitations and collaboration | 1 | P0 | 01–03 |
| [06](06-open-trip-discovery.md) | Open-trip publishing and discovery | 3 | P1 | 01–03, 08 |
| [07](07-compatibility-and-join-requests.md) | Compatibility and join requests | 3 | P1 | 01, 02, 06, 08 |
| [08](08-trust-safety-and-moderation.md) | Trust, safety, and moderation | 3 | P0 gate for open trips | 01–02 |
| [09](09-notifications-and-activity.md) | Notifications and activity | 3 | P1 | 05, 07 |
| [10](10-organizer-pro-and-revenuecat.md) | Organizer Pro and RevenueCat | 2 | P0 | 01–04 |
| [11](11-offline-and-export.md) | Offline access and export | 4 | P1/P2 | 02–05 |
| [12](12-growth-analytics-and-release.md) | Growth analytics and release | 0–5 | P0 | All shipped features |

## Definition of ready

A feature may enter implementation when:

- Its dependencies have stable contracts.
- Required backend authorization is specified.
- Default, loading, empty, error, offline, and permission-denied states are designed.
- Analytics names and prohibited payload data are reviewed.
- Open product questions that affect storage or store policy are resolved.

## Definition of done

A feature is done when:

- Acceptance criteria pass on Android and iOS.
- Backend authorization tests cover owner, member, applicant, blocked user, and anonymous access where applicable.
- Accessibility labels, focus order, large text, and reduced-motion behavior pass.
- Analytics are observable without containing itinerary text, messages, exact locations, or personal profile content.
- Product copy is available in English and prepared for localization.
- Empty, loading, error, retry, and offline states are implemented.
- Relevant deletion, retention, and abuse paths work.

## Priority conflict rule

Security, privacy, purchase restoration, and store eligibility outrank feature breadth. Open-trip features must remain disabled if PRD 08 is incomplete.
