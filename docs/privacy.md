# TripTandem privacy disclosure draft

This is a product-development draft for the Shipaton build, not a legal
privacy policy. It should be reviewed and replaced with the final policy before
production analytics or public-trip discovery is enabled.

## What TripTandem stores

- Account identifiers and authentication state supplied by Firebase
  Authentication.
- Profile fields that a traveler chooses to provide, such as display name,
  avatar, home region, pace, and budget band.
- Trip planning data that a user or approved collaborator creates, including
  destination, dates, itinerary items, roles, invitations, and join requests.
- Safety reports and account-deletion audit records needed to operate trust and
  safety workflows.
- Community activity and notification records are retained for 90 days. Closed
  join requests are retained for 30 days after their terminal state. Safety
  reports are retained for 180 days and their restricted moderation audit
  records for 365 days, unless a documented legal hold requires longer
  preservation. Delivery tokens and installation records expire after 30 days
  without refresh and are removed on sign-out or account deletion.

When a user starts AI itinerary generation, the normalized planning parameters
and sanitized preview are kept in a temporary generation job for up to seven
days. This supports foreground resume, retry, and explicit Apply. The provider
prompt and raw provider response are transient and are not persisted in
Firestore, analytics, or general application logs. A scheduled cleanup removes
expired generation jobs and their coordination locks at least every 24 hours;
account deletion must also remove outstanding jobs, locks, and allowance
metadata. The mobile account-deletion flow calls the authenticated regional
`deleteAccountProfile` server boundary before revoking Firebase Auth. That
callable rechecks active shared-trip ownership, deletes each owned trip root and
its known `members`, `itinerary`, `invites`, and `joinRequests` subcollections,
deletes any generation jobs on owned trips (including collaborator requests),
the requester's generation jobs, per-trip generation locks, and
`users/{uid}/billing/*` documents in bounded, idempotent batches, and then
deletes the profile in a guarded transaction. Firestore parent deletes do not
cascade, so trip-graph cleanup is performed by this trusted boundary and fails
closed if concurrent ownership data remains.

## AI provider and data-use decision

TripTandem uses the Gemini Developer API from a server-only Firebase Functions
worker. The default model is `gemini-3.7-flash` (overridable by the server-only
`GEMINI_MODEL` setting); no model key or provider call ships in either mobile
client. The worker sends only the normalized trip-planning fields needed for a
draft: destination, trip dates/time zone, pace, budget band, selected interests,
optional generation-specific accessibility or diet notes, daily time labels,
generation scope, and coarse booked-item schedule windows. It never sends member
names, emails, biographies, contact details, exact lodging, private notes, or
account identifiers.

The applicable provider terms are the [Gemini API Additional Terms of
Service](https://ai.google.dev/gemini-api/terms) and [Gemini API data logging
policy](https://ai.google.dev/gemini-api/docs/logs-policy). Paid Gemini API
services state that prompts and responses are not used to improve Google
products, while logging and abuse-monitoring behavior remains governed by the
current provider terms. TripTandem therefore promises only its own seven-day
job retention and transient raw request/response handling; it does not promise
zero provider retention. The product/legal owner must confirm the project
billing tier, applicable provider terms, and final disclosure before enabling
`ai_generation_enabled` in production.

## How the data is used

Trip data is used to show the shared plan, enforce trip roles, deliver essential
activity updates, and (when the feature is enabled) calculate compatibility
signals. Safety data is used to investigate reports and protect travelers.

Firebase Analytics receives only coarse product events such as screen IDs and
success/failure states. Analytics parameters must never contain itinerary text,
exact addresses, dates, member biographies, contact details, or message
content. Crash diagnostics must follow the same minimization rule.

## Sharing and visibility

Private trip documents are restricted by Firestore rules to the owner and
approved members. Unlisted trips require a revocable invitation path. Open-trip
discovery must use a sanitized projection and is disabled until its trust and
safety review is complete. TripTandem does not process bookings or transfer
travelers’ money in this release.

## Controls and retention

Users should be able to edit profile data, leave a trip, revoke an invitation,
block/report another user, and request account deletion. Account deletion must
remove or anonymize personal data while preserving only narrowly necessary
moderation/audit records. The Phase 3 retention periods are documented above;
the product owner must still replace this draft with the final policy and
publish a staffed support contact before enabling community discovery.

## Consent and children

Analytics collection should be disclosed and controlled according to the final
store privacy declarations. The release audience and age gate must be decided
before launch; do not knowingly collect data from children without the required
parental and legal safeguards.

## Review checklist before production

- Confirm the legal entity, support contact, jurisdictions, and effective date.
- Confirm the seven-day generation-job retention and 24-hour cleanup behavior,
  then document retention periods for account, trip, analytics, crash, and
  moderation records, including deletion exceptions.
- Reconcile Firebase Auth, Firestore, Analytics, Crashlytics (if enabled), and
  RevenueCat data flows with the final store privacy declarations.
- Decide whether analytics is opt-in, opt-out, or disabled until consent for
  each launch region, and make the in-app control copy match that decision.
- Explain subscription billing, restore behavior, account deletion, and the
  fact that deleting a TripTandem account does not cancel store billing.
- Have product/legal review the final text before enabling public open-trip
  discovery or production analytics.
