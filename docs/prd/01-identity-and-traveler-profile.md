# PRD 01 — Identity and Traveler Profile

Phase: 1  
Priority: P0  
Owner: Product + identity/backend  
Status: Implemented for Phase 1 MVP; release gates tracked in `docs/phase1-status.md`

## Outcome

Create the minimum identity needed to own and collaborate on trips, plus a consent-based traveler profile that helps other users judge compatibility without exposing sensitive information.

## User stories

- As a new user, I can create an account and continue to my first trip with minimal friction.
- As a traveler, I can describe my pace, budget, interests, and languages.
- As a traveler, I can control what applicants and open-trip viewers can see.
- As a returning user, I can sign in on another device and recover my trips and Pro entitlement.
- As a user leaving the service, I can delete my account in-app.

## Scope

### P0

- Email magic link or one cross-platform identity provider plus email fallback
- Stable internal user ID, session restore, sign out
- Required profile: display name, age-confirmation gate, home country/region, primary language
- Optional profile: avatar, short bio, additional languages, interests, travel pace, budget band
- Profile visibility preview
- Edit profile and delete account
- Blocked/suspended account states

### P1

- Verified-email indicator
- Profile-completion guidance

### Non-goals

- Government-ID verification
- Public follower/friend graph
- Exact home address, employer, passport, or live location
- Social popularity scores

## Functional requirements

1. A user accepts Terms and Privacy Policy versions before account completion.
2. The service stores date/time of consent and policy version, not a prechecked UI value.
3. Display name is 2–40 Unicode characters and is normalized for abuse checks without destroying the chosen display form.
4. Bio is optional and limited to 240 characters.
5. Interests come from a controlled multi-select vocabulary with an optional “Other” label reviewed for abuse.
6. Travel pace is one of `relaxed`, `balanced`, or `packed`.
7. Budget band is relative (`budget`, `moderate`, `comfort`, `premium`); it is never presented as proof of financial status.
8. Home location is stored no more precisely than city/region; public display defaults to country/region.
9. Email, legal name, birth date, exact location, and account identifiers are never public.
10. Profile editing uses optimistic UI but rolls back with an understandable error when persistence fails.
11. Account deletion requires recent authentication, explains consequences, and handles owned trips according to the cross-feature deletion rules.
12. Suspended users can access support and deletion but cannot create, publish, invite, request, or edit.

## Profile visibility

| Field | Public open-trip viewer | Host reviewing request | Approved member |
|---|---|---|---|
| Display name/avatar | Yes | Yes | Yes |
| Country/region | Optional | Optional | Optional |
| Bio | User controlled | User controlled | User controlled |
| Pace/budget/interests | Summary | Yes | Yes |
| Languages | Summary | Yes | Yes |
| Email/account ID | Never | Never | Never |
| Block/report controls | N/A | Yes | Yes |

The edit screen must include “Preview what others see.”

## States and edge cases

- Magic link opened on another device: allow authentication, then explain that the original device remains signed out.
- Duplicate identity provider account: require a verified linking flow; never merge by display name.
- Deleted user referenced in an itinerary revision: display “Former member,” not personal information.
- Upload failure: retain local crop until retry or cancellation.
- Offline launch with valid cached session: allow cached trip reading but block profile mutation until online.
- Underage entry: enforce the product’s selected minimum-age policy and applicable consent path before community features.

## Data and security

- Separate private account data from public profile projection.
- Avatar uploads use content-type, byte-size, and image-dimension validation plus malware scanning where available.
- Session tokens use platform-secure storage; never log tokens or magic links.
- Rate-limit authentication, profile search, and upload endpoints.
- Account deletion removes authentication credentials and schedules permitted cleanup of profile/media.

## Analytics

- `sign_up_started(method)`
- `sign_up_completed(method)`
- `profile_essentials_completed`
- `profile_optional_completed(field_count_bucket)`
- `profile_preview_opened`
- `account_deletion_started`
- `account_deletion_completed`

Never include email, display name, bio, avatar URL, home region, or interest values in analytics.

## Acceptance criteria

- A new user can authenticate and reach trip creation in under two minutes.
- Session restoration works after process death on Android and iOS.
- A public API response never contains email or internal identity-provider data.
- Visibility preview exactly matches the applicant/host/member projection.
- Changing profile visibility updates new reads within five seconds.
- Account deletion can be completed in-app and leaves no accessible public profile.
- A blocked or suspended user cannot invoke protected mutations through direct API calls.
- Screen reader, large-text, keyboard/focus, loading, error, and offline tests pass.

## Dependencies and open decisions

- Choose the identity provider during Phase 0.
- Define minimum age and guardian-consent handling before open trips launch.
- Decide media moderation provider or restrict avatars to generated initials for v1.
