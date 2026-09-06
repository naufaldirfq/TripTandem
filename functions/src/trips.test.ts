import test from "node:test";
import assert from "node:assert/strict";
import { canCreateActiveTrip, canUpdateTripCapacity, canUseTripCapacity, normalizeTripCreateInput } from "./trips";

const validInput = {
  title: "Kyoto in spring",
  destination: "Kyoto, Japan",
  startDate: "2026-04-12",
  endDate: "2026-04-19",
  destinationTimezone: "Asia/Tokyo",
  datesFlexible: false,
  visibility: "private",
  capacity: 4,
  status: "planning",
  interests: ["Food", "Nature"],
};

test("normalizes a server trip payload and strips whitespace", () => {
  const result = normalizeTripCreateInput({ ...validInput, title: "  Kyoto in spring  " });
  assert.equal(result.ok, true);
  if (result.ok) {
    assert.equal(result.value.title, "Kyoto in spring");
    assert.deepEqual(result.value.interests, ["Food", "Nature"]);
  }
});

test("rejects open publishing and unknown fields before the transaction", () => {
  assert.equal(normalizeTripCreateInput({ ...validInput, visibility: "open" }).ok, false);
  assert.equal(normalizeTripCreateInput({ ...validInput, unsafe: true }).ok, false);
  assert.equal(normalizeTripCreateInput({ ...validInput, endDate: "2026-04-01" }).ok, false);
});

test("free organizers get one active trip while verified Pro is fair-use", () => {
  assert.equal(canCreateActiveTrip(0, false), true);
  assert.equal(canCreateActiveTrip(1, false), false);
  assert.equal(canCreateActiveTrip(4, true), true);
  assert.equal(canUseTripCapacity(6, false), true);
  assert.equal(canUseTripCapacity(7, false), false);
  assert.equal(canUseTripCapacity(12, true), true);
  assert.equal(canUpdateTripCapacity(12, 12, false, false), true);
  assert.equal(canUpdateTripCapacity(12, 11, false, false), false);
  assert.equal(canUpdateTripCapacity(12, 12, false, true), false);
  assert.equal(canUpdateTripCapacity(12, 12, true, true), true);
});
