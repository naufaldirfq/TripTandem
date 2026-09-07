import { getMessaging } from "firebase-admin/messaging";
import { createHash } from "node:crypto";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { onDocumentWritten, onDocumentCreated } from "firebase-functions/v2/firestore";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { getApps, initializeApp } from "firebase-admin/app";
import { getFirestore, FieldValue, Transaction } from "firebase-admin/firestore";
import { getRemoteConfig } from "firebase-admin/remote-config";
import { isRemoteConfigBooleanEnabled } from "./remoteconfig";
import { isValidIsoDate } from "./generation";
import { RecordData, DESTINATIONS, REPORT_CATEGORIES, safeId, safePublicText, pushAllowed, pushCategory, profileProjection, publicProjection, projectionHash, eligibleTrip, compatibility, matchesFilters, requestState } from "./community-policy";
if (!getApps().length) initializeApp();
const db = getFirestore();
const region = "asia-southeast2";
const nowField = () => FieldValue.serverTimestamp();
const hash = (s: string) => createHash("sha256").update(s).digest("hex");
const id = (v: unknown): string => { if (!safeId(v)) throw new HttpsError("invalid-argument", "invalid_identifier"); return v; };
const unavailable = () => new HttpsError("permission-denied", "unavailable");
const active = (p: RecordData | undefined) => p?.accountStatus === "active" && p?.ageConfirmed === true;
const blockRef = (a: string, b: string) => db.doc(`communityBlocks/${a}/targets/${b}`);
async function blocked(a: string, b: string, tx?: Transaction, since?: number): Promise<boolean> {
  const refs = [blockRef(a, b), blockRef(b, a)];
  const docs = tx ? await tx.getAll(...refs) : await db.getAll(...refs);
  return docs.some(d => d.exists && (d.get("active") !== false || (since !== undefined && d.get("createdAtMs") >= since)));
}
async function gate(join = false) {
  const readiness = await db.doc("communityConfig/readiness").get();
  const template = process.env.FIRESTORE_EMULATOR_HOST
    ? { parameters: Object.fromEntries((readiness.get("emulatorFlags") ?? []).map((key: string) => [key, { defaultValue: { value: "true" } }])) }
    : await getRemoteConfig().getTemplate();
  const keys = ["open_trip_publishing_enabled", "discovery_enabled", ...(join ? ["join_requests_enabled"] : [])];
  if (!keys.every(k => isRemoteConfigBooleanEnabled(template, k))) throw new HttpsError("failed-precondition", "community_paused");
  if (readiness.get("approved") !== true || readiness.get("expiresAtMs") <= Date.now()) throw new HttpsError("failed-precondition", "community_paused");
}
function activity(tx: Transaction, uid: string, key: string, type: string, tripId: string, actionable = true) {
  // Deterministic ID makes source-trigger redelivery harmless; create source state and outbox atomically.
  const ref = db.doc(`communityActivity/${uid}/items/${hash(key)}`);
  tx.set(ref, { type, tripId, actionable, read: false, createdAtMs: Date.now(), expiresAtMs: Date.now() + 90 * 86400000, updatedAt: nowField() });
}
async function publicListing(tripId: string, uid: string, tx?: Transaction) {
  const refs = [db.doc(`communityListings/${tripId}`), db.doc(`trips/${tripId}`)];
  const [listing, trip] = tx ? await tx.getAll(...refs) : await db.getAll(...refs);
  const t = trip.data();
  if (!t || !listing.exists) throw unavailable();
  const ownerRef = db.doc(`users/${t.ownerId}`);
  const owner = tx ? await tx.get(ownerRef) : await ownerRef.get();
  if (await blocked(uid, t.ownerId, tx) || !eligibleTrip(t, owner.data(), new Date().toISOString().slice(0, 10))
    || t.visibility !== "open" || listing.get("status") !== "published" || listing.get("tripRevision") !== t.revision
    || listing.get("ownerId") !== t.ownerId || listing.get("ownerProfileHash") !== projectionHash(profileProjection(owner.data()!))) throw unavailable();
  // Rebuild from validated input, never return the stored internal document.
  return { projection: publicProjection(tripId, t, owner.data()!, listing.get("input")), trip: t, owner: owner.data()! };
}
async function rateLimit(tx: Transaction, uid: string, operation: string, max: number) {
  const ref = db.doc(`communityLimits/${hash(uid + operation + new Date().toISOString().slice(0, 10))}`);
  const snap = await tx.get(ref); const count = snap.get("count") ?? 0;
  if (!Number.isInteger(count) || count >= max) throw new HttpsError("resource-exhausted", "try_later");
  return () => tx.set(ref, { count: count + 1, expiresAtMs: Date.now() + 2 * 86400000 });
}
/** Shared native adapters pass a small string map; responses are JSON to preserve the same wire schema on KMP/iOS. */
export const communityAction = onCall({ region, enforceAppCheck: true }, async request => {
  if (!request.auth) throw new HttpsError("unauthenticated", "sign_in_required");
  const uid = request.auth.uid; const data = request.data as RecordData;
  if (!data || typeof data !== "object" || typeof data.operation !== "string") throw new HttpsError("invalid-argument", "invalid_request");
  const input: RecordData = data.input ?? {}; const op = data.operation;
  const profile = await db.doc(`users/${uid}`).get();
  if (!active(profile.data()) && !["report", "activity", "read", "preferences", "blocks", "block", "unblock", "unregister_installation"].includes(op)) throw unavailable();
  let result: unknown;
  if (op === "destinations") {
    await gate();
    result = { items: Object.entries(DESTINATIONS).map(([id, label]) => ({ id, label })) };
  }
  else if (op === "preview" || op === "publish" || op === "reopen") {
    await gate(); const tripId = id(input.tripId);
    result = await db.runTransaction(async tx => {
      const trip = await tx.get(db.doc(`trips/${tripId}`)); const p = await tx.get(db.doc(`users/${uid}`));
      const lock = await tx.get(db.doc(`tripDeletionLocks/${tripId}`));
      if (trip.get("ownerId") !== uid || lock.exists || !eligibleTrip(trip.data(), p.data(), new Date().toISOString().slice(0, 10))) throw unavailable();
      let projection; try { projection = publicProjection(tripId, trip.data()!, p.data()!, input); } catch { throw new HttpsError("invalid-argument", "public_content_needs_review"); }
      const previewHash = projectionHash(projection);
      if (op === "publish" || op === "reopen") {
        if (input.previewHash !== previewHash || input.acknowledged !== "true") throw new HttpsError("failed-precondition", "review_public_preview");
        const consume = await rateLimit(tx, uid, "publish", 12); consume();
        // First publication requires a human review of broad text. No automated location filter is treated as complete.
        tx.set(db.doc(`communityListings/${tripId}`), { input: { destinationId: input.destinationId, title: input.title, expectationNote: input.expectationNote, summary: input.summary }, ownerId: uid,
          destinationId: input.destinationId, status: "held", previewHash, tripRevision: trip.get("revision"), ownerProfileHash: projectionHash(profileProjection(p.data()!)), updatedAt: nowField(), createdAtMs: Date.now() }, { merge: true });
        if (op === "reopen") tx.set(db.doc(`communityClosures/${tripId}`), { invalidate: false, reopenedAt: nowField() }, { merge: true });
      }
      return { projection, previewHash, status: op === "preview" ? "preview" : "held" };
    });
  } else if (op === "search") {
    await gate();
    if (!DESTINATIONS[input.destinationId]) throw new HttpsError("invalid-argument", "destination_required");
    if ((input.startDate && !isValidIsoDate(input.startDate)) || (input.endDate && !isValidIsoDate(input.endDate))) {
      throw new HttpsError("invalid-argument", "invalid_date_filter");
    }
    if (input.startDate && input.endDate && input.startDate > input.endDate) throw new HttpsError("invalid-argument", "invalid_date_range");
    if (input.pace && !["relaxed", "balanced", "packed", "slow", "fast"].includes(input.pace)) throw new HttpsError("invalid-argument", "invalid_pace_filter");
    if (input.budgetBand && !["budget", "moderate", "comfort", "premium", "mid", "flexible"].includes(input.budgetBand)) throw new HttpsError("invalid-argument", "invalid_budget_filter");
    if (input.language && !safePublicText(input.language, 1, 40)) throw new HttpsError("invalid-argument", "invalid_language_filter");
    if (input.interest && !safePublicText(input.interest, 1, 40)) throw new HttpsError("invalid-argument", "invalid_interest_filter");
    if (input.spots !== undefined && input.spots !== "") {
      const spots = Number(input.spots);
      if (!Number.isInteger(spots) || spots < 1 || spots > 12) throw new HttpsError("invalid-argument", "invalid_spots_filter");
    }
    // ID cursor is stable under deletions. Hard filters precede ranking within each bounded page.
    let query = db.collection("communityListings").where("destinationId", "==", input.destinationId).orderBy("__name__").limit(40);
    if (input.cursor) query = query.startAfter(id(input.cursor));
    const page = await query.get(); const items: RecordData[] = [];
    for (const doc of page.docs) {
      try {
        const { projection } = await publicListing(doc.id, uid);
        if (matchesFilters(projection, input)) { const fit = compatibility(projection, profile.data()!, input); items.push({ ...projection, fit }); }
      } catch (e) { if (!(e instanceof HttpsError)) throw e; }
    }
    items.sort((a, b) => b.fit.score - a.fit.score || a.tripId.localeCompare(b.tripId));
    result = { items: items.map(({ fit, ...rest }) => ({ ...rest, fit: { label: fit.label, reasons: fit.reasons, missingFields: fit.missingFields } })), cursor: page.size === 40 ? page.docs.at(-1)!.id : "" };
  } else if (op === "detail") {
    await gate(); const { projection } = await publicListing(id(input.tripId), uid);
    const fit = compatibility(projection, profile.data()!, input);
    result = { projection, fit: { label: fit.label, reasons: fit.reasons, missingFields: fit.missingFields }, sharedProfile: profileProjection(profile.data()!) };
  } else if (op === "close") {
    const tripId = id(input.tripId);
    if (!["keep", "invalidate"].includes(input.pendingChoice)) throw new HttpsError("invalid-argument", "pending_choice_required");
    await db.runTransaction(async tx => {
      const ref = db.doc(`trips/${tripId}`); const trip = await tx.get(ref);
      if (trip.get("ownerId") !== uid) throw unavailable();
      tx.set(db.doc(`communityListings/${tripId}`), { status: "closed", updatedAt: nowField() }, { merge: true });
      // keep means retain for a later reviewed reopen; approval remains unavailable while closed.
      tx.update(ref, { visibility: "private", revision: trip.get("revision") + 1, updatedAt: nowField() });
      tx.set(db.doc(`communityClosures/${tripId}`), { invalidate: input.pendingChoice === "invalidate", createdAtMs: Date.now() });
    }); result = { status: "closed" };
  } else if (op === "request") {
    await gate(true); const tripId = id(input.tripId);
    const intro = input.introduction ?? "";
    if ((intro && !safePublicText(intro, 20, 300)) || input.acknowledged !== "true") throw new HttpsError("invalid-argument", "review_introduction_and_safety");
    result = await db.runTransaction(async tx => {
      const listing = await publicListing(tripId, uid, tx);
      const p = await tx.get(db.doc(`users/${uid}`));
      const ref = db.doc(`trips/${tripId}/joinRequests/${uid}`);
      const previous = await tx.get(ref); const member = await tx.get(db.doc(`trips/${tripId}/members/${uid}`));
      const lock = await tx.get(db.doc(`tripDeletionLocks/${tripId}`));
      const consume = await rateLimit(tx, uid, "request", 10);
      const tripConsume = await rateLimit(tx, tripId, "trip_requests", 60);
      if (!active(p.data()) || lock.exists || listing.trip.ownerId === uid || member.get("status") === "active" || previous.exists) throw new HttpsError("failed-precondition", "request_unavailable");
      const sharedProfile = profileProjection(p.data()!);
      if (input.profileHash !== projectionHash(sharedProfile)) throw new HttpsError("failed-precondition", "review_current_profile");
      const expiresAtMs = Math.min(Date.now() + 7 * 86400000, Date.parse(listing.trip.startDate + "T00:00:00Z"));
      consume(); tripConsume();
      tx.create(ref, { applicantId: uid, ownerId: listing.trip.ownerId, tripId, introduction: intro, status: "pending", sharedProfile, compatibilitySnapshot: compatibility(listing.projection, p.data()!, input), snapshotAtMs: Date.now(), requestDates: { startDate: input.startDate ?? "", endDate: input.endDate ?? "" },
        createdAtMs: Date.now(), expiresAtMs, revision: 0, createdAt: nowField(), updatedAt: nowField() });
      activity(tx, listing.trip.ownerId, `${tripId}:${uid}:received`, "join_request_received", tripId);
      return { status: "pending" };
    });
  } else if (op === "requests") {
    const tripId = input.tripId ? id(input.tripId) : undefined;
    const trip = tripId ? await db.doc(`trips/${tripId}`).get() : undefined;
    if (tripId && trip?.get("ownerId") !== uid) throw unavailable();
    let query = tripId ? db.collection(`trips/${tripId}/joinRequests`).orderBy("createdAtMs") : db.collectionGroup("joinRequests").where("applicantId", "==", uid).orderBy("createdAtMs");
    if (input.cursor) query = query.startAfter(Number(input.cursor));
    const page = await query.limit(50).get(); const items = [];
    for (const r of page.docs) {
      const d = r.data(); const t = await db.doc(`trips/${id(d.tripId)}`).get();
      const owner = t.exists ? await db.doc(`users/${id(t.get("ownerId"))}`).get() : undefined;
      const isBlocked = t.exists ? await blocked(d.applicantId, t.get("ownerId"), undefined, d.createdAtMs) : false;
      const applicant = await db.doc(`users/${id(d.applicantId)}`).get();
      const listing = t.exists ? await db.doc(`communityListings/${d.tripId}`).get() : undefined;
      const closure = t.exists ? await db.doc(`communityClosures/${d.tripId}`).get() : undefined;
      const rawTrip = t.data();
      // A close with the owner's explicit “keep” choice retains pending
      // requests for a later reviewed reopening. Treat that closed listing as
      // temporarily open for lifecycle display only; approval still checks
      // the authoritative trip visibility in its transaction.
      const effectiveTrip = rawTrip && closure?.get("invalidate") === false && listing?.get("status") === "closed"
        ? { ...rawTrip, visibility: "open" }
        : rawTrip;
      const status = requestState(d, effectiveTrip, owner?.data(), isBlocked || !active(applicant.data()), Date.now());
      if (isBlocked) { if (!tripId) items.push({ tripId: d.tripId, status, applicantId: uid }); continue; }
      let fit: RecordData | null = null;
      if (status === "pending" && t.exists && listing?.exists) {
          try { const currentFit = compatibility(publicProjection(d.tripId, t.data()!, owner?.data() ?? {}, listing.get("input")), applicant.data() ?? {}, d.requestDates ?? {});
            fit = { label: currentFit.label, reasons: currentFit.reasons, missingFields: currentFit.missingFields };
          } catch { /* A withdrawn/changed projection is unavailable, never expose its internal fields. */ }
      }
      items.push({ tripId: d.tripId, applicantId: d.applicantId, status, introduction: status === "pending" ? d.introduction : "", sharedProfile: status === "pending" ? profileProjection(applicant.data() ?? {}) : null, fit, snapshotAtMs: d.snapshotAtMs ?? d.createdAtMs, createdAtMs: d.createdAtMs, expiresAtMs: d.expiresAtMs });
    }
    result = { items, cursor: page.size === 50 ? String(page.docs.at(-1)!.get("createdAtMs")) : "" };
  } else if (["approve", "decline", "withdraw"].includes(op)) {
    if (op === "approve") await gate(true);
    const tripId = id(input.tripId); const applicantId = op === "withdraw" ? uid : id(input.applicantId);
    result = await db.runTransaction(async tx => {
      const ref = db.doc(`trips/${tripId}/joinRequests/${applicantId}`); const r = await tx.get(ref);
      const tripRef = db.doc(`trips/${tripId}`); const t = await tx.get(tripRef);
      if (!r.exists || !t.exists || (op !== "withdraw" && t.get("ownerId") !== uid) || (op === "withdraw" && r.get("applicantId") !== uid)) throw unavailable();
      const owner = await tx.get(db.doc(`users/${id(t.get("ownerId"))}`));
      const applicant = await tx.get(db.doc(`users/${applicantId}`));
      const isBlocked = await blocked(applicantId, t.get("ownerId"), tx, r.get("createdAtMs"));
      const memberRef = db.doc(`trips/${tripId}/members/${applicantId}`); const member = await tx.get(memberRef);
      const members = await tx.get(db.collection(`trips/${tripId}/members`).where("status", "==", "active"));
      const lock = await tx.get(db.doc(`tripDeletionLocks/${tripId}`));
      const state = requestState(r.data()!, t.data(), owner.data(), isBlocked || !active(applicant.data()), Date.now());
      if (state !== "pending" || lock.exists) throw new HttpsError("failed-precondition", state);
      if (op === "approve") {
        await publicListing(tripId, uid, tx);
        if (applicantId === uid || member.get("status") === "active" || members.size !== t.get("activeMemberCount") || members.size >= t.get("capacity")) throw new HttpsError("failed-precondition", "capacity_changed");
      }
      const status = op === "approve" ? "approved" : op === "decline" ? "declined" : "withdrawn";
      tx.update(ref, { status, revision: r.get("revision") + 1, actorId: uid, updatedAt: nowField() });
      tx.set(db.doc(`communityActivity/${t.get("ownerId")}/items/${hash(`${tripId}:${applicantId}:received`)}`), { actionable: false, read: true }, { merge: true });
      if (op === "approve") {
        tx.set(memberRef, { userId: applicantId, displayName: applicant.get("displayName"), role: "viewer", status: "active", joinedAt: nowField(), createdAt: member.get("createdAt") ?? nowField(), updatedAt: nowField() });
        tx.update(tripRef, { activeMemberCount: members.size + 1, revision: t.get("revision") + 1, updatedAt: nowField() });
        tx.update(db.doc(`communityListings/${tripId}`), { tripRevision: t.get("revision") + 1 });
        if (members.size + 1 >= t.get("capacity")) activity(tx, t.get("ownerId"), `${tripId}:capacity`, "trip_reached_capacity", tripId, false);
      }
      activity(tx, applicantId, `${tripId}:${applicantId}:${status}`, `request_${status}`, tripId, status === "approved");
      return { status };
    });
  } else if (op === "block" || op === "unblock") {
    const target = input.tripId ? id((await db.doc(`trips/${id(input.tripId)}`).get()).get("ownerId")) : id(input.userId); if (target === uid) throw new HttpsError("invalid-argument", "invalid_target");
    if (op === "unblock" && input.confirmed !== "true") throw new HttpsError("failed-precondition", "confirmation_required");
    await db.runTransaction(async tx => {
      const ref = blockRef(uid, target); const previous = await tx.get(ref);
      if (op === "block") tx.set(ref, { active: true, createdAtMs: Date.now(), lastBlockedAt: nowField(), createdAt: nowField(), blockerId: uid, blockedId: target }); else if (previous.exists) tx.update(ref, { active: false, updatedAt: nowField() });
    });
    result = { status: op === "block" ? "blocked" : "unblocked", guidance: "If you share a trip, leave it or ask the owner/support to remove the other member. Shared itinerary access is preserved." };
  } else if (op === "blocks") {
    const page = await db.collection(`communityBlocks/${uid}/targets`).limit(100).get(); result = { items: page.docs.filter(d => d.get("active") !== false).map(d => ({ userId: d.id })) };
  } else if (op === "report") {
    if (!REPORT_CATEGORIES.includes(input.category) || !["user", "trip", "public_content"].includes(input.subjectType)) throw new HttpsError("invalid-argument", "invalid_report");
    const subjectId = id(input.subjectId); const key = id(input.idempotencyKey);
    const detail = input.detail ?? "";
    if (typeof detail !== "string" || detail.length > 2000 || /[\u0000-\u0008]/.test(detail)) throw new HttpsError("invalid-argument", "invalid_detail");
    const reference = hash(`${uid}:${key}`);
    await db.runTransaction(async tx => {
      const ref = db.doc(`reports/${reference}`); const previous = await tx.get(ref);
      if (previous.exists) return;
      const consume = await rateLimit(tx, uid, "report", 20); consume();
      tx.create(ref, { reporterId: uid, subjectType: input.subjectType, subjectId, category: input.category, detail, status: "received", revision: 0,
        createdAtMs: Date.now(), expiresAtMs: Date.now() + 180 * 86400000, createdAt: nowField(), updatedAt: nowField() });
      tx.create(ref.collection("audit").doc(), { actorId: uid, status: "received", policyBasis: input.category, createdAt: nowField(), reversible: true });
    }); result = { reference, status: "received" };
  } else if (op === "activity") {
    let query = db.collection(`communityActivity/${uid}/items`).orderBy("createdAtMs", "desc").limit(40);
    if (input.cursor) query = query.startAfter(Number(input.cursor));
    const page = await query.get(); result = { items: page.docs.filter(d => d.get("expiresAtMs") > Date.now()).map(d => ({ id: d.id, ...d.data() })), cursor: page.size === 40 ? String(page.docs.at(-1)!.get("createdAtMs")) : "" };
  } else if (op === "read") {
    const collection = db.collection(`communityActivity/${uid}/items`);
    const docs = input.activityId ? [await collection.doc(id(input.activityId)).get()] : (await collection.where("read", "==", false).limit(400).get()).docs;
    const batch = db.batch(); for (const doc of docs) if (doc.exists) batch.update(doc.ref, { read: true, updatedAt: nowField() }); await batch.commit(); result = { status: "read" };
  } else if (op === "register_installation" || op === "unregister_installation") {
    const installationId = id(input.installationId);
    const ref = db.doc(`communityInstallations/${uid}/devices/${installationId}`);
    if (op === "unregister_installation") await ref.delete();
    else {
      if (typeof input.token !== "string" || input.token.length < 16 || input.token.length > 4096 || !["android", "ios"].includes(input.platform)) throw new HttpsError("invalid-argument", "invalid_installation");
      // A token hash has one account owner, including after an account switch.
      await db.runTransaction(async tx => {
        const tokenRef = db.doc(`communityTokenOwners/${hash(input.token)}`); const previous = await tx.get(tokenRef);
        if (previous.exists && previous.get("path") !== ref.path) tx.delete(db.doc(previous.get("path")));
        tx.set(tokenRef, { path: ref.path, expiresAtMs: Date.now() + 30 * 86400000 });
        tx.set(ref, { token: input.token, platform: input.platform, expiresAtMs: Date.now() + 30 * 86400000, updatedAt: nowField() });
      });
    }
    result = { status: "updated" };
  } else if (op === "preferences") {
    const ref = db.doc(`communityPreferences/${uid}`);
    if (input.save === "true") {
      let timezone = input.timezone || "UTC";
      try { new Intl.DateTimeFormat("en", { timeZone: timezone }); } catch { throw new HttpsError("invalid-argument", "invalid_timezone"); }
      const start = Number(input.quietStart ?? 22), end = Number(input.quietEnd ?? 8);
      if (![start, end].every(v => Number.isInteger(v) && v >= 0 && v <= 23)) throw new HttpsError("invalid-argument", "invalid_quiet_hours");
      await ref.set({ push: input.push === "true", requests: input.requests !== "false", membership: input.membership !== "false", trip_changes: input.trip_changes !== "false", reminders: input.reminders === "true", timezone, quietStart: start, quietEnd: end, updatedAt: nowField() });
    }
    result = (await ref.get()).data() ?? { push: false, requests: true, membership: true, trip_changes: true, reminders: false, timezone: "UTC", quietStart: 22, quietEnd: 8 };
  } else throw new HttpsError("invalid-argument", "unknown_operation");
  if (op === "detail") (result as RecordData).profileHash = projectionHash(profileProjection(profile.data()!));
  return { json: JSON.stringify(result) };
});

/** Moderator custom claims are assigned only by an operator, never a writable profile flag. */
export const moderateCommunity = onCall({ region, enforceAppCheck: true }, async request => {
  if (!request.auth || request.auth.token.communityModerator !== true) throw unavailable();
  const uid = request.auth.uid; const input = request.data as RecordData;
  if (input.operation === "queue") {
    const [reports, listings] = await Promise.all([db.collection("reports").orderBy("createdAtMs").limit(50).get(), db.collection("communityListings").where("status", "==", "held").limit(50).get()]);
    await db.collection("moderationAudit").add({ actorId: uid, action: "queue_read", createdAt: nowField(), expiresAtMs: Date.now() + 365 * 86400000 });
    return { reports: reports.docs.map(d => ({ id: d.id, ...d.data() })), listings: listings.docs.map(d => ({ id: d.id, ...d.data() })) };
  }
  if (!safePublicText(input.policyBasis, 3, 200)) throw new HttpsError("invalid-argument", "policy_basis_required");
  const subjectId = id(input.subjectId);
  await db.runTransaction(async tx => {
    const ref = db.doc(input.operation === "suspend" ? `users/${subjectId}` : input.operation === "report_status" ? `reports/${subjectId}` : `communityListings/${subjectId}`);
    const subject = await tx.get(ref); if (!subject.exists) throw unavailable();
    if (input.operation === "publish_reviewed") {
      const tripRef = db.doc(`trips/${subjectId}`); const trip = await tx.get(tripRef);
      const owner = await tx.get(db.doc(`users/${id(trip.get("ownerId"))}`));
      if (subject.get("status") !== "held" || !eligibleTrip(trip.data(), owner.data(), new Date().toISOString().slice(0, 10)) || trip.get("revision") !== subject.get("tripRevision")) throw new HttpsError("failed-precondition", "preview_changed");
      const projection = publicProjection(subjectId, trip.data()!, owner.data()!, subject.get("input"));
      if (projectionHash(projection) !== subject.get("previewHash")) throw new HttpsError("failed-precondition", "preview_changed");
      tx.update(tripRef, { visibility: "open", revision: trip.get("revision") + 1, updatedAt: nowField() });
      tx.update(ref, { status: "published", tripRevision: trip.get("revision") + 1, updatedAt: nowField() });
    } else if (input.operation === "unpublish") {
      const tripRef = db.doc(`trips/${subjectId}`);
      const trip = await tx.get(tripRef);
      if (!trip.exists) throw unavailable();
      const revision = trip.get("revision");
      if (!Number.isInteger(revision)) throw new HttpsError("failed-precondition", "trip_state_invalid");
      tx.update(tripRef, { visibility: "private", revision: revision + 1, updatedAt: nowField() });
      tx.set(db.doc(`communityClosures/${subjectId}`), { invalidate: true, moderatedAt: nowField() }, { merge: true });
      tx.update(ref, { status: "moderated", updatedAt: nowField() });
    }
    else if (input.operation === "suspend") tx.update(ref, { accountStatus: "suspended", updatedAt: nowField() });
    else if (input.operation === "report_status") {
      const transitions: Record<string, string[]> = { received: ["triaged"], triaged: ["investigating", "no_action"], investigating: ["actioned", "no_action"], actioned: ["appealed", "closed"], no_action: ["appealed", "closed"], appealed: ["investigating", "closed"] };
      if (!transitions[subject.get("status")]?.includes(input.status)) throw new HttpsError("failed-precondition", "invalid_transition");
      tx.update(ref, { status: input.status, revision: subject.get("revision") + 1, updatedAt: nowField() });
    } else throw new HttpsError("invalid-argument", "invalid_action");
    const audit = { actorId: uid, action: input.operation, subjectId, policyBasis: input.policyBasis, status: input.status ?? null, reversible: true, createdAt: nowField(), expiresAtMs: Date.now() + 365 * 86400000 };
    tx.create(db.collection("moderationAudit").doc(), audit);
    if (input.operation === "report_status") tx.create(ref.collection("audit").doc(), audit);
  }); return { status: "updated" };
});

async function reconcileRequests(tripId: string) {
  const page = await db.collection(`trips/${tripId}/joinRequests`).where("status", "==", "pending").limit(400).get();
  for (const doc of page.docs) await db.runTransaction(async tx => {
    const r = await tx.get(doc.ref); if (r.get("status") !== "pending") return;
    const t = await tx.get(db.doc(`trips/${tripId}`));
    const owner = t.exists ? await tx.get(db.doc(`users/${id(t.get("ownerId"))}`)) : undefined;
    const applicant = await tx.get(db.doc(`users/${id(r.get("applicantId"))}`));
    const listing = await tx.get(db.doc(`communityListings/${tripId}`));
    const isBlocked = t.exists ? await blocked(r.get("applicantId"), t.get("ownerId"), tx, r.get("createdAtMs")) : false;
    const closure = await tx.get(db.doc(`communityClosures/${tripId}`));
    const trip = t.data();
    // Closing with Keep retains pending requests, but cannot permit approval.
    const effectiveTrip = trip && closure.get("invalidate") === false && listing.get("status") === "closed" ? { ...trip, visibility: "open" } : trip;
    let status = requestState(r.data()!, effectiveTrip, owner?.data(), isBlocked || !active(applicant.data()), Date.now());
    if (listing.get("status") === "moderated") status = "invalidated";
    if (status === "pending") return;
    tx.update(doc.ref, { status, revision: r.get("revision") + 1, actorId: "system", updatedAt: nowField() });
    activity(tx, r.get("applicantId"), `${tripId}:${doc.id}:${status}`, `request_${status}`, tripId, false);
  });
}
export const communityTripChanged = onDocumentWritten({ region, document: "trips/{tripId}" }, async event => {
  if (!event.data) return;
  const before = event.data.before.data(), after = event.data.after.data(); const tripId = event.params.tripId;
  const material = !after || (before && ["destination", "startDate", "endDate", "status"].some(k => before[k] !== after[k]));
  if (material && before) {
    const members = await db.collection(`trips/${tripId}/members`).where("status", "==", "active").get();
    for (const member of members.docs) await writeActivityOnce(member.id, event.id, after?.status === "cancelled" ? "trip_cancelled" : "trip_changed", tripId, true);
    const applicants = await db.collection(`trips/${tripId}/joinRequests`).where("status", "==", "pending").get();
    for (const applicant of applicants.docs) await writeActivityOnce(applicant.id, event.id, "trip_changed", tripId, false);
  }
  await reconcileRequests(tripId);
});
async function writeActivityOnce(uid: string, eventId: string, type: string, tripId: string, actionable: boolean) {
  await db.runTransaction(async tx => {
    const ref = db.doc(`communityActivity/${uid}/items/${hash(eventId)}`);
    const old = await tx.get(ref); if (old.exists) return;
    activity(tx, uid, eventId, type, tripId, actionable);
  });
}
export const communityMemberChanged = onDocumentWritten({ region, document: "trips/{tripId}/members/{userId}" }, async event => {
  if (!event.data) return;
  const before = event.data.before.data(), after = event.data.after.data(); const { tripId, userId } = event.params;
  const trip = await db.doc(`trips/${tripId}`).get();
  if (before && (!after || after.status === "removed" || after.role !== before.role)) await writeActivityOnce(userId, event.id, after?.status === "active" ? "role_changed" : "membership_removed", tripId, true);
  if (after?.status === "active" && before?.status !== "active" && after.inviteId && trip.exists) await writeActivityOnce(trip.get("ownerId"), event.id, "invitation_accepted", tripId, false);
});
export const communityItineraryChanged = onDocumentWritten({ region, document: "trips/{tripId}/itinerary/{itemId}" }, async event => {
  if (!event.data?.after.exists && !event.data?.before.exists) return;
  const members = await db.collection(`trips/${event.params.tripId}/members`).where("status", "==", "active").get();
  // One activity-only digest per trip/day. No individual itinerary push.
  const key = `${event.params.tripId}:itinerary:${event.time.slice(0, 10)}`;
  for (const member of members.docs) await writeActivityOnce(member.id, key, "itinerary_digest", event.params.tripId, false);
});
export const communityBlockChanged = onDocumentWritten({ region, document: "communityBlocks/{uid}/targets/{target}" }, async event => {
  if (!event.data?.after.exists || event.data.after.get("active") === false) return;
  // Preserve invalidation across unblock and revoke pre-existing invite tokens.
  for (const [ownerId, applicantId] of [[event.params.uid, event.params.target], [event.params.target, event.params.uid]]) {
    const trips = await db.collection("trips").where("ownerId", "==", ownerId).get();
    for (const trip of trips.docs) {
      await db.runTransaction(async tx => {
        const ref = trip.ref.collection("joinRequests").doc(applicantId); const r = await tx.get(ref);
        const invites = await tx.get(trip.ref.collection("invites"));
        if (r.get("status") === "pending") {
          tx.update(ref, { status: "invalidated", revision: r.get("revision") + 1, actorId: "system", updatedAt: nowField() });
          activity(tx, applicantId, `${trip.id}:${applicantId}:invalidated`, "request_invalidated", trip.id, false);
        }
        for (const invite of invites.docs) tx.update(invite.ref, { revokedAt: nowField(), updatedAt: nowField() });
      });
    }
  }
});
export const communityRetention = onSchedule({ region, schedule: "every 60 minutes" }, async () => {
  const pending = await db.collectionGroup("joinRequests").where("status", "==", "pending").limit(400).get();
  for (const tripId of new Set(pending.docs.map(d => d.ref.parent.parent!.id))) await reconcileRequests(tripId);
  for (const collection of ["communityLimits", "communityPushClaims", "communityTokenOwners", "moderationAudit", "reports"]) {
    const page = await db.collection(collection).where("expiresAtMs", "<=", Date.now()).limit(100).get();
    for (const doc of page.docs) if (doc.get("legalHold") !== true) await db.recursiveDelete(doc.ref);
  }
  const expiredInstallations = await db.collectionGroup("devices").where("expiresAtMs", "<=", Date.now()).limit(400).get();
  for (const device of expiredInstallations.docs) {
    if (device.ref.path.startsWith("communityInstallations/")) await device.ref.delete();
  }
  const oldRequests = await db.collectionGroup("joinRequests").where("expiresAtMs", "<=", Date.now() - 30 * 86400000).limit(200).get();
  for (const r of oldRequests.docs) if (r.get("status") !== "pending") await r.ref.delete();
  const items = await db.collectionGroup("items").where("expiresAtMs", "<=", Date.now()).limit(400).get();
  const batch = db.batch(); for (const doc of items.docs) if (doc.ref.path.startsWith("communityActivity/")) batch.delete(doc.ref); await batch.commit();
});

export async function cleanupCommunityAccount(uid: string) {
  const devices = await db.collection(`communityInstallations/${uid}/devices`).get();
  const tokenOwners = await Promise.all(devices.docs.map((device) => db.doc(`communityTokenOwners/${hash(device.get("token"))}`).get()));
  await Promise.all(tokenOwners.filter((owner) => owner.exists && String(owner.get("path") ?? "").startsWith(`communityInstallations/${uid}/`)).map((owner) => owner.ref.delete()));
  await db.recursiveDelete(db.doc(`communityActivity/${uid}`));
  await db.recursiveDelete(db.doc(`communityBlocks/${uid}`));
  await db.doc(`communityPreferences/${uid}`).delete();
  await db.recursiveDelete(db.doc(`communityInstallations/${uid}`));
  const incomingBlocks = await db.collectionGroup("targets").where("blockedId", "==", uid).get();
  await Promise.all(incomingBlocks.docs.map((block) => block.ref.delete()));
  const requests = await db.collectionGroup("joinRequests").where("applicantId", "==", uid).get();
  for (const r of requests.docs) await r.ref.delete();
  // Safety reports/audit records retain restricted evidence for the documented TTL.
}

export const communityPushCreated = onDocumentCreated({ region, document: "communityActivity/{uid}/items/{activityId}" }, async event => {
  if (!event.data) return;
  const { uid, activityId } = event.params;
  if (process.env.FIRESTORE_EMULATOR_HOST) return; // Never contact live FCM from tests.
  const template = await getRemoteConfig().getTemplate();
  if (!isRemoteConfigBooleanEnabled(template, "push_enabled")) return;
  const preferences = (await db.doc(`communityPreferences/${uid}`).get()).data() ?? {};
  const type = event.data.get("type");
  if (!pushAllowed(preferences, type, new Date()) || event.data.get("read") === true) return;
  const devices = await db.collection(`communityInstallations/${uid}/devices`).get();
  for (const device of devices.docs) {
    if (device.get("expiresAtMs") <= Date.now()) continue;
    const claim = db.doc(`communityPushClaims/${hash(event.id + device.id)}`);
    const send = await db.runTransaction(async tx => {
      const previous = await tx.get(claim); const current = await tx.get(device.ref);
      const owner = current.exists ? await tx.get(db.doc(`communityTokenOwners/${hash(current.get("token"))}`)) : undefined;
      if (previous.exists || !current.exists || owner?.get("path") !== device.ref.path) return false;
      // Claim before send: duplicate triggers never deliver another push. Activity remains authoritative if the provider fails.
      tx.create(claim, { createdAtMs: Date.now(), expiresAtMs: Date.now() + 7 * 86400000 }); return true;
    });
    if (!send) continue;
    try {
      await getMessaging().send({ token: device.get("token"), notification: { title: "TripTandem", body: "A TripTandem update needs your attention." },
        data: { community_activity: activityId },
        android: { collapseKey: pushCategory(type), notification: { tag: `triptandem-${pushCategory(type)}` } },
        apns: { headers: { "apns-collapse-id": `triptandem-${pushCategory(type)}` }, payload: { aps: { sound: "default" } } } });
    } catch (error: any) {
      if (["messaging/registration-token-not-registered", "messaging/invalid-registration-token"].includes(error?.code)) await device.ref.delete();
      // Never log token or provider response. In-app activity is already persisted.
    }
  }
});
