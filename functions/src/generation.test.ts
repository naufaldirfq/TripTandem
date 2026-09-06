import test from "node:test";
import assert from "node:assert/strict";
import {
  applyGeneratedItemEdits,
  boundedNonNegativeInteger,
  classifyGeminiResponse,
  hasGenerationAccess,
  hasItineraryRevisionConflict,
  hasUnsafeGenerationIntent,
  isVersionedGenerationResponse,
  normalizeGenerationInput,
  validateGeneratedItems,
} from "./generation";

test("quota counters accept only bounded non-negative integers", () => {
  assert.equal(boundedNonNegativeInteger(undefined, 30), 0);
  assert.equal(boundedNonNegativeInteger(null, 30), 0);
  assert.equal(boundedNonNegativeInteger(0, 30), 0);
  assert.equal(boundedNonNegativeInteger(30, 30), 30);
  for (const value of [-1, 31, 1.5, Number.NaN, Number.POSITIVE_INFINITY, "1", true]) {
    assert.equal(boundedNonNegativeInteger(value, 30), null);
  }
});

test("provider responses must carry the current generation schema version", () => {
  assert.equal(isVersionedGenerationResponse({ schemaVersion: "2026-09-04.v1", items: [] }), true);
  assert.equal(isVersionedGenerationResponse({ schemaVersion: "old", items: [] }), false);
  assert.equal(isVersionedGenerationResponse({ schemaVersion: "2026-09-04.v1" }), false);
  assert.equal(isVersionedGenerationResponse([]), false);
});

test("classifies Gemini safety, throttling, timeout, and success responses without provider text", () => {
  assert.equal(classifyGeminiResponse(200, { candidates: [{ finishReason: "SAFETY" }] }), "safety_blocked");
  assert.equal(classifyGeminiResponse(400, { error: { status: "SAFETY" } }), "safety_blocked");
  assert.equal(classifyGeminiResponse(429, {}), "provider_rate_limited");
  assert.equal(classifyGeminiResponse(504, {}), "provider_timeout");
  assert.equal(classifyGeminiResponse(200, { candidates: [{ content: { parts: [{ text: "{}" }] } }] }), "ok");
});

test("normalizes a minimum whole-trip request without private identity fields", () => {
  const result = normalizeGenerationInput({
    scope: "whole_trip",
    interests: ["Food", "Nature"],
    lockedItemIds: ["booked-1"],
  });
  assert.equal(result.ok, true);
  if (result.ok) {
    assert.deepEqual(result.value.interests, ["Food", "Nature"]);
    assert.equal("memberNames" in result.value, false);
  }
  assert.equal(normalizeGenerationInput({
    scope: "whole_trip",
    interests: ["Food"],
    lockedItemIds: [],
    memberNames: ["must-not-be-forwarded"],
  }).ok, false);
});

test("requires a day for single-day scope and bounds notes", () => {
  assert.equal(normalizeGenerationInput({ scope: "single_day", interests: [], lockedItemIds: [] }).ok, false);
  assert.equal(normalizeGenerationInput({ scope: "single_day", dayDate: "2026-02-30", interests: [], lockedItemIds: [] }).ok, false);
  const result = normalizeGenerationInput({
    scope: "single_day",
    dayDate: "2026-04-13",
    interests: [],
    lockedItemIds: [],
    accessibilityNotes: "wheelchair-friendly route",
  });
  assert.equal(result.ok, true);
});

test("rejects invalid optional values and non-string arrays instead of silently dropping them", () => {
  assert.equal(normalizeGenerationInput({ scope: "whole_trip", pace: "fast", interests: [], lockedItemIds: [] }).ok, false);
  assert.equal(normalizeGenerationInput({ scope: "whole_trip", interests: [42], lockedItemIds: [] }).ok, false);
  assert.equal(normalizeGenerationInput({ scope: "whole_trip", dayDate: "2026-04-13", interests: [], lockedItemIds: [] }).ok, false);
});

test("blocks clearly unsafe intent before a provider call", () => {
  assert.equal(hasUnsafeGenerationIntent({ interests: ["Food"], accessibilityNotes: "", dietNotes: "" }), false);
  assert.equal(hasUnsafeGenerationIntent({ interests: ["hack a border crossing"], accessibilityNotes: "", dietNotes: "" }), true);
  assert.equal(hasUnsafeGenerationIntent({ interests: [], accessibilityNotes: "", dietNotes: "", dailyStartLabel: "evade police" }), true);
});

test("worker access revalidation requires an active age-confirmed profile and editor access", () => {
  const profile = { exists: true, accountStatus: "active", ageConfirmed: true };
  const trip = { exists: true, ownerId: "owner", status: "planning" };
  assert.equal(hasGenerationAccess("owner", profile, trip, { exists: false }), true);
  assert.equal(hasGenerationAccess("editor", profile, trip, { exists: true, status: "active", role: "editor" }), true);
  assert.equal(hasGenerationAccess("viewer", profile, trip, { exists: true, status: "active", role: "viewer" }), false);
  assert.equal(hasGenerationAccess("editor", profile, { ...trip, status: "completed" }, { exists: true, status: "active", role: "editor" }), false);
  assert.equal(hasGenerationAccess("editor", { ...profile, ageConfirmed: false }, trip, { exists: true, status: "active", role: "editor" }), false);
  assert.equal(hasGenerationAccess("editor", { ...profile, accountStatus: "suspended" }, trip, { exists: true, status: "active", role: "editor" }), false);
});

test("fill-empty-days validation rejects suggestions on occupied days", () => {
  const result = validateGeneratedItems(
    [
      { type: "activity", title: "Occupied day", dayDate: "2026-04-12", startTimeLabel: "09:00", flexibleTime: false, durationMinutes: 60 },
      { type: "activity", title: "Empty day", dayDate: "2026-04-13", startTimeLabel: "09:00", flexibleTime: false, durationMinutes: 60 },
    ],
    {
      tripStartDate: "2026-04-12",
      tripEndDate: "2026-04-14",
      scope: "fill_empty_days",
      existingItems: [{ id: "existing-1", title: "Booked", dayDate: "2026-04-12" }],
      emptyDayDates: ["2026-04-13", "2026-04-14"],
    },
  );
  assert.deepEqual(result.items.map((item) => item.dayDate), ["2026-04-13"]);
  assert.equal(result.rejectedCount, 1);
});

test("rejects out-of-range or malformed generated items and flags duplicates", () => {
  const result = validateGeneratedItems(
    [
      { type: "activity", title: "Fushimi Inari", dayDate: "2026-04-13", startTimeLabel: "09:00", flexibleTime: false, durationMinutes: 120 },
      { type: "activity", title: "Fushimi Inari", dayDate: "2026-04-13", startTimeLabel: "09:30", flexibleTime: false, durationMinutes: 120 },
      { type: "activity", title: "Outside", dayDate: "2027-01-01", startTimeLabel: "09:00", flexibleTime: false, durationMinutes: 60 },
      { type: "activity", title: "Invalid calendar date", dayDate: "2026-02-30", startTimeLabel: "09:00", durationMinutes: 60, flexibleTime: false },
      { type: "lodging", title: "Hotel", dayDate: "2026-04-13", startTimeLabel: "09:00", durationMinutes: 60 },
    ],
    {
      tripStartDate: "2026-04-12",
      tripEndDate: "2026-04-19",
      scope: "whole_trip",
      existingItems: [{
        id: "existing-1",
        title: "Fushimi Inari Shrine",
        dayDate: "2026-04-13",
        place: "Fushimi Inari",
        startTimeLabel: "09:00",
        flexibleTime: false,
      }],
    },
  );
  assert.equal(result.items.length, 2);
  assert.equal(result.rejectedCount, 3);
  assert.ok(result.items.every((item) => item.warning));
});

test("flags place and nearby-time duplicates even when titles differ", () => {
  const result = validateGeneratedItems([
    {
      type: "activity",
      title: "Explore the market",
      dayDate: "2026-04-13",
      startTimeLabel: "10:00",
      flexibleTime: false,
      durationMinutes: 90,
      place: "Nishiki Market",
    },
  ], {
    tripStartDate: "2026-04-12",
    tripEndDate: "2026-04-19",
    scope: "whole_trip",
    existingItems: [{
      id: "existing-1",
      title: "Nishiki Market visit",
      dayDate: "2026-04-13",
      place: "Nishiki Market",
      startTimeLabel: "09:30",
      flexibleTime: false,
    }],
  });
  assert.equal(result.items[0]?.duplicateOfItemId, "existing-1");
  assert.match(result.items[0]?.warning ?? "", /Similar/);
});

test("omits suggestions that overlap a fixed booked window", () => {
  const result = validateGeneratedItems([
    {
      type: "activity",
      title: "Museum visit",
      dayDate: "2026-04-13",
      startTimeLabel: "10:30",
      flexibleTime: false,
      durationMinutes: 60,
    },
    {
      type: "activity",
      title: "Evening walk",
      dayDate: "2026-04-13",
      startTimeLabel: "18:00",
      flexibleTime: false,
      durationMinutes: 60,
    },
  ], {
    tripStartDate: "2026-04-12",
    tripEndDate: "2026-04-19",
    scope: "whole_trip",
    existingItems: [],
    lockedItems: [{
      dayDate: "2026-04-13",
      startTimeLabel: "10:00",
      flexibleTime: false,
      durationMinutes: 120,
      type: "activity",
    }],
  });
  assert.deepEqual(result.items.map((item) => item.title), ["Evening walk"]);
  assert.equal(result.rejectedCount, 1);
  assert.match(result.warnings[0] ?? "", /overlaps a booked item/);
});

test("applies bounded organizer edits without changing the generated scope", () => {
  const original = {
    id: "suggestion-1",
    type: "activity" as const,
    title: "Fushimi Inari",
    dayDate: "2026-04-13",
    startTimeLabel: "09:00",
    flexibleTime: false,
    durationMinutes: 120,
    place: "Kyoto",
  };
  const result = applyGeneratedItemEdits([original], [{
    id: original.id,
    title: "Fushimi Inari Shrine",
    startTimeLabel: "10:00",
    flexibleTime: false,
    durationMinutes: 90,
    note: "Allow extra time for the gates",
  }]);
  assert.equal(result.ok, true);
  if (result.ok) {
    assert.equal(result.items[0].title, "Fushimi Inari Shrine");
    assert.equal(result.items[0].dayDate, original.dayDate);
    assert.equal(result.items[0].type, original.type);
    assert.equal(result.items[0].durationMinutes, 90);
  }
});

test("rejects malformed preview edits and invalid clock labels", () => {
  const original = {
    id: "suggestion-1",
    type: "meal" as const,
    title: "Lunch",
    dayDate: "2026-04-13",
    startTimeLabel: "12:00",
    flexibleTime: false,
    durationMinutes: 60,
  };
  assert.equal(applyGeneratedItemEdits([original], [{ id: "unknown", title: "Nope" }]).ok, false);
  assert.equal(applyGeneratedItemEdits([original], [{ id: original.id, flexibleTime: false, startTimeLabel: "noon" }]).ok, false);
  assert.equal(applyGeneratedItemEdits([original], [{ id: original.id, title: "" }]).ok, false);
});

test("detects added, removed, and revised itinerary items before Apply", () => {
  const expected = { "item-1": 0, "item-2": 3 };
  assert.equal(hasItineraryRevisionConflict(expected, [
    { id: "item-1", revision: 0 },
    { id: "item-2", revision: 3 },
  ]), false);
  assert.equal(hasItineraryRevisionConflict(expected, [
    { id: "item-1", revision: 0 },
    { id: "item-2", revision: 4 },
  ]), true);
  assert.equal(hasItineraryRevisionConflict(expected, [
    { id: "item-1", revision: 0 },
    { id: "item-2", revision: 3 },
    { id: "item-3", revision: 0 },
  ]), true);
  assert.equal(hasItineraryRevisionConflict(expected, [{ id: "item-1", revision: 0 }]), true);
});
