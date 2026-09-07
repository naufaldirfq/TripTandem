# TripTandem Operator & Backend Gates Guide

This guide establishes the standard operating procedures for release managers, community operators, and moderators managing TripTandem's production rollout gates.

---

## 1. Overview of Gating Architecture

TripTandem employs defense-in-depth rollout gates across three distinct tiers:

```
┌────────────────────────────────────────────────────────┐
│ 1. Product Rollout Tier (Firebase Remote Config)       │
│    - ai_generation_enabled                             │
│    - open_trip_publishing_enabled                      │
│    - discovery_enabled                                 │
│    - join_requests_enabled                             │
│    - push_enabled                                      │
└───────────────────────────┬────────────────────────────┘
                            │
┌───────────────────────────▼────────────────────────────┐
│ 2. Operator Safety Tier (Firestore communityConfig)    │
│    - communityConfig/readiness: approved & expiresAtMs │
│    - communityConfig/operatorAudit: immutable audit    │
│    - customClaims: communityModerator = true           │
└───────────────────────────┬────────────────────────────┘
                            │
┌───────────────────────────▼────────────────────────────┐
│ 3. Server-Authoritative Backend Tier (Cloud Functions) │
│    - REVENUECAT_WEBHOOK_SECRET authentication          │
│    - GEMINI_API_KEY for AI generation                  │
│    - Server-authoritative createTrip & member counts   │
│    - Guarded deleteAccountProfile & deleteTrip         │
└────────────────────────────────────────────────────────┘
```

Even if a product flag is enabled in Remote Config, the community and AI services fail closed unless the Operator and Server-Authoritative tiers are also satisfied.

---

## 2. Using the Operator CLI (`scripts/manage_operator_gates.mjs`)

The repository provides a dedicated operator CLI tool:

### A. Inspect All Gates
```bash
node scripts/manage_operator_gates.mjs status
```
Inspects:
- Live Remote Config parameter values vs safe defaults.
- Community readiness approval status, expiry timestamp, and remaining window.
- RevenueCat webhook configuration status.

### B. Open or Renew Community Readiness
Community discovery and publishing require an active, unexpired operator approval:
```bash
# Open community for 24 hours
node scripts/manage_operator_gates.mjs readiness --approved true --hours 24 --notes "Staffed review queue active for launch" --operator "release-lead"

# Renew for 72 hours
node scripts/manage_operator_gates.mjs readiness --approved true --hours 72 --notes "Weekend community window"
```

### C. Emergency Pause (Kill Switch)
To immediately pause all community search, publishing, and join request operations without redeploying code:
```bash
node scripts/manage_operator_gates.mjs readiness --approved false --notes "Emergency pause per incident INC-102"
```

### D. Assign / Revoke Moderator Claims
The mobile app never exposes a moderator dashboard or self-claim path. Moderator privileges are assigned to verified operator accounts via custom claims:
```bash
# Assign moderator role
node scripts/manage_operator_gates.mjs moderator --uid "<FIREBASE_UID>" --enabled true --operator "admin"

# Revoke moderator role
node scripts/manage_operator_gates.mjs moderator --uid "<FIREBASE_UID>" --enabled false --operator "admin"
```

### E. Audit History
Review all historical operator gate actions:
```bash
node scripts/manage_operator_gates.mjs audit --limit 20
```

---

## 3. Moderating Community Content (`scripts/moderate_community.mjs`)

Moderators with the `communityModerator` claim use the reviewed CLI script to inspect held listings and take action:

```bash
export MODERATOR_ID_TOKEN_FILE="/path/to/moderator-token.txt"
export MODERATOR_APP_CHECK_TOKEN_FILE="/path/to/app-check-token.txt"

# Inspect moderation queue
echo '{"operation": "queue"}' > action.json
node scripts/moderate_community.mjs action.json

# Publish a reviewed listing
echo '{"operation": "publish_reviewed", "input": {"tripId": "<TRIP_ID>"}}' > action.json
node scripts/moderate_community.mjs action.json

# Unpublish a violating listing
echo '{"operation": "unpublish", "input": {"tripId": "<TRIP_ID>", "reason": "inappropriate_content"}}' > action.json
node scripts/moderate_community.mjs action.json

# Suspend a repeat violator
echo '{"operation": "suspend", "input": {"userId": "<USER_ID>", "reason": "repeated_harassment"}}' > action.json
node scripts/moderate_community.mjs action.json
```

---

## 4. RevenueCat Webhook & Entitlement Verification

- **Live Endpoint**: `https://asia-southeast2-triptandem.cloudfunctions.net/revenueCatWebhook`
- **Authentication**: Secured with `REVENUECAT_WEBHOOK_SECRET` stored in Google Secret Manager and mirrored in RevenueCat Webhook settings (`whintgr93c42a14d3`).
- **Test Endpoint**:
```bash
node scripts/manage_operator_gates.mjs test-webhook --secret "$REVENUECAT_WEBHOOK_SECRET"
```

The verifier strictly rejects stale event timestamps, unrecognized event types, and mismatched secret signatures, ensuring that only verified store receipts grant `triptandem_pro` access.
