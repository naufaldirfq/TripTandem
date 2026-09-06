# TripTandem judge and demo guide

This guide is the handoff for a Shipaton 2026 reviewer. It describes the
verified local path and makes the production-only steps explicit; it is not a
claim that signed store billing or live AI is enabled yet.

## What to demonstrate

TripTandem is a Kotlin Multiplatform app for turning a trip idea into a shared
plan. The safest end-to-end demo is:

1. Create an account and complete the display name, age confirmation, region,
   and language fields.
2. Create a private trip with a destination, dates, pace, budget, and capacity.
3. Add and edit a day-specific itinerary item, then use the invite flow to
   share a viewer or editor link.
4. Open Profile → Organizer Pro to see the localized monthly and annual
   offering, renewal information, Terms, Privacy, Restore, and subscription
   management controls.

Joining, manual planning, invitations, and safety controls are intentionally
available on the free plan. The app does not show a first-launch paywall.

## Local RevenueCat Test Store path (verified on Android)

The checked-in Android **debug** build uses the RevenueCat Test Store public
key. It is suitable for emulator/demo verification only and must never be
submitted to Google Play. No secret RevenueCat key is included in the app.

On a fresh debug install:

1. Finish profile setup and open Profile → Organizer Pro.
2. Confirm that monthly and annual products load with store-provided prices and
   billing periods. A one-week introductory trial is shown for a
   `never_purchased` Test Store account.
3. Select a package and complete the simulated purchase. The Organizer Pro
   card should become active and a second active trip or a Pro-sized trip can
   then be exercised.
4. Use Restore purchases to confirm that the entitlement is recovered after a
   restart. Cancel a subsequent simulated purchase to verify the neutral
   “free plan is still available” state.

The local Test Store flow was exercised with the Android CLI emulator on
September 5, 2026. The iOS host builds and launches in the simulator, but its
interactive Test Store and signed App Store purchase paths still require the
release owner's deliberate store setup and device verification.

## AI itinerary generation

The AI entry point is present behind the fail-closed `ai_generation_enabled`
Remote Config flag. The current live project keeps that flag `false` because
Firebase Functions billing/secrets and the provider data-use review are still
production gates. Do not advertise live AI generation in a build until the
Functions deployment, Gemini secret, webhook, and privacy review are complete.

## Before publishing a signed judge build

- Configure real Play Console and App Store Connect products in RevenueCat.
- Inject the matching platform public keys into Release builds; remove the
  Test Store key from any distributed artifact.
- Provide a free-trial or promo path for a clean judge account and repeat
  purchase, pending, cancellation, restore, expiration, refund/revocation,
  and account-transfer checks on both platforms.
- Publish the Terms and Privacy URLs used by the paywall.
- Upgrade Firebase to Blaze, deploy the regional Functions, set the Gemini and
  webhook secrets, and keep the AI flag disabled until the reviewed rollout.

See [RevenueCat setup](revenuecat.md), [Phase 2 status](phase2-status.md), and
the [release checklist](release-checklist.md) for the owner-only gates.
