# TripTandem Phase 4 Status: Offline Access and Export (PRD 11)

Phase 4 implements offline access, hardware-backed local persistence, and privacy-safe itinerary export as defined in PRD 11.

## Implemented

### 1. Hardware-Backed Encrypted Local Cache
- Android implementation via `AndroidEncryptedPayloadStorage`:
  - Uses AES-256-GCM authenticated encryption.
  - Master keys generated and managed securely within `AndroidKeyStore`.
  - Stored in private app storage with encrypted payloads.
- KMP & iOS support via `SecurePayloadStorage` and `StandardProtectedTripCacheRepository`:
  - Self-healing corrupted cache detection.
  - Comprehensive index management (`trip_index:<userId>` and `trip_bundle:<userId>:<tripId>`).
  - Total cache byte size calculation for user privacy visibility.

### 2. Transparent Offline Fallbacks & Read-Only Enforcement
- `FirebaseRepositories` updates cached trip bundles on successful network fetch, and falls back to encrypted cached bundles when network fails with `TripTandemError.Offline`.
- Editing actions (creating/updating stops, reordering, managing members) are gated when offline (`isOnline`), showing clear feedback: `"Connection required to add or edit itinerary stops"`.
- 7-day stale offline authorization rule enforced per PRD 11 (`MAX_OFFLINE_AUTH_WINDOW_MILLIS = 7 * 24 * 60 * 60 * 1000L`). When cached data is older than 7 days, an alert banner advises reconnecting to re-authorize.

### 3. Sync Indicator & Manual Refresh
- `OfflineModeBanner` in `ItineraryScreen` displays freshness age (`<1h`, `1-24h`, `1-7d`, `>7d`) and prominent "Sync now" button.
- "Sync now" triggers a forced network refresh with live progress indicator and emits `offline_refresh_completed` with `result: "succeeded" | "failed"`.

### 4. Privacy-Safe Itinerary Export Engine & Preview
- Multi-format exporter (`ItineraryExporter`): PlainText and Markdown.
- Strict privacy defaults per PRD 11:
  - Private notes: **OFF** by default.
  - Lodging details (exact street addresses, reservation codes): **OFF** by default.
  - Traveler names: **OFF** by default (when enabled, outputs initials only to avoid PII exposure).
  - Stop times and locations: ON by default.
- Interactive modal dialog (`ExportItineraryDialog`):
  - Format toggle (Plain Text / Markdown).
  - Individual checkboxes for privacy controls with clear privacy explanation.
  - Live formatted preview.
  - One-tap "Copy" to system clipboard.
  - "Share" button integrated with native share sheet (`onShareExport`).
  - Analytics logged: `export_started(format)` and `export_completed(format, included_field_count)`.

### 5. Protected Cache Lifecycle & Privacy Controls
- Protected cache is automatically pruned or cleared on:
  - Sign-out (`reason: "sign_out"`).
  - Account deletion (`reason: "account_deletion"`).
  - Leaving a trip or member removal (`reason: "membership_removed"`).
  - Explicit manual clearing from Profile/Settings (`OfflineStorageCard`, `reason: "user_cleared"`).

### 6. Analytics Parity & Taxonomy Compliance
- 5 new analytics events fully verified and aligned across Android (`FirebaseAnalyticsTracker`), iOS (`FirebaseAnalyticsBridge`), and shared schemas:
  - `offline_cache_read` (`freshness_bucket`)
  - `offline_refresh_completed` (`result`)
  - `export_started` (`format`)
  - `export_completed` (`format`, `included_field_count`)
  - `protected_cache_cleared` (`reason`)
- Verified by `node scripts/check_analytics_parity.mjs` (78 total events, 60 parameter schemas).

## Verification

- `node scripts/check_analytics_parity.mjs`: PASSED (78 events, 60 parameter schemas).
- `./gradlew :shared:compileAndroidMain :shared:compileKotlinIosSimulatorArm64`: PASSED.
- `./gradlew :app:testDebugUnitTest`: PASSED (49/49 test tasks executed, including `OfflineCacheAndExportTest` covering bundle serialization, corrupted cache self-healing, 7-day stale policy, privacy export defaults, and markdown/plaintext generation).
