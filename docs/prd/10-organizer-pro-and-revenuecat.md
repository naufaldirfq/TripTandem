# PRD 10 — Organizer Pro and RevenueCat

Phase: 2  
Priority: P0 and hackathon eligibility critical  
Owner: Product + monetization  
Status: Phase 2 implementation complete; catalog/paywall configured for Test
Store, with production store credentials and Functions deployment pending.
The optional one-trip Organizer Pass is deferred until its entitlement and
restore semantics are unambiguous.

Compatibility filters and offline/export are listed as future Organizer Pro
value areas, but are owned by PRD 06 (Phase 3) and PRD 11 (Phase 4)
respectively. They are not part of the Phase 2 P0 implementation or its
paywall triggers; the app must not present a Pro trigger for an unavailable
capability.

## Outcome

Monetize the organizer’s coordination workload without charging every invited member or withholding essential safety and collaboration controls.

## Product model

Working entitlement: `triptandem_pro`

Recommended initial products, subject to store configuration and price testing:

- Monthly Pro: approximately USD 5.99 equivalent
- Annual Pro: approximately USD 29.99 equivalent
- Optional one-trip Organizer Pass: approximately USD 4.99 equivalent, only if entitlement duration and store semantics can be communicated cleanly

Start with monthly + annual if the one-trip pass threatens schedule. Localized prices always come from the store through RevenueCat, never hardcoded.

## Free versus Pro

| Capability | Free | Organizer Pro |
|---|---|---|
| Join trips and accept invites | Included | Included |
| Create active trips | 1 | Unlimited/fair-use |
| Manual itinerary | Included | Included |
| Members per trip | Up to 6 | Up to 12 |
| AI generation | 1 successful trial generation | Monthly/fair-use allowance |
| Open-trip request/approval | Included | Included |
| Advanced compatibility filters | Basic | Included |
| Offline/export | Basic cached view | Enhanced export/offline |
| Safety/report/block | Included | Included |

Existing user content remains viewable and editable at a useful basic level after Pro expires. Expiration never removes members, hides safety controls, or deletes trips.

## User stories

- As an organizer, I understand Pro benefits before purchasing.
- As a purchaser, I can complete, cancel, or restore through the platform-standard flow.
- As a member, I benefit from an organizer’s enhanced trip without needing my own subscription.
- As an expired subscriber, I know what changed and retain my data.
- As a judge, I can test premium features through the required free trial or promo access.

## Paywall triggers

Show after explicit value intent:

- Creating a second active trip
- Requesting additional AI generation after the free allowance
- Selecting a clearly labeled Pro export/filter
- Opening “Upgrade to Pro” from settings

Do not show on first launch, invitation acceptance, join-request submission, report/block, or account deletion.

## Functional requirements

1. Use the official RevenueCat Kotlin Multiplatform SDK with platform-specific public API keys; no secret key ships in the client.
2. App Store and Play products map to the same RevenueCat offering and `triptandem_pro` entitlement.
3. A stable authenticated App User ID associates purchases with the TripTandem account across platforms; identity merge/transfer behavior is explicitly configured and tested.
4. Offerings, product titles, localized prices, periods, introductory eligibility, and purchase state come from RevenueCat/store data.
5. Paywall includes close, purchase, restore, Terms, Privacy, renewal terms, and subscription management path.
6. Purchase states include `idle`, `loading_products`, `ready`, `purchasing`, `pending`, `active`, `cancelled_by_user`, `failed`, and `unavailable`.
7. User cancellation is not displayed as an error.
8. Pending purchases do not grant access until entitlement is active.
9. Entitlement refresh occurs at app start, foreground, post-purchase, restore, authentication change, and server webhook update.
10. Backend authorization for Pro server resources derives from verified RevenueCat state/webhook, not a client boolean.
11. Restore is visible without requiring another purchase attempt.
12. If product loading fails, the free app remains usable and no fabricated price is shown.
13. Expiration stops new gated actions but preserves previously created content and current trip membership.
14. Account deletion explains store subscription cancellation separately; deleting the account does not falsely claim to cancel store billing.
15. Judges receive a functioning free trial or promo code and clear testing instructions through the hackathon submission.

## Organizer-funded trip behavior

- Pro applies to organizer capabilities and eligible trips owned by that account.
- Members receive the resulting shared itinerary/features where needed, but do not inherit a personal subscription.
- Transferring ownership recalculates organizer-only limits and warns before completion.
- A trip is never disabled because ownership transferred to a free user; creation/generation of new Pro-only actions is limited instead.

## Experiments

Run one variable at a time:

- Trigger copy: time saved versus group coordination value
- Monthly versus annual default presentation, without preselected consent
- Free AI allowance size

Do not run fake discounts, countdowns, hidden close controls, or pricing that differs from checkout disclosure.

## Analytics

- `paywall_viewed(trigger, offering_id, entitlement_state)`
- `package_selected(package_type)`
- `purchase_started(package_type)`
- `purchase_completed(package_type, trial, localized_price_bucket)`
- `purchase_cancelled(package_type)`
- `purchase_failed(package_type, error_class)`
- `restore_started`
- `restore_completed(result_class)`
- `entitlement_changed(from, to, source)`

Never send receipt, transaction ID, store account data, API key, or exact user identifier to general analytics. Revenue metrics reported publicly must match RevenueCat and store dashboards.

## Acceptance criteria

- Test and production products load and purchase on Android and iOS.
- Purchase, pending, cancellation, failure, offline, expiration, billing retry, refund/revocation, and restore paths are tested.
- The same authenticated user receives the correct entitlement on both platforms according to configured transfer rules.
- A forged client `isPro` state cannot access gated server operations.
- Product-unavailable state leaves the free experience usable.
- Paywall shows localized price, billing period, renewal disclosure, close, restore, Terms, and Privacy.
- Safety, joining, basic collaboration, and existing data are never paywalled.
- Judge access works in the publicly released builds.

## Dependencies and open decisions

- Create store products immediately because review/configuration can outlast implementation.
- Confirm subscription group, grace period, account hold, and RevenueCat transfer behavior before production.
- One-trip pass is optional; do not ship it until its entitlement duration and restore behavior are unambiguous.
