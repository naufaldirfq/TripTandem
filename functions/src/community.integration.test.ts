import test from "node:test";
import assert from "node:assert/strict";
import { getFirestore } from "firebase-admin/firestore";
import { communityAction, moderateCommunity } from "./community";
const enabled = !!process.env.FIRESTORE_EMULATOR_HOST;
const db = getFirestore();
const call = async (uid: string, operation: string, input: Record<string, string> = {}) => JSON.parse((await communityAction.run({ auth: { uid, token: {} }, data: { operation, input } } as any)).json);
const moderate = (operation: string, subjectId: string, rest = {}) => moderateCommunity.run({ auth: { uid: "moderator", token: { communityModerator: true } }, data: { operation, subjectId, policyBasis: "Community guidelines review", ...rest } } as any);
const fixture = async (suffix: string, capacity = 3) => {
  const owner = `owner-${suffix}`, a = `applicant-${suffix}`, b = `second-${suffix}`, tripId = `trip-${suffix}`;
  for (const uid of [owner, a, b]) await db.doc(`users/${uid}`).set({ uid, displayName: "A Traveler", accountStatus: "active", ageConfirmed: true, primaryLanguage: "English", pace: "balanced", budgetBand: "moderate", interests: ["Food"] });
  await db.doc(`trips/${tripId}`).set({ ownerId: owner, title: "Private title", destination: "Exact private address", startDate: "2099-10-10", endDate: "2099-10-15", status: "planning", visibility: "private", capacity, activeMemberCount: 1, revision: 0, pace: "balanced", budgetBand: "moderate", interests: ["Food"] });
  await db.doc(`trips/${tripId}/members/${owner}`).set({ userId: owner, role: "owner", status: "active" });
  const input = { tripId, destinationId: "id-bali", title: "Explore together", expectationNote: "Enjoy a calm trip with shared planning.", summary: "Food and cultural activities" };
  const preview = await call(owner, "preview", input);
  await call(owner, "publish", { ...input, previewHash: preview.previewHash, acknowledged: "true" });
  await moderate("publish_reviewed", tripId);
  return { owner, a, b, tripId };
};
const submit = async (uid: string, tripId: string) => { const detail = await call(uid, "detail", { tripId }); return call(uid, "request", { tripId, profileHash: detail.profileHash, acknowledged: "true" }); };
test("community emulator transactions and privacy", { skip: !enabled }, async t => {
  await db.doc("communityConfig/readiness").set({ approved: true, expiresAtMs: Date.now() + 86400000, emulatorFlags: ["open_trip_publishing_enabled", "discovery_enabled", "join_requests_enabled"] });
  await t.test("reviewed public projection, duplicate request and capacity race", async () => {
    const f = await fixture(`race-${Date.now()}`, 2);
    const d = await call(f.a, "detail", { tripId: f.tripId });
    assert.ok(!JSON.stringify(d).includes("Exact private address"));
    await submit(f.a, f.tripId); await submit(f.b, f.tripId);
    await assert.rejects(submit(f.a, f.tripId));
    const outcomes = await Promise.allSettled([call(f.owner, "approve", { tripId: f.tripId, applicantId: f.a }), call(f.owner, "approve", { tripId: f.tripId, applicantId: f.b })]);
    assert.equal(outcomes.filter(o => o.status === "fulfilled").length, 1);
    assert.equal((await db.doc(`trips/${f.tripId}`).get()).get("activeMemberCount"), 2);
    await assert.rejects(call(f.a, "detail", { tripId: f.tripId }));
  });
  await t.test("blocks hide direct listing and forbid approval", async () => {
    const f = await fixture(`block-${Date.now()}`); await submit(f.a, f.tripId);
    await call(f.a, "block", { userId: f.owner });
    await assert.rejects(call(f.a, "detail", { tripId: f.tripId }));
    await assert.rejects(call(f.owner, "approve", { tripId: f.tripId, applicantId: f.a }));
    assert.equal((await call(f.a, "requests")).items.find((r: any) => r.tripId === f.tripId).status, "invalidated");
  });
  await t.test("withdraw, suspension, moderation, and stale public preview deny actions", async () => {
    const f = await fixture(`states-${Date.now()}`); await submit(f.a, f.tripId);
    await call(f.a, "withdraw", { tripId: f.tripId });
    await assert.rejects(call(f.owner, "approve", { tripId: f.tripId, applicantId: f.a }));
    await moderate("suspend", f.owner);
    await assert.rejects(call(f.b, "detail", { tripId: f.tripId }));
    const g = await fixture(`mod-${Date.now()}`); await moderate("unpublish", g.tripId);
    assert.equal((await db.doc(`trips/${g.tripId}`).get()).get("visibility"), "private");
    await assert.rejects(call(g.a, "detail", { tripId: g.tripId }));
    const h = await fixture(`stale-${Date.now()}`); await db.doc(`trips/${h.tripId}`).update({ revision: 99 });
    await assert.rejects(call(h.a, "detail", { tripId: h.tripId }));
  });
  await t.test("closing discovery preserves the private trip and reopening requires a fresh review", async () => {
    const f = await fixture(`reopen-${Date.now()}`);
    const input = { tripId: f.tripId, destinationId: "id-bali", title: "Explore together", expectationNote: "Enjoy a calm trip with shared planning.", summary: "Food and cultural activities" };
    await submit(f.a, f.tripId);
    await call(f.owner, "close", { tripId: f.tripId, pendingChoice: "keep" });
    await assert.rejects(call(f.a, "detail", { tripId: f.tripId }));
    assert.equal((await call(f.a, "requests")).items.find((r: any) => r.tripId === f.tripId).status, "pending");
    const preview = await call(f.owner, "preview", input);
    await call(f.owner, "reopen", { ...input, previewHash: preview.previewHash, acknowledged: "true" });
    await moderate("publish_reviewed", f.tripId);
    assert.equal((await call(f.a, "detail", { tripId: f.tripId })).projection.tripId, f.tripId);
    assert.equal((await db.doc(`trips/${f.tripId}`).get()).get("visibility"), "open");
  });
  await t.test("report idempotency, status audit, and moderator access control", async () => {
    const uid = `reporter-${Date.now()}`;
    const input = { subjectType: "user", subjectId: "someone", category: "privacy", detail: "Sensitive report narrative", idempotencyKey: "stable-key" };
    const first = await call(uid, "report", input), second = await call(uid, "report", input);
    assert.equal(first.reference, second.reference);
    await moderate("report_status", first.reference, { status: "triaged" });
    const ref = db.doc(`reports/${first.reference}`);
    assert.equal((await ref.get()).get("status"), "triaged");
    assert.equal((await ref.collection("audit").get()).size, 2);
    await assert.rejects(moderateCommunity.run({ auth: { uid, token: {} }, data: { operation: "queue" } } as any));
  });
  await t.test("disabled rollout and self approval fail closed", async () => {
    const f = await fixture(`gates-${Date.now()}`);
    await assert.rejects(submit(f.owner, f.tripId));
    await db.doc("communityConfig/readiness").update({ approved: false });
    await assert.rejects(call(f.a, "detail", { tripId: f.tripId }));
    await db.doc("communityConfig/readiness").update({ approved: true });
  });
});
