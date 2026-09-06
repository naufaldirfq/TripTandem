# Remote Config rollout controls

TripTandem uses Firebase Remote Config as a rollout boundary for capabilities
that can affect safety, cost, or notification consent. It is a product switch,
not an authorization boundary: Firestore rules and server-side validation must
continue to enforce access even when a flag is enabled.

## Current status

- `remote_config.json` is checked in and referenced by `firebase.json`.
- All five parameters default to `false` in the template and in both clients.
- The Android client fetches and activates the template through
  `RemoteConfigRuntime`; the resulting flags are passed to the shared UI.
- The iOS host configures Firebase Remote Config, installs the same safe
  defaults, fetches the template, and passes the resulting flags into the
  shared UI. A failed fetch leaves all flags disabled, and only an explicit
  `true` value can enable a capability; malformed values fail closed.
- The Functions AI create-job callable and its worker re-read the reviewed
  template's `ai_generation_enabled` default with a short instance-local
  cache. Missing, conditional-only, malformed, or unavailable values fail
  closed, so a forged client flag cannot start provider work.
- `scripts/check_remote_config_parity.mjs` verifies that the five template
  parameters, Android XML defaults, shared flag constants, and iOS/Android
  readers remain aligned in CI.
- The approved cloud template is deployed as version `2` in project
  `triptandem`, with all five values set to Boolean `false` (Firebase MCP job
  `1788541626530`). The live values were fetched after deployment and match
  the checked-in defaults.

## Parameters

| Parameter | Initial value | Gate | Enable only after |
| --- | --- | --- | --- |
| `ai_generation_enabled` | `false` | AI itinerary generation | provider, quota, prompt/data-use, and validation review |
| `open_trip_publishing_enabled` | `false` | publishing a trip to the community | trust/safety review and sanitized public projection |
| `discovery_enabled` | `false` | browsing public trips and matches | moderation, blocking, and empty-state review |
| `join_requests_enabled` | `false` | sending requests to join | approval, privacy, and abuse-rate-limit review |
| `push_enabled` | `false` | push notification rollout | consent copy, tokens, delivery, and opt-out tests |

Community discovery is intentionally conjunctive: the UI exposes the discovery
surface only when both `open_trip_publishing_enabled` and
`discovery_enabled` are true. Enabling one flag alone must not make open-trip
data visible.

## Client behavior

The clients install local defaults before the first network request. A timeout,
offline launch, malformed value, or unavailable Firebase configuration keeps
all high-risk flags disabled and never throws into the UI; basic manual/private
trip use remains available. Debug builds use a zero minimum fetch interval for
development; release builds use a one-hour minimum interval. Remote Config
values do not grant access to a Firestore document.

When implementing a gated feature, use the shared
`TripTandemFeatureFlags` value at the presentation boundary and repeat the
check in the repository/server operation. The AI server check intentionally
uses the reviewed template default; conditional client rollouts never grant
server authorization. Do not treat a hidden button as a security control.

## Review and deployment

Before changing a value, the release owner should review the complete template,
the feature PRD, the privacy disclosure, and the trust/safety checklist. Check
the currently deployed template first, then deploy the reviewed file:

```bash
firebase use triptandem
npx -y firebase-tools@latest remoteconfig:get --project triptandem
npx -y firebase-tools@latest deploy --only remoteconfig --project triptandem
```

Do not run the deploy command until the product owner has explicitly approved
the checked-in `remote_config.json`. Keep a copy of the previous template so a
bad rollout can be restored. Start with all values false, enable one capability
at a time, and record who approved each change and when it was rolled back or
made permanent.

## Verification matrix

For each flag, verify all of the following in a debug build and then in a
release-like build:

1. The feature is unavailable when the local default is false and the device
   is offline.
2. A reviewed true value appears after fetch/activate without an app crash.
3. A user without the required Firestore role still receives permission
   denied, regardless of the flag.
4. Turning the value back to false removes new entry points without deleting
   existing user data.
5. Analytics records only the coarse flag/flow outcome if the event taxonomy
   explicitly allows it; never log the template, user IDs, trip text, or exact
   locations.
