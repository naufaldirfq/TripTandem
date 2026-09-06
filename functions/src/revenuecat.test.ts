import test from "node:test";
import assert from "node:assert/strict";
import { createHmac } from "node:crypto";
import {
  isSafeAppUserId,
  isStaleWebhook,
  parseWebhookMillis,
  parseTransferAppUserIds,
  resolveWebhookActive,
  resolveTransferActive,
  targetsEntitlement,
  verifyRevenueCatWebhookSignature,
} from "./revenuecat";

test("only explicitly targeted TripTandem Pro events can change the entitlement", () => {
  assert.equal(targetsEntitlement({ entitlement_ids: ["other"] }), false);
  assert.equal(targetsEntitlement({ entitlement_id: "triptandem_pro" }), true);
  assert.equal(targetsEntitlement({ entitlement_ids: ["other", "triptandem_pro"] }), true);
});

test("webhook millisecond fields are finite positive integers", () => {
  assert.equal(parseWebhookMillis("1700000000000"), 1700000000000);
  assert.equal(parseWebhookMillis(1700000000000), 1700000000000);
  assert.equal(parseWebhookMillis("NaN"), null);
  assert.equal(parseWebhookMillis(-1), null);
  assert.equal(parseWebhookMillis(1.5), null);
});

test("cancellation and pause preserve access until expiration", () => {
  const now = 1_700_000_000_000;
  assert.equal(resolveWebhookActive("CANCELLATION", now + 60_000, true, now), true);
  assert.equal(resolveWebhookActive("BILLING_ISSUE", now + 60_000, true, now), true);
  assert.equal(resolveWebhookActive("SUBSCRIPTION_PAUSED", now + 60_000, true, now), true);
  assert.equal(resolveWebhookActive("CANCELLATION", now + 60_000, false, now), false);
  assert.equal(resolveWebhookActive("SUBSCRIPTION_PAUSED", null, false, now), false);
  assert.equal(resolveWebhookActive("CANCELLATION", now - 1, true, now), false);
  assert.equal(resolveWebhookActive("CANCELLATION", null, true, now), false);
  assert.equal(resolveWebhookActive("EXPIRATION", now + 60_000, true, now), false);
  assert.equal(resolveWebhookActive("REFUND", now + 60_000, true, now), false);
});

test("unpaid invoice cannot grant access and purchase events expire safely", () => {
  const now = 1_700_000_000_000;
  assert.equal(resolveWebhookActive("INVOICE_ISSUANCE", null, false, now), false);
  assert.equal(resolveWebhookActive("INVOICE_ISSUANCE", null, true, now), true);
  assert.equal(resolveWebhookActive("INITIAL_PURCHASE", now - 1, false, now), false);
  assert.equal(resolveWebhookActive("RENEWAL", null, false, now), false);
});

test("duplicate and older webhook deliveries are ignored", () => {
  const current = { isActive: true, lastEventTimestampMs: 200, lastEventId: "event-2" };
  assert.equal(isStaleWebhook(300, "event-2", current), true);
  assert.equal(isStaleWebhook(100, "event-3", current), true);
  assert.equal(isStaleWebhook(300, "event-3", current), false);
  assert.equal(isStaleWebhook(null, "event-3", current), false);
});

test("transfer user IDs are bounded and safe as Firestore path segments", () => {
  assert.equal(isSafeAppUserId("firebase-user-1"), true);
  assert.equal(isSafeAppUserId("$RCAnonymousID:abc"), true);
  assert.equal(isSafeAppUserId("users/other"), false);
  assert.deepEqual(parseTransferAppUserIds([" source ", "source", "destination"]), ["source", "destination"]);
  assert.equal(parseTransferAppUserIds([]), null);
  assert.equal(parseTransferAppUserIds(["bad/id"]), null);
});

test("transfer access moves only from verified evidence", () => {
  const now = 1_700_000_000_000;
  assert.equal(resolveTransferActive(false, true, false, now + 60_000, now), true);
  assert.equal(resolveTransferActive(true, false, false, null, now), false);
  assert.equal(resolveTransferActive(false, false, true, null, now), false);
  assert.equal(resolveTransferActive(false, false, false, null, now), false);
  assert.equal(resolveTransferActive(true, true, true, now - 1, now), false);
});

test("RevenueCat HMAC verification uses the exact body and rejects stale or malformed signatures", () => {
  const secret = "test-webhook-secret";
  const body = '{"event":{"type":"TRANSFER"}}';
  const now = 1_700_000_000_000;
  const timestamp = Math.floor(now / 1000).toString();
  const signature = createHmac("sha256", secret).update(`${timestamp}.${body}`).digest("hex");
  assert.equal(verifyRevenueCatWebhookSignature(Buffer.from(body), `t=${timestamp},v1=${signature}`, secret, now), true);
  assert.equal(verifyRevenueCatWebhookSignature(Buffer.from(`${body} `), `t=${timestamp},v1=${signature}`, secret, now), false);
  assert.equal(verifyRevenueCatWebhookSignature(Buffer.from(body), `t=${Number(timestamp) - 301},v1=${signature}`, secret, now), false);
  assert.equal(verifyRevenueCatWebhookSignature(Buffer.from(body), "v1=not-a-signature", secret, now), false);
});
