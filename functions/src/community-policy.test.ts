import test from "node:test";
import assert from "node:assert/strict";
import { safePublicText, publicProjection, compatibility, requestState, eligibleTrip, matchesFilters, projectionHash } from "./community-policy";
const profile = { displayName: "A Traveler", accountStatus: "active", ageConfirmed: true, primaryLanguage: "English", pace: "balanced", budgetBand: "moderate", interests: ["Food"], bio: "private biography", homeRegion: "exact home", avatarUrl: "private.jpg" };
const trip = { ownerId: "owner", title: "private title", destination: "private exact place", startDate: "2099-10-10", endDate: "2099-10-15", visibility: "open", status: "planning", pace: "balanced", budgetBand: "moderate", capacity: 3, activeMemberCount: 1, interests: ["Food"], note: "PRIVATE", inviteToken: "SECRET" };
const input = { destinationId: "id-bali", title: "Explore together", expectationNote: "A relaxed group exploring local culture.", summary: "Food and cultural activities" };
test("public projection uses an exact field allowlist and never copies private trip/profile values", () => {
  const projection = publicProjection("trip", trip, profile, input);
  assert.deepEqual(Object.keys(projection).sort(), ["tripId", "title", "destinationId", "destination", "startDate", "endDate", "datesFlexible", "pace", "budgetBand", "capacity", "remainingSpots", "host", "languages", "interests", "expectationNote", "summary"].sort());
  const json = JSON.stringify(projection);
  for (const secret of ["PRIVATE", "SECRET", "private biography", "exact home", "private.jpg", "private exact place", "private title"]) assert.ok(!json.includes(secret));
  assert.equal(projection.host.initials, "AT");
});
test("contact/location screening rejects common prohibited content and unicode evasions", () => {
  for (const value of ["Meet at 15 Ocean Road", "hotel in town", "contact me @alice", "https://example.com", "+62 812 3456 7890", "send money now", "ＷＷＷ.example.com", "hello\u202eworld", "telegram Alice", "room twelve", "meet at the villa"]) assert.equal(safePublicText(value), false, value);
  assert.equal(safePublicText(input.expectationNote), true);
});
test("publishing eligibility rejects stale, suspended, full, and malformed trips", () => {
  assert.equal(eligibleTrip(trip, profile, "2099-01-01"), true);
  for (const changed of [{ capacity: 1 }, { activeMemberCount: -1 }, { status: "cancelled" }, { startDate: "2020-01-01" }, { startDate: "2099-02-30" }]) assert.equal(eligibleTrip({ ...trip, ...changed }, profile, "2099-01-01"), false);
  assert.equal(eligibleTrip(trip, { ...profile, accountStatus: "suspended" }, "2099-01-01"), false);
});
test("compatibility explains missing data and ignores protected attributes", () => {
  const p = publicProjection("trip", trip, profile, input);
  const fit = compatibility(p, profile, { startDate: trip.startDate, endDate: trip.endDate });
  assert.ok(fit.reasons.includes("Exact date overlap"));
  assert.deepEqual(fit, compatibility(p, { ...profile, gender: "x", nationality: "y", age: 99, reports: 100 }, { startDate: trip.startDate, endDate: trip.endDate }));
  assert.equal(compatibility(p, {}).label, "Not enough information");
  assert.ok(compatibility(p, {}).reasons.length > 0);
  assert.equal(matchesFilters(p, { destinationId: "id-bali", startDate: "2099-10-12", endDate: "2099-10-20", spots: 2 }), true);
  assert.equal(matchesFilters(p, { destinationId: "jp-tokyo" }), false);
  assert.equal(matchesFilters(p, { destinationId: "id-bali", spots: 3 }), false);
});
test("request decisions fail closed after block, expiry, closure, fullness or suspension", () => {
  const now = Date.parse("2099-01-01"); const request = { status: "pending", expiresAtMs: now + 10000 };
  assert.equal(requestState(request, trip, profile, false, now), "pending");
  assert.equal(requestState(request, trip, profile, true, now), "invalidated");
  assert.equal(requestState(request, trip, profile, false, now + 10000), "expired");
  for (const changed of [{ visibility: "private" }, { status: "cancelled" }, { activeMemberCount: 3 }]) assert.equal(requestState(request, { ...trip, ...changed }, profile, false, now), "invalidated");
  assert.equal(requestState(request, trip, { ...profile, accountStatus: "suspended" }, false, now), "invalidated");
  for (const status of ["approved", "declined", "withdrawn", "expired", "invalidated"]) assert.equal(requestState({ ...request, status }, trip, profile, false, now), status);
});
test("preview hashes change when any public field changes", () => {
  const p = publicProjection("trip", trip, profile, input);
  assert.notEqual(projectionHash(p), projectionHash({ ...p, title: "Changed" }));
});

test("push respects categories and timezone quiet hours; itinerary remains activity-only", async () => {
  const { pushAllowed } = await import("./community-policy");
  const preferences = { push: true, requests: true, membership: true, trip_changes: false, timezone: "Asia/Jakarta", quietStart: 22, quietEnd: 8 };
  assert.equal(pushAllowed(preferences, "request_approved", new Date("2026-09-07T05:00:00Z")), true);
  assert.equal(pushAllowed(preferences, "request_approved", new Date("2026-09-07T16:00:00Z")), false);
  assert.equal(pushAllowed(preferences, "trip_changed", new Date("2026-09-07T05:00:00Z")), false);
  assert.equal(pushAllowed(preferences, "itinerary_digest", new Date("2026-09-07T05:00:00Z")), false);
  assert.equal(pushAllowed({ ...preferences, push: false }, "request_approved", new Date("2026-09-07T05:00:00Z")), false);
});
