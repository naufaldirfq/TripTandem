/**
 * Pure RevenueCat webhook policy. Keeping lifecycle decisions independent of
 * Firebase makes the entitlement boundary easy to test without a live store.
 */

import { createHmac, timingSafeEqual } from "node:crypto";

export const REVENUECAT_PRO_ENTITLEMENT = "triptandem_pro";

/** Event names currently emitted by RevenueCat's webhook integration. */
export const REVENUECAT_WEBHOOK_EVENT_TYPES = new Set([
  "INITIAL_PURCHASE",
  "RENEWAL",
  "PRODUCT_CHANGE",
  "CANCELLATION",
  "BILLING_ISSUE",
  "NON_RENEWING_PURCHASE",
  "UNCANCELLATION",
  "TRANSFER",
  "SUBSCRIPTION_PAUSED",
  "EXPIRATION",
  "SUBSCRIPTION_EXTENDED",
  "REFUND_REVERSED",
  "INVOICE_ISSUANCE",
  "TEMPORARY_ENTITLEMENT_GRANT",
  // Kept for older integrations that represented a refund as its own event.
  "REFUND",
  "PURCHASE_REDEEMED",
]);

export type RevenueCatWebhookEvent = {
  type?: unknown;
  id?: unknown;
  app_user_id?: unknown;
  entitlement_id?: unknown;
  entitlement_ids?: unknown;
  product_id?: unknown;
  event_timestamp_ms?: unknown;
  expiration_at_ms?: unknown;
  transferred_from?: unknown;
  transferred_to?: unknown;
};

export type CurrentEntitlementState = {
  isActive: boolean;
  lastEventTimestampMs?: number;
  lastEventId?: string;
};

const MAX_TRANSFER_USER_IDS = 64;
const MAX_APP_USER_ID_LENGTH = 128;

/**
 * RevenueCat App User IDs become Firestore path segments in the entitlement
 * mirror. Reject separators and control characters so a webhook cannot turn a
 * user-controlled ID into a different document path.
 */
export function isSafeAppUserId(value: unknown): value is string {
  return typeof value === "string"
    && value.trim().length > 0
    && value.trim().length <= MAX_APP_USER_ID_LENGTH
    && !/[\\/\u0000-\u001f\u007f]/.test(value);
}

/** Parses and bounds transfer arrays without silently dropping malformed IDs. */
export function parseTransferAppUserIds(value: unknown): string[] | null {
  if (!Array.isArray(value) || value.length === 0 || value.length > MAX_TRANSFER_USER_IDS) return null;
  if (!value.every((entry) => isSafeAppUserId(entry))) return null;
  return [...new Set(value.map((entry) => (entry as string).trim()))];
}

/**
 * Determines the destination state for a signed TRANSFER event. A transfer
 * with an explicit Pro entitlement can grant the destination directly. When
 * RevenueCat omits entitlement IDs (the normal transfer payload), access is
 * inherited only when a source account has a verified active entitlement; we
 * fail closed when neither signal is present.
 */
export function resolveTransferActive(
  explicitlyTargetsEntitlement: boolean,
  sourceHasActiveEntitlement: boolean,
  destinationCurrentlyActive: boolean,
  expirationAtMs: number | null,
  nowMs = Date.now(),
): boolean {
  // TripTandem's supported catalog is entirely time-bounded. A missing
  // expiry is malformed/legacy state and must not grant a server entitlement,
  // even when the transfer payload explicitly names Pro or a destination was
  // previously active.
  if (expirationAtMs === null || expirationAtMs <= nowMs) return false;
  if (explicitlyTargetsEntitlement || sourceHasActiveEntitlement) return true;
  return destinationCurrentlyActive;
}

/**
 * Verifies RevenueCat's optional HMAC header over the exact raw request body.
 * The authorization-header path remains supported for integrations that use
 * RevenueCat's simpler shared-header configuration.
 */
export function verifyRevenueCatWebhookSignature(
  rawBody: Buffer | string | undefined,
  header: string | undefined,
  secret: string,
  nowMs = Date.now(),
  toleranceSeconds = 300,
): boolean {
  if (!rawBody || !header || !secret) return false;
  const parts = new Map<string, string>();
  for (const rawPart of header.split(",")) {
    const separator = rawPart.indexOf("=");
    if (separator <= 0) return false;
    const key = rawPart.slice(0, separator).trim();
    const value = rawPart.slice(separator + 1).trim();
    if (!key || !value) return false;
    parts.set(key, value);
  }
  const timestampText = parts.get("t");
  const signature = parts.get("v1");
  if (!timestampText || !signature || !/^\d+$/.test(timestampText) || !/^[0-9a-f]{64}$/i.test(signature)) {
    return false;
  }
  const timestamp = Number(timestampText);
  if (!Number.isSafeInteger(timestamp) || Math.abs(nowMs / 1000 - timestamp) > toleranceSeconds) return false;
  const signedBody = `${timestampText}.${typeof rawBody === "string" ? rawBody : rawBody.toString("utf8")}`;
  const computed = createHmac("sha256", secret).update(signedBody).digest("hex");
  const expected = Buffer.from(computed, "utf8");
  const actual = Buffer.from(signature, "utf8");
  return expected.length === actual.length && timingSafeEqual(expected, actual);
}

/** Returns true only when the webhook explicitly references TripTandem Pro. */
export function targetsEntitlement(event: RevenueCatWebhookEvent, entitlement = REVENUECAT_PRO_ENTITLEMENT): boolean {
  if (event.entitlement_id === entitlement) return true;
  return Array.isArray(event.entitlement_ids)
    && event.entitlement_ids.some((value) => value === entitlement);
}

/** Parses RevenueCat millisecond fields without accepting NaN, fractions, or negatives. */
export function parseWebhookMillis(value: unknown): number | null {
  const parsed = typeof value === "number" || typeof value === "string" ? Number(value) : NaN;
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null;
}

/**
 * Cancellation and pause notifications describe a future access change, not
 * an immediate entitlement revocation. Access is removed on EXPIRATION (or an
 * explicit refund), while billing issues retain access through a future
 * expiration/grace period.
 */
export function resolveWebhookActive(
  type: string,
  expirationAtMs: number | null,
  currentIsActive: boolean,
  nowMs = Date.now(),
): boolean {
  switch (type) {
    case "EXPIRATION":
    case "REFUND":
      return false;
    case "INVOICE_ISSUANCE":
      // An unpaid invoice is not a purchase and must never grant access.
      return currentIsActive;
    case "CANCELLATION":
    case "BILLING_ISSUE":
    case "SUBSCRIPTION_PAUSED":
      // These lifecycle events can arrive before the current period ends. A
      // cancellation/pause must never create access for a user whose verified
      // entitlement was not active already.
      return currentIsActive && expirationAtMs !== null && expirationAtMs > nowMs;
    default:
      // Every supported TripTandem product is time-bounded. Missing or
      // malformed expiry must fail closed rather than becoming an implicit
      // lifetime entitlement.
      return expirationAtMs !== null && expirationAtMs > nowMs;
  }
}

/**
 * Duplicate and out-of-order webhook deliveries are safe to ignore. RevenueCat
 * supplies an event id and event timestamp; older integrations may omit one,
 * so the absent value is treated as unknown rather than as a stale event.
 */
export function isStaleWebhook(
  eventTimestampMs: number | null,
  eventId: string | null,
  current: CurrentEntitlementState,
): boolean {
  if (eventId && current.lastEventId === eventId) return true;
  return eventTimestampMs !== null
    && typeof current.lastEventTimestampMs === "number"
    && eventTimestampMs < current.lastEventTimestampMs;
}
