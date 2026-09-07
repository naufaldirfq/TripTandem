import { cleanupCommunityAccount } from "./community";
import { createHash, timingSafeEqual } from "node:crypto";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { onCall, onRequest, HttpsError } from "firebase-functions/v2/https";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { defineSecret } from "firebase-functions/params";
import { setGlobalOptions } from "firebase-functions/v2";
import { getApps, initializeApp } from "firebase-admin/app";
import { DocumentReference, DocumentSnapshot, FieldValue, Timestamp, getFirestore } from "firebase-admin/firestore";
import { getRemoteConfig } from "firebase-admin/remote-config";
import {
  GENERATION_SCHEMA_VERSION,
  GenerationInput,
  GenerationPreview,
  LockedGenerationWindow,
  applyGeneratedItemEdits,
  boundedNonNegativeInteger,
  classifyGeminiResponse,
  hasGenerationAccess,
  hasUnsafeGenerationIntent,
  hasItineraryRevisionConflict,
  isVersionedGenerationResponse,
  isValidIsoDate,
  normalizeGenerationInput,
  validateGeneratedItems,
} from "./generation";
import {
  ACTIVE_TRIP_STATUSES,
  canCreateActiveTrip,
  canUseTripCapacity,
  canUpdateTripCapacity,
  normalizeTripCreateInput,
} from "./trips";
import {
  REVENUECAT_PRO_ENTITLEMENT,
  REVENUECAT_WEBHOOK_EVENT_TYPES,
  isSafeAppUserId,
  isStaleWebhook,
  parseWebhookMillis,
  parseTransferAppUserIds,
  resolveWebhookActive,
  resolveTransferActive,
  targetsEntitlement,
  verifyRevenueCatWebhookSignature,
} from "./revenuecat";
import { isRemoteConfigBooleanEnabled } from "./remoteconfig";

setGlobalOptions({ region: "asia-southeast2", maxInstances: 10 });
if (getApps().length === 0) initializeApp();
const db = getFirestore();

const geminiApiKey = defineSecret("GEMINI_API_KEY");
const revenueCatWebhookSecret = defineSecret("REVENUECAT_WEBHOOK_SECRET");
const MAX_FREE_GENERATIONS = 1;
const MAX_PRO_GENERATIONS_PER_MONTH = 30;
const JOB_RETENTION_DAYS = 7;
const ACTIVE_JOB_STATES = ["queued", "running"];
const GENERATION_LOCKS_COLLECTION = "generationLocks";
const TRIP_DELETION_LOCKS_COLLECTION = "tripDeletionLocks";
const AI_GENERATION_ENABLED_KEY = "ai_generation_enabled";
const REMOTE_CONFIG_CACHE_MS = 30_000;
const EXPIRABLE_GENERATION_STATES = new Set([
  "queued",
  "running",
  "succeeded",
  "partially_succeeded",
]);
const PUBLIC_GENERATION_STATES = new Set([
  ...EXPIRABLE_GENERATION_STATES,
  "failed",
  "cancelled",
  "expired",
  "applied",
]);
const PUBLIC_GENERATION_FAILURE_CLASSES = new Set([
  "quota_exceeded",
  "provider_unconfigured",
  "feature_disabled",
  "provider_rate_limited",
  "provider_timeout",
  "no_valid_suggestions",
  "invalid_job_input",
  "access_revoked",
  "safety_blocked",
  "invalid_provider_output",
  "provider_error",
  "cancelled",
  "expired",
]);
// Firestore write batches accept at most 500 operations. Keep a small margin
// for future cleanup additions so account deletion remains one bounded helper.
const ACCOUNT_CLEANUP_BATCH_SIZE = 450;
const ACCOUNT_TRIP_SUBCOLLECTIONS = ["members", "itinerary", "invites", "joinRequests"] as const;
const VALID_CLOCK_LABEL = /^(?:[01]\d|2[0-3]):[0-5]\d$/;
const VALID_FLEXIBLE_LABEL = /^(morning|late morning|afternoon|evening|night|anytime)$/i;

let aiGenerationFlagCache: { enabled: boolean; expiresAt: number } | undefined;
let aiGenerationFlagRequest: Promise<boolean> | undefined;

type CallableRequest = { auth?: { uid: string } | null; data?: unknown };

/**
 * Creates a trip through a trusted transaction. The previous client-only
 * active-trip count could be bypassed by writing directly to Firestore; this
 * callable reads every owner trip in the same transaction and grants the
 * second active trip only when the verified RevenueCat webhook state is Pro.
 */
export const createTrip = onCall(async (request) => {
  const uid = requireAuth(request);
  const normalized = normalizeTripCreateInput(request.data);
  if (!normalized.ok) {
    throw new HttpsError("invalid-argument", `Invalid trip field: ${normalized.field}`);
  }

  const input = normalized.value;
  const profileRef = db.doc(`users/${uid}`);
  const billingRef = db.doc(`users/${uid}/billing/current`);
  const ownerTripsQuery = db.collection("trips").where("ownerId", "==", uid);
  const tripRef = db.collection("trips").doc();
  const memberRef = tripRef.collection("members").doc(uid);

  await db.runTransaction(async (transaction) => {
    // All reads intentionally happen before the first write. Reading the
    // owner-trip query in the transaction makes concurrent create attempts
    // conflict and retry instead of both bypassing the free-plan limit.
    const profileSnapshot = await transaction.get(profileRef);
    const billingSnapshot = await transaction.get(billingRef);
    const ownerTrips = await transaction.get(ownerTripsQuery);
    if (!isActiveProfileSnapshot(profileSnapshot)) {
      throw new HttpsError("failed-precondition", "profile_required");
    }

    const activeTripCount = ownerTrips.docs.filter((trip) =>
      ACTIVE_TRIP_STATUSES.includes(String(trip.get("status") ?? "") as (typeof ACTIVE_TRIP_STATUSES)[number]),
    ).length;
    const isPro = verifiedProBilling(billingSnapshot);
    if (!canUseTripCapacity(input.capacity, isPro)) {
      throw new HttpsError("failed-precondition", "organizer_pro_required");
    }
    if (ACTIVE_TRIP_STATUSES.includes(input.status as (typeof ACTIVE_TRIP_STATUSES)[number])
      && !canCreateActiveTrip(activeTripCount, isPro)) {
      throw new HttpsError("failed-precondition", "organizer_pro_required");
    }

    const now = FieldValue.serverTimestamp();
    transaction.create(tripRef, {
      ownerId: uid,
      title: input.title,
      destination: input.destination,
      startDate: input.startDate,
      endDate: input.endDate,
      destinationTimezone: input.destinationTimezone,
      datesFlexible: input.datesFlexible,
      visibility: input.visibility,
      capacity: input.capacity,
      status: input.status,
      ...(input.currency === undefined ? {} : { currency: input.currency }),
      ...(input.budgetBand === undefined ? {} : { budgetBand: input.budgetBand }),
      ...(input.pace === undefined ? {} : { pace: input.pace }),
      ...(input.expectationNote === undefined ? {} : { expectationNote: input.expectationNote }),
      interests: input.interests,
      ...(input.coverColor === undefined ? {} : { coverColor: input.coverColor }),
      activeMemberCount: 1,
      revision: 0,
      createdAt: now,
      updatedAt: now,
    });
    transaction.create(memberRef, {
      userId: uid,
      displayName: String(profileSnapshot.get("displayName") ?? "You"),
      role: "owner",
      status: "active",
      joinedAt: now,
      createdAt: now,
      updatedAt: now,
    });
  });

  return { tripId: tripRef.id };
});

/** Updates trip fields through a trusted transaction when a gated field changes. */
export const updateTrip = onCall(async (request) => {
  const uid = requireAuth(request);
  const data = asRecord(request.data);
  const tripId = requiredDocumentId(data.tripId, "tripId", 128);
  const expectedRevision = data.expectedRevision;
  if (typeof expectedRevision !== "number" || !Number.isInteger(expectedRevision) || expectedRevision < 0) {
    throw new HttpsError("invalid-argument", "expectedRevision is invalid");
  }
  // Editing an open trip closes its projection until a fresh preview is reviewed.
  const tripInput = asRecord(data.trip);
  const normalized = normalizeTripCreateInput({ ...tripInput, visibility: tripInput.visibility === "open" ? "private" : tripInput.visibility });
  if (!normalized.ok) {
    throw new HttpsError("invalid-argument", `Invalid trip field: ${normalized.field}`);
  }
  const input = normalized.value;
  const tripRef = db.doc(`trips/${tripId}`);
  const memberRef = tripRef.collection("members").doc(uid);
  const profileRef = db.doc(`users/${uid}`);
  const billingRef = db.doc(`users/${uid}/billing/current`);
  const deletionLockRef = db.collection(TRIP_DELETION_LOCKS_COLLECTION).doc(tripId);

  await db.runTransaction(async (transaction) => {
    // Reads are intentionally completed before the update so revision and
    // entitlement checks participate in the same optimistic transaction.
    const profileSnapshot = await transaction.get(profileRef);
    const tripSnapshot = await transaction.get(tripRef);
    const memberSnapshot = await transaction.get(memberRef);
    const billingSnapshot = await transaction.get(billingRef);
    const deletionLockSnapshot = await transaction.get(deletionLockRef);
    const ownerTrips = await transaction.get(db.collection("trips").where("ownerId", "==", uid));
    if (!isActiveProfileSnapshot(profileSnapshot)) {
      throw new HttpsError("failed-precondition", "profile_required");
    }
    if (!tripSnapshot.exists) throw new HttpsError("not-found", "Trip not found");
    if (deletionLockSnapshot.exists) {
      throw new HttpsError("failed-precondition", "trip_deletion_in_progress");
    }
    const current = tripSnapshot.data()!;
    const isOwner = current.ownerId === uid;
    const memberRole = memberSnapshot.exists && memberSnapshot.get("status") === "active"
      ? String(memberSnapshot.get("role") ?? "")
      : "";
    if (!isOwner && memberRole !== "editor") {
      throw new HttpsError("permission-denied", "Only the organizer or an editor can update this trip");
    }
    const currentRevision = Number(current.revision ?? 0);
    if (currentRevision !== expectedRevision) {
      throw new HttpsError("aborted", "Trip changed while it was open");
    }
    if (!isOwner && (
      input.visibility !== current.visibility
      || input.capacity !== Number(current.capacity)
      || input.status !== current.status
    )) {
      throw new HttpsError("permission-denied", "Only the organizer can change trip access, capacity, or status");
    }
    const wasActive = ACTIVE_TRIP_STATUSES.includes(String(current.status ?? "") as (typeof ACTIVE_TRIP_STATUSES)[number]);
    const becomesActive = ACTIVE_TRIP_STATUSES.includes(input.status as (typeof ACTIVE_TRIP_STATUSES)[number]);
    const isPro = verifiedProBilling(billingSnapshot);
    if (!canUpdateTripCapacity(Number(current.capacity), input.capacity, isPro, becomesActive && !wasActive)) {
      throw new HttpsError("failed-precondition", "organizer_pro_required");
    }
    if (becomesActive && !wasActive) {
      const activeTripCount = ownerTrips.docs.filter((trip) =>
        trip.id !== tripId
          && ACTIVE_TRIP_STATUSES.includes(String(trip.get("status") ?? "") as (typeof ACTIVE_TRIP_STATUSES)[number]),
      ).length;
      if (!canCreateActiveTrip(activeTripCount, isPro)) {
        throw new HttpsError("failed-precondition", "organizer_pro_required");
      }
    }

    transaction.update(tripRef, {
      title: input.title,
      destination: input.destination,
      startDate: input.startDate,
      endDate: input.endDate,
      destinationTimezone: input.destinationTimezone,
      datesFlexible: input.datesFlexible,
      visibility: input.visibility,
      capacity: input.capacity,
      status: input.status,
      currency: input.currency ?? FieldValue.delete(),
      budgetBand: input.budgetBand ?? FieldValue.delete(),
      pace: input.pace ?? FieldValue.delete(),
      expectationNote: input.expectationNote ?? FieldValue.delete(),
      interests: input.interests,
      coverColor: input.coverColor ?? FieldValue.delete(),
      revision: currentRevision + 1,
      updatedAt: FieldValue.serverTimestamp(),
    });
  });

  return { tripId };
});

/**
 * Deletes an organizer-owned trip through a server-owned graph cleanup. A
 * Firestore parent delete does not cascade into subcollections, so direct
 * client deletes are denied by Rules and this callable first takes a
 * short-lived deletion lock, makes the trip read-only, drains every known
 * subcollection, and only then removes the root. Retries are idempotent.
 */
export const deleteTrip = onCall(async (request) => {
  const uid = requireAuth(request);
  const tripId = requiredDocumentId(asRecord(request.data).tripId, "tripId", 128);
  const tripRef = db.doc(`trips/${tripId}`);
  const state = await lockTripForDeletion(uid, tripRef);
  if (state === "missing") return { tripId, deleted: false, deletedDocumentCount: 0 };

  const deletedSubcollectionDocumentCount = await deleteTripSubcollections(tripRef);
  const deletedServerOwnedDocumentCount = await deleteTripServerOwnedData(tripId);
  const finalized = await finalizeTripDeletion(uid, tripRef);
  return {
    tripId,
    deleted: finalized,
    deletedDocumentCount: finalized
      ? deletedSubcollectionDocumentCount + deletedServerOwnedDocumentCount + 1
      : deletedSubcollectionDocumentCount + deletedServerOwnedDocumentCount,
  };
});

/**
 * Removes a member and decrements the trip aggregate in one trusted
 * transaction. A client cannot safely update an arbitrary member document and
 * its parent counter with Firestore Rules alone because the parent rule cannot
 * inspect a wildcard member path. Keeping this mutation server-side prevents
 * an owner from forging the aggregate count (or removing someone without the
 * corresponding membership change) while preserving the PRD 05 owner flow.
 */
export const removeTripMember = onCall(async (request) => {
  const uid = requireAuth(request);
  const data = asRecord(request.data);
  const tripId = requiredDocumentId(data.tripId, "tripId", 128);
  const memberId = requiredDocumentId(data.userId, "userId", 128);
  if (uid === memberId) {
    throw new HttpsError("failed-precondition", "The organizer cannot remove themselves");
  }

  const profileRef = db.doc(`users/${uid}`);
  const tripRef = db.doc(`trips/${tripId}`);
  const ownerMemberRef = tripRef.collection("members").doc(uid);
  const memberRef = tripRef.collection("members").doc(memberId);
  const deletionLockRef = db.collection(TRIP_DELETION_LOCKS_COLLECTION).doc(tripId);

  await db.runTransaction(async (transaction) => {
    const [profileSnapshot, tripSnapshot, ownerMemberSnapshot, memberSnapshot, deletionLockSnapshot] = await Promise.all([
      transaction.get(profileRef),
      transaction.get(tripRef),
      transaction.get(ownerMemberRef),
      transaction.get(memberRef),
      transaction.get(deletionLockRef),
    ]);
    if (!isActiveProfileSnapshot(profileSnapshot)) {
      throw new HttpsError("failed-precondition", "profile_required");
    }
    if (!tripSnapshot.exists) throw new HttpsError("not-found", "Trip not found");
    if (deletionLockSnapshot.exists) {
      throw new HttpsError("failed-precondition", "trip_deletion_in_progress");
    }
    // Cancelled, completed, and archived trips are read-only records. Keep
    // the trusted member-removal boundary consistent with the child-write
    // rules and the shared UI; an owner must not mutate membership after the
    // trip has been closed.
    const tripStatus = String(tripSnapshot.get("status") ?? "");
    if (!ACTIVE_TRIP_STATUSES.includes(tripStatus as (typeof ACTIVE_TRIP_STATUSES)[number])) {
      throw new HttpsError("failed-precondition", "trip_read_only");
    }
    if (tripSnapshot.get("ownerId") !== uid
      || !ownerMemberSnapshot.exists
      || ownerMemberSnapshot.get("userId") !== uid
      || ownerMemberSnapshot.get("role") !== "owner"
      || ownerMemberSnapshot.get("status") !== "active") {
      throw new HttpsError("permission-denied", "Only the organizer can remove a member");
    }
    if (!memberSnapshot.exists) throw new HttpsError("not-found", "Member not found");
    if (memberSnapshot.get("userId") !== memberId || memberSnapshot.get("role") === "owner") {
      throw new HttpsError("permission-denied", "The organizer cannot remove this member");
    }

    const currentStatus = memberSnapshot.get("status");
    if (currentStatus === "active") {
      const activeMemberCount = tripSnapshot.get("activeMemberCount");
      const revision = tripSnapshot.get("revision");
      if (typeof activeMemberCount !== "number" || !Number.isInteger(activeMemberCount) || activeMemberCount <= 1
        || typeof revision !== "number" || !Number.isInteger(revision) || revision < 0) {
        throw new HttpsError("failed-precondition", "Trip membership state is invalid");
      }
      transaction.update(tripRef, {
        activeMemberCount: activeMemberCount - 1,
        revision: revision + 1,
        updatedAt: FieldValue.serverTimestamp(),
      });
    } else if (currentStatus !== "invited" && currentStatus !== "removed") {
      throw new HttpsError("failed-precondition", "Member status is invalid");
    }
    transaction.update(memberRef, {
      status: "removed",
      updatedAt: FieldValue.serverTimestamp(),
    });
  });

  return { tripId, userId: memberId, status: "removed" };
});

/**
 * Removes server-owned account metadata before the client revokes Firebase
 * Auth. Generation jobs are requester-scoped and can contain temporary
 * planning parameters or sanitized previews; generation locks and billing
 * documents are server-owned coordination/entitlement state. None of these
 * collections is readable by mobile clients, so account deletion must use this
 * authenticated server boundary.
 *
 * The operation is intentionally idempotent. Deleting an already-clean
 * account returns zero counts, and a client can retry after a transient
 * network failure without recreating or exposing any data.
 */
export const cleanupAccountData = onCall(async (request) => {
  const uid = requireAuth(request);
  return cleanupServerOwnedData(uid);
});

/**
 * Completes account deletion through a trusted boundary. Profile documents
 * are intentionally not client-deletable: deleting one directly could leave
 * server-owned billing/generation state behind or strand collaborators on an
 * active shared trip. The callable rechecks ownership on the server, cleans
 * private coordination data, removes the owned trip graph, and deletes the
 * profile in a transaction. Trip roots are not deleted from a client because
 * Firestore parent deletion does not cascade into subcollections.
 */
export const deleteAccountProfile = onCall(async (request) => {
  const uid = requireAuth(request);
  await assertNoActiveSharedOwnedTrips(uid);
  const cleanup = await cleanupServerOwnedData(uid);
  const tripCleanup = await cleanupOwnedTripData(uid);
  await cleanupCommunityAccount(uid);
  const profileRef = db.doc(`users/${uid}`);
  const ownerTripsQuery = db.collection("trips").where("ownerId", "==", uid);

  const deleted = await db.runTransaction(async (transaction) => {
    // Recheck after cleanup so a trip created while the request was in flight
    // cannot turn this into an orphaned trip graph or active collaboration.
    const [profileSnapshot, ownerTrips] = await Promise.all([
      transaction.get(profileRef),
      transaction.get(ownerTripsQuery),
    ]);
    if (await hasActiveSharedOwnedTrips(ownerTrips.docs, (tripRef) =>
      transaction.get(tripRef.collection("members").where("status", "==", "active").limit(2))
        .then((snapshot) => snapshot.docs),
    )) {
      throw new HttpsError("failed-precondition", "ownership_required");
    }
    if (!ownerTrips.empty) {
      // The cleanup query can race a newly-created inactive trip. Fail closed
      // instead of deleting the profile while that trip still points at it;
      // retrying the callable will clean the newly observed graph.
      throw new HttpsError("failed-precondition", "account_cleanup_incomplete");
    }
    if (!profileSnapshot.exists) return false;
    transaction.delete(profileRef);
    return true;
  });

  return { ...cleanup, ...tripCleanup, deleted };
});

/** Creates a queued job. No model call happens in the client request. */
export const createItineraryGenerationJob = onCall(async (request) => {
  const uid = requireAuth(request);
  if (!(await isAiGenerationEnabled())) {
    throw new HttpsError("failed-precondition", "ai_generation_disabled");
  }
  const data = asRecord(request.data);
  const tripId = requiredDocumentId(data.tripId, "tripId", 128);
  const normalized = normalizeGenerationInput(data.input);
  if (!normalized.ok) throw new HttpsError("invalid-argument", `Invalid generation field: ${normalized.field}`);
  if (hasUnsafeGenerationIntent(normalized.value)) {
    throw new HttpsError("invalid-argument", "This request isn't available for itinerary planning.");
  }

  const tripRef = db.doc(`trips/${tripId}`);
  const memberRef = tripRef.collection("members").doc(uid);
  const [tripSnapshot, memberSnapshot] = await Promise.all([tripRef.get(), memberRef.get()]);
  if (!tripSnapshot.exists) throw new HttpsError("not-found", "Trip not found");
  assertCanEditTrip(uid, tripSnapshot.data(), memberSnapshot.data());
  const trip = tripSnapshot.data()!;
  if (!ACTIVE_TRIP_STATUSES.includes(String(trip.status ?? "") as (typeof ACTIVE_TRIP_STATUSES)[number])) {
    throw new HttpsError("failed-precondition", "This trip is read-only");
  }
  if (normalized.value.scope === "single_day" &&
      !isDateInTrip(normalized.value.dayDate!, String(trip.startDate), String(trip.endDate))) {
    throw new HttpsError("invalid-argument", "dayDate must be within the trip dates");
  }

  const activeJobsQuery = db.collection("generationJobs")
    .where("requesterId", "==", uid)
    .where("state", "in", ACTIVE_JOB_STATES);
  const activeJobs = await activeJobsQuery.get();
  if (activeJobs.docs.some((job) => job.get("tripId") === tripId)) {
    throw new HttpsError("already-exists", "A generation is already running for this trip");
  }

  // Itinerary positions allow 0–500 inclusive, so read the complete bounded
  // set when taking a concurrency snapshot.
  const itinerarySnapshot = await tripRef.collection("itinerary").limit(1000).get();
  const existingItemRevisions: Record<string, number> = {};
  itinerarySnapshot.docs.forEach((item) => {
    existingItemRevisions[item.id] = Number(item.get("revision") ?? 0);
  });
  const jobRef = db.collection("generationJobs").doc();
  const generationLockRef = db.collection(GENERATION_LOCKS_COLLECTION).doc(generationLockId(uid, tripId));
  const deletionLockRef = db.collection(TRIP_DELETION_LOCKS_COLLECTION).doc(tripId);
  const profileRef = db.doc(`users/${uid}`);
  const expiresAt = Timestamp.fromMillis(Date.now() + JOB_RETENTION_DAYS * 24 * 60 * 60 * 1000);
  const reservation = await reserveGenerationSlot(uid);
  if (!reservation.allowed) throw new HttpsError("resource-exhausted", "Your generation allowance is used.");
  try {
    await db.runTransaction(async (transaction) => {
      // The query is retained inside the transaction for compatibility with
      // jobs created before the lock document existed. The deterministic lock
      // closes the race between otherwise-identical new requests.
      const [locked, activeJobsInTransaction, profileSnapshot, currentTripSnapshot, currentMemberSnapshot, deletionLockSnapshot] = await Promise.all([
        transaction.get(generationLockRef),
        transaction.get(activeJobsQuery),
        transaction.get(profileRef),
        transaction.get(tripRef),
        transaction.get(memberRef),
        transaction.get(deletionLockRef),
      ]);
      if (!isActiveProfileSnapshot(profileSnapshot)) {
        throw new HttpsError("failed-precondition", "profile_required");
      }
      if (!currentTripSnapshot.exists) throw new HttpsError("not-found", "Trip not found");
      if (deletionLockSnapshot.exists) {
        throw new HttpsError("failed-precondition", "trip_deletion_in_progress");
      }
      const currentTrip = currentTripSnapshot.data()!;
      assertCanEditTrip(uid, currentTrip, currentMemberSnapshot.data());
      if (!ACTIVE_TRIP_STATUSES.includes(String(currentTrip.status ?? "") as (typeof ACTIVE_TRIP_STATUSES)[number])) {
        throw new HttpsError("failed-precondition", "This trip is read-only");
      }
      const existingJobId = locked.exists && typeof locked.get("jobId") === "string"
        ? locked.get("jobId") as string
        : null;
      const existingJob = existingJobId
        ? await transaction.get(db.doc(`generationJobs/${existingJobId}`))
        : null;
      const existingJobState = existingJob?.exists ? String(existingJob.get("state") ?? "") : "";
      const existingJobExpiry = existingJob?.exists ? existingJob.get("expiresAt") : null;
      const existingLockIsActive = ACTIVE_JOB_STATES.includes(existingJobState)
        && existingJobExpiry instanceof Timestamp
        && existingJobExpiry.toMillis() > Date.now();
      const hasActiveJob = activeJobsInTransaction.docs.some((job) => job.get("tripId") === tripId);
      if (existingLockIsActive || hasActiveJob) {
        throw new HttpsError("already-exists", "A generation is already running for this trip");
      }

      transaction.set(generationLockRef, {
        requesterId: uid,
        tripId,
        jobId: jobRef.id,
        expiresAt,
        createdAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      });
      transaction.create(jobRef, {
        schemaVersion: GENERATION_SCHEMA_VERSION,
        requesterId: uid,
        tripId,
        scope: normalized.value.scope,
        input: normalized.value,
        baseTripRevision: Number(currentTrip.revision ?? 0),
        existingItemRevisions,
        state: "queued",
        allowanceReservationMonth: reservation.month,
        createdAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
        expiresAt,
      });
    });
  } catch (error) {
    await settleGenerationSlot(uid, reservation.month, false);
    throw error;
  }

  return {
    jobId: jobRef.id,
    state: "queued",
    expiresAt: expiresAt.toMillis(),
    schemaVersion: GENERATION_SCHEMA_VERSION,
  };
});

/** Returns only sanitized preview data. Raw prompts/provider responses never leave the worker. */
export const getItineraryGenerationJob = onCall(async (request) => {
  const uid = requireAuth(request);
  const jobId = requiredDocumentId(asRecord(request.data).jobId, "jobId", 128);
  const snapshot = await db.doc(`generationJobs/${jobId}`).get();
  if (!snapshot.exists) throw new HttpsError("not-found", "Generation job not found");
  const job = snapshot.data()!;
  if (job.requesterId !== uid) throw new HttpsError("permission-denied", "You cannot view this generation");
  const tripId = typeof job.tripId === "string" ? job.tripId : "";
  // A retained preview can contain destination, dates, and generated place
  // suggestions. Re-check the requester's current profile and editor access
  // before returning it so removal from a shared trip or account suspension
  // takes effect immediately; creation-time authorization is not enough for
  // a seven-day job-retention window. Malformed legacy paths fail closed
  // without attempting to construct a Firestore document reference.
  const validTripId = tripId.length > 0 && tripId.length <= 128 && !tripId.includes("/");
  const tripRef = validTripId ? db.doc(`trips/${tripId}`) : null;
  const [profileSnapshot, tripSnapshot, memberSnapshot] = await Promise.all([
    db.doc(`users/${uid}`).get(),
    tripRef?.get() ?? Promise.resolve(null),
    tripRef?.collection("members").doc(uid).get() ?? Promise.resolve(null),
  ]);
  if (!hasGenerationAccess(
    uid,
    {
      exists: profileSnapshot.exists,
      accountStatus: profileSnapshot.get("accountStatus"),
      ageConfirmed: profileSnapshot.get("ageConfirmed"),
    },
    {
      exists: tripSnapshot?.exists === true,
      ownerId: tripSnapshot?.get("ownerId"),
      status: tripSnapshot?.get("status"),
    },
    {
      exists: memberSnapshot?.exists === true,
      status: memberSnapshot?.get("status"),
      role: memberSnapshot?.get("role"),
    },
  )) {
    throw new HttpsError("permission-denied", "You cannot view this generation");
  }
  const input = asRecord(job.input);
  return publicJob(jobId, job, {
    tripStartDate: tripSnapshot?.exists && typeof tripSnapshot.get("startDate") === "string"
      ? tripSnapshot.get("startDate") as string
      : undefined,
    tripEndDate: tripSnapshot?.exists && typeof tripSnapshot.get("endDate") === "string"
      ? tripSnapshot.get("endDate") as string
      : undefined,
    scope: typeof job.scope === "string" ? job.scope : undefined,
    dayDate: typeof input.dayDate === "string" ? input.dayDate : undefined,
  });
});

/** Cancels a queued/running job without exposing provider details. */
export const cancelItineraryGenerationJob = onCall(async (request) => {
  const uid = requireAuth(request);
  const jobId = requiredDocumentId(asRecord(request.data).jobId, "jobId", 128);
  const jobRef = db.doc(`generationJobs/${jobId}`);
  const result = await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(jobRef);
    if (!snapshot.exists) throw new HttpsError("not-found", "Generation job not found");
    const job = snapshot.data()!;
    if (job.requesterId !== uid) throw new HttpsError("permission-denied", "You cannot cancel this generation");
    const state = String(job.state ?? "");
    if (ACTIVE_JOB_STATES.includes(state)) {
      transaction.update(jobRef, {
        state: "cancelled",
        cancelledBy: uid,
        cancelledAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      });
      return {
        state: "cancelled",
        tripId: typeof job.tripId === "string" ? job.tripId : null,
        reservationMonth: typeof job.allowanceReservationMonth === "string" ? job.allowanceReservationMonth : null,
      };
    }
    return {
      state,
      tripId: typeof job.tripId === "string" ? job.tripId : null,
      reservationMonth: null,
    };
  });
  if (result.state === "cancelled" && result.reservationMonth) {
    await settleGenerationJobReservation(jobRef, uid, result.reservationMonth);
  }
  if (result.tripId) await releaseGenerationLock(uid, result.tripId, jobId);
  return { jobId, state: result.state };
});

/** Applies explicitly selected preview IDs in one transaction. */
export const applyItineraryGenerationJob = onCall(async (request) => {
  const uid = requireAuth(request);
  const data = asRecord(request.data);
  const jobId = requiredDocumentId(data.jobId, "jobId", 128);
  const idempotencyKey = requiredString(data.idempotencyKey, "idempotencyKey", 128);
  const selectedIds = stringArray(data.selectedItemIds, 120, 128);
  if (!selectedIds) throw new HttpsError("invalid-argument", "selectedItemIds must be an array");
  if (selectedIds.length === 0) throw new HttpsError("invalid-argument", "Select at least one suggestion");

  const jobRef = db.doc(`generationJobs/${jobId}`);
  const jobSnapshot = await jobRef.get();
  if (!jobSnapshot.exists) throw new HttpsError("not-found", "Generation job not found");
  const job = jobSnapshot.data()!;
  if (job.requesterId !== uid) throw new HttpsError("permission-denied", "You cannot apply this generation");
  // A retained job is server-owned data, but legacy or manually repaired
  // documents can still be malformed. Validate its schema and path segment
  // before constructing any Firestore reference so every Apply state fails
  // closed with a bounded client error instead of an internal path exception.
  if (job.schemaVersion !== GENERATION_SCHEMA_VERSION) {
    throw new HttpsError("failed-precondition", publicFailureMessage("invalid_job_input"));
  }
  const tripId = typeof job.tripId === "string" ? job.tripId.trim() : "";
  if (!tripId || tripId.length > 128 || tripId.includes("/")) {
    throw new HttpsError("failed-precondition", publicFailureMessage("invalid_job_input"));
  }
  if (job.state === "applied") {
    await releaseGenerationLock(uid, tripId, jobId);
    return {
      jobId,
      state: "applied",
      appliedItemIds: Array.isArray(job.appliedItemIds) ? job.appliedItemIds : [],
      idempotent: job.applyIdempotencyKey === idempotencyKey,
    };
  }
  if (isGenerationJobExpired(job)) {
    throw new HttpsError("failed-precondition", publicFailureMessage("expired"));
  }
  if (!["succeeded", "partially_succeeded"].includes(String(job.state))) {
    throw new HttpsError("failed-precondition", "This preview is not ready to apply");
  }

  const tripRef = db.doc(`trips/${tripId}`);
  const memberRef = tripRef.collection("members").doc(uid);
  const profileRef = db.doc(`users/${uid}`);
  const tripSnapshot = await tripRef.get();
  const memberSnapshot = await memberRef.get();
  assertCanEditTrip(uid, tripSnapshot.data(), memberSnapshot.data());
  const destinationTimezone = typeof tripSnapshot.get("destinationTimezone") === "string"
    ? tripSnapshot.get("destinationTimezone") as string
    : "UTC";
  const jobInput = asRecord(job.input);
  const preview = sanitizePreview(job.preview, {
    tripStartDate: typeof tripSnapshot.get("startDate") === "string" ? tripSnapshot.get("startDate") as string : undefined,
    tripEndDate: typeof tripSnapshot.get("endDate") === "string" ? tripSnapshot.get("endDate") as string : undefined,
    scope: typeof job.scope === "string" ? job.scope : undefined,
    dayDate: typeof jobInput.dayDate === "string" ? jobInput.dayDate : undefined,
  });
  const selectedOriginal = preview.items.filter((item) => selectedIds.includes(item.id));
  if (selectedOriginal.length !== selectedIds.length) throw new HttpsError("invalid-argument", "Unknown suggestion selected");
  const edited = applyGeneratedItemEdits(selectedOriginal, data.editedItems);
  if (!edited.ok) throw new HttpsError("invalid-argument", `Invalid ${edited.field}`);
  const selected = edited.items;

  const applied = await db.runTransaction(async (transaction) => {
    // Re-read the job inside the transaction so two taps racing through Apply
    // cannot both create the deterministic AI item IDs.
    const currentJob = await transaction.get(jobRef);
    if (!currentJob.exists) throw new HttpsError("not-found", "Generation job not found");
    if (currentJob.get("requesterId") !== uid) throw new HttpsError("permission-denied", "You cannot apply this generation");
    if (currentJob.get("schemaVersion") !== GENERATION_SCHEMA_VERSION
      || currentJob.get("tripId") !== tripId) {
      // The preview snapshot was read before the transaction. If a legacy or
      // manually repaired job changes while Apply is in flight, never apply
      // the old preview to a different trip or schema contract.
      throw new HttpsError("failed-precondition", publicFailureMessage("invalid_job_input"));
    }
    if (currentJob.get("state") === "applied") {
      const ids = Array.isArray(currentJob.get("appliedItemIds")) ? currentJob.get("appliedItemIds") as string[] : [];
      return { ids, idempotent: currentJob.get("applyIdempotencyKey") === idempotencyKey };
    }
    // The first expiry check is only an optimistic fast path. Re-check the
    // canonical job inside the transaction so a preview cannot be applied
    // after its retention window elapses while the organizer is editing it.
    if (isGenerationJobExpired(currentJob.data() ?? {})) {
      throw new HttpsError("failed-precondition", publicFailureMessage("expired"));
    }
    if (!["succeeded", "partially_succeeded"].includes(String(currentJob.get("state")))) {
      throw new HttpsError("failed-precondition", "This preview is not ready to apply");
    }
    const [profileSnapshot, currentTrip] = await Promise.all([
      transaction.get(profileRef),
      transaction.get(tripRef),
    ]);
    if (!isActiveProfileSnapshot(profileSnapshot)) {
      throw new HttpsError("failed-precondition", "profile_required");
    }
    if (!currentTrip.exists) throw new HttpsError("not-found", "Trip not found");
    const currentMember = await transaction.get(memberRef);
    assertCanEditTrip(uid, currentTrip.data(), currentMember.data());
    if (!ACTIVE_TRIP_STATUSES.includes(String(currentTrip.get("status") ?? "") as (typeof ACTIVE_TRIP_STATUSES)[number])) {
      throw new HttpsError("failed-precondition", "This trip is read-only");
    }
    if (Number(currentTrip.get("revision") ?? 0) !== Number(job.baseTripRevision ?? 0)) {
      throw new HttpsError("aborted", "Trip changed while the preview was open");
    }
    const currentItems = await transaction.get(tripRef.collection("itinerary").limit(1000));
    const expectedItemRevisions = (job.existingItemRevisions ?? {}) as Record<string, number>;
    const currentItemRevisions = currentItems.docs.map((item) => ({
      id: item.id,
      revision: Number(item.get("revision") ?? 0),
    }));
    if (hasItineraryRevisionConflict(expectedItemRevisions, currentItemRevisions)) {
      throw new HttpsError("aborted", "The itinerary changed while the preview was open");
    }
    const nextPosition = currentItems.docs.reduce(
      (highest, item) => Math.max(highest, Number(item.get("position") ?? -1)),
      -1,
    ) + 1;
    const ids: string[] = [];
    selected.forEach((item, index) => {
      const id = `ai_${jobId}_${index + 1}`;
      const itemRef = tripRef.collection("itinerary").doc(id);
      transaction.create(itemRef, {
        type: item.type,
        title: item.title,
        startTime: Timestamp.fromMillis(localDateTimeToEpochMillis(
          item.dayDate,
          item.startTimeLabel,
          destinationTimezone,
          item.flexibleTime,
        )),
        dayDate: item.dayDate,
        flexibleTime: item.flexibleTime,
        durationMinutes: item.durationMinutes,
        status: "idea",
        visibility: "members",
        position: nextPosition + index,
        revision: 0,
        lastEditedBy: uid,
        generatedBy: "triptandem_ai",
        generationJobId: jobId,
        createdAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
        ...(item.startTimeLabel ? { startTimeLabel: item.startTimeLabel } : {}),
        ...(item.place ? { place: item.place } : {}),
        ...(item.note ? { note: item.note } : {}),
      });
      ids.push(id);
    });
    transaction.update(tripRef, {
      revision: FieldValue.increment(1),
      updatedAt: FieldValue.serverTimestamp(),
    });
    transaction.update(jobRef, {
      state: "applied",
      appliedItemIds: ids,
      selectedItemIds: selectedIds,
      applyIdempotencyKey: idempotencyKey,
      appliedBy: uid,
      appliedAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    });
    return { ids, idempotent: false };
  });

  await releaseGenerationLock(uid, tripId, jobId);

  return { jobId, state: "applied", appliedItemIds: applied.ids, idempotent: applied.idempotent };
});

/** Firestore-triggered worker. It uses a server-only Gemini key and bounded JSON output. */
export const processItineraryGenerationJob = onDocumentCreated(
  // Leave enough wall-clock time for the bounded provider timeout plus the
  // completion/allowance transaction to settle the reservation reliably.
  { document: "generationJobs/{jobId}", secrets: [geminiApiKey], timeoutSeconds: 90 },
  async (event) => {
    const snapshot = event.data;
    if (!snapshot) return;
    const jobId = event.params.jobId;
    const job = snapshot.data();
    // Firestore can deliver an empty/malformed event for a document that was
    // repaired or removed while the trigger was being scheduled. Treat it as
    // a no-op rather than dereferencing an absent payload; no provider call or
    // allowance mutation is safe without a complete queued job document.
    if (!job || typeof job !== "object") return;
    const lockUid = typeof job.requesterId === "string" ? job.requesterId : "";
    const lockTripId = typeof job.tripId === "string" ? job.tripId : "";
    if (job.state !== "queued") return;
    if (!(await isAiGenerationEnabled())) {
      const transition = await transitionActiveGenerationJob(snapshot.ref, {
        state: "failed",
        failureClass: "feature_disabled",
        errorMessage: publicFailureMessage("feature_disabled"),
        updatedAt: FieldValue.serverTimestamp(),
      });
      if (transition !== "unchanged") {
        await settleGenerationJobReservation(
          snapshot.ref,
          String(job.requesterId),
          typeof job.allowanceReservationMonth === "string" ? job.allowanceReservationMonth : null,
        );
      }
      await bestEffortReleaseGenerationLock(lockUid, lockTripId, jobId);
      return;
    }
    if (isGenerationJobExpired(job)) {
      const transition = await transitionActiveGenerationJob(snapshot.ref, {
        state: "expired",
        failureClass: "expired",
        errorMessage: publicFailureMessage("expired"),
        updatedAt: FieldValue.serverTimestamp(),
      });
      if (transition !== "unchanged") {
        await settleGenerationJobReservation(snapshot.ref, String(job.requesterId), typeof job.allowanceReservationMonth === "string" ? job.allowanceReservationMonth : null);
      }
      await bestEffortReleaseGenerationLock(lockUid, lockTripId, jobId);
      return;
    }
    const claimed = await claimQueuedJob(snapshot.ref);
    if (!claimed) return;
    try {
      // Re-validate identifiers, schema, and current access after claiming the
      // job. The requester can be suspended, age consent can be withdrawn, or
      // editor access can be revoked while a queued job is waiting. Nothing
      // from the trip or job may cross the provider boundary until this check
      // succeeds.
      const requesterId = typeof job.requesterId === "string" ? job.requesterId.trim() : "";
      const tripId = typeof job.tripId === "string" ? job.tripId.trim() : "";
      if (!requesterId || requesterId.length > 128 || !tripId || tripId.length > 128 || tripId.includes("/")) {
        throw new GenerationFailure("invalid_job_input");
      }
      if (job.schemaVersion !== GENERATION_SCHEMA_VERSION) {
        throw new GenerationFailure("invalid_job_input");
      }
      const tripRef = db.doc(`trips/${tripId}`);
      const profileRef = db.doc(`users/${requesterId}`);
      const memberRef = tripRef.collection("members").doc(requesterId);
      const [tripSnapshot, profileSnapshot, memberSnapshot] = await Promise.all([
        tripRef.get(),
        profileRef.get(),
        memberRef.get(),
      ]);
      if (!tripSnapshot.exists) throw new GenerationFailure("trip_not_found");
      const trip = tripSnapshot.data()!;
      if (!hasGenerationAccess(
        requesterId,
        {
          exists: profileSnapshot.exists,
          accountStatus: profileSnapshot.get("accountStatus"),
          ageConfirmed: profileSnapshot.get("ageConfirmed"),
        },
        {
          exists: tripSnapshot.exists,
          ownerId: trip.ownerId,
          status: trip.status,
        },
        {
          exists: memberSnapshot.exists,
          status: memberSnapshot.get("status"),
          role: memberSnapshot.get("role"),
        },
      )) {
        throw new GenerationFailure("access_revoked");
      }
      const itemSnapshot = await tripRef.collection("itinerary").limit(1000).get();
      const activeItemDocs = itemSnapshot.docs.filter((doc) => {
        const deletedAt = doc.get("deletedAt");
        return deletedAt === undefined || deletedAt === null;
      });
      // Jobs are normally created through the callable, but the worker must
      // also fail closed for legacy or manually repaired documents. Re-run the
      // same allowlist/shape validation immediately before building a provider
      // prompt so malformed stored input can never cross the privacy boundary.
      const normalizedInput = normalizeGenerationInput(job.input);
      if (!normalizedInput.ok) throw new GenerationFailure("invalid_job_input");
      const input = normalizedInput.value;
      // Every booked item is a server-side scheduling constraint. The client
      // includes these IDs in the reviewed parameter sheet for transparency,
      // but the worker must not trust a forged or stale list to unlock a
      // booked stop. Only the coarse window crosses the provider boundary;
      // titles, places, notes, and member context remain private.
      const lockedWindows = activeItemDocs
        .filter((doc) => doc.get("status") === "booked")
        .map(toLockedGenerationWindow);
      if (lockedWindows.some((window) => window === null)) {
        // A malformed booked item must never be silently omitted from the
        // provider context: doing so could produce a suggestion that
        // collides with a reservation whose time cannot be trusted.
        throw new GenerationFailure("invalid_job_input");
      }
      const validLockedWindows = lockedWindows.filter(
        (window): window is LockedGenerationWindow => window !== null,
      );
      const emptyDayDates = datesInRange(String(trip.startDate), String(trip.endDate))
        .filter((date) => !activeItemDocs.some((doc) => doc.get("dayDate") === date));
      const model = process.env.GEMINI_MODEL || "gemini-3.7-flash";
      const raw = await callGemini(geminiApiKey.value(), model, buildPrompt(trip, input, emptyDayDates, validLockedWindows));
      const validated = validateGeneratedItems(raw, {
        tripStartDate: String(trip.startDate),
        tripEndDate: String(trip.endDate),
        scope: input.scope,
        dayDate: input.dayDate,
        emptyDayDates,
        lockedItems: validLockedWindows,
        existingItems: activeItemDocs.map((doc) => ({
          id: doc.id,
          title: String(doc.get("title") ?? ""),
          dayDate: (doc.get("dayDate") as string | undefined) ?? null,
          place: (doc.get("place") as string | undefined) ?? null,
          startTimeLabel: (doc.get("startTimeLabel") as string | undefined) ?? null,
          flexibleTime: doc.get("flexibleTime") === true,
        })),
      });
      if (validated.items.length === 0) throw new GenerationFailure("no_valid_suggestions");
      const targetDates = input.scope === "single_day"
        ? [input.dayDate!]
        : input.scope === "fill_empty_days"
          ? emptyDayDates
          : datesInRange(String(trip.startDate), String(trip.endDate));
      const suggestedDates = new Set(validated.items.map((item) => item.dayDate));
      const missingDates = targetDates.filter((date) => !suggestedDates.has(date));
      const warnings = [...validated.warnings];
      if (missingDates.length > 0) {
        const previewDates = missingDates.slice(0, 8).join(", ");
        warnings.push(
          missingDates.length > 8
            ? `No suggestions were returned for ${missingDates.length} days, including ${previewDates}.`
            : `No suggestions were returned for ${previewDates}.`,
        );
      }
      const preview: GenerationPreview = {
        schemaVersion: GENERATION_SCHEMA_VERSION,
        assumptions: [
          "Suggestions are planning ideas, not bookings or verified facts.",
          "Travel times and opening hours should be checked before confirming.",
          ...(input.accessibilityNotes ? ["Accessibility guidance used only the note you supplied."] : []),
          ...(input.dietNotes ? ["Food guidance used only the dietary note you supplied."] : []),
        ],
        warnings: [...new Set(warnings)],
        items: validated.items,
        unverifiedInformationNotice: "AI suggestions may be outdated. Check details with the venue or provider before you go.",
      };
      const state = validated.rejectedCount > 0 || missingDates.length > 0 ? "partially_succeeded" : "succeeded";
      const reservationMonth = typeof job.allowanceReservationMonth === "string" ? job.allowanceReservationMonth : null;
      const completion = await completeJobWithUsage(snapshot.ref, String(job.requesterId), reservationMonth, {
        state,
        preview,
        generatedItemCount: preview.items.length,
        rejectedItemCount: validated.rejectedCount,
        completedAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      });
      if (completion === "cancelled") {
        // A user cancellation won the race with the provider response. The
        // reservation must be released and the preview must not become live.
        await settleGenerationJobReservation(snapshot.ref, String(job.requesterId), reservationMonth);
        await bestEffortReleaseGenerationLock(lockUid, lockTripId, jobId);
        return;
      }
      if (completion === "unaccounted") {
        // A reservation should always exist for new jobs. If an older job did
        // not carry one, fail closed instead of exposing an unaccounted draft.
        const transition = await transitionActiveGenerationJob(snapshot.ref, {
          state: "failed",
          failureClass: "quota_exceeded",
          errorMessage: publicFailureMessage("quota_exceeded"),
          failedAt: FieldValue.serverTimestamp(),
          updatedAt: FieldValue.serverTimestamp(),
        });
        if (transition !== "unchanged") {
          await settleGenerationJobReservation(snapshot.ref, String(job.requesterId), reservationMonth);
        }
      }
      await bestEffortReleaseGenerationLock(lockUid, lockTripId, jobId);
    } catch (error) {
      const failureClass = error instanceof GenerationFailure ? error.code : "provider_error";
      const reservationMonth = typeof job.allowanceReservationMonth === "string" ? job.allowanceReservationMonth : null;
      const transition = await transitionActiveGenerationJob(snapshot.ref, {
        state: "failed",
        failureClass,
        // Keep the user-facing error coarse; provider responses and prompts
        // are intentionally not written to Firestore or logs.
        errorMessage: publicFailureMessage(failureClass),
        failedAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      });
      // A cancellation may have won either before or during this transaction;
      // in both cases release the reservation, but never overwrite the
      // cancelled state with a provider failure.
      if (transition !== "unchanged") {
        await settleGenerationJobReservation(snapshot.ref, String(job.requesterId), reservationMonth);
      }
      await bestEffortReleaseGenerationLock(lockUid, lockTripId, jobId);
    }
  },
);

/** RevenueCat webhook is the only source that can grant server-side Pro access. */
export const revenueCatWebhook = onRequest({ secrets: [revenueCatWebhookSecret] }, async (request, response) => {
  const expected = revenueCatWebhookSecret.value();
  const hmacHeader = request.header("x-revenuecat-webhook-signature");
  const provided = request.header("authorization") || request.header("x-revenuecat-signature") || "";
  // Prefer HMAC when the integration sends it; accepting the shared
  // authorization header remains useful for integrations that have not
  // enabled HMAC yet. Never silently downgrade a request that includes an
  // invalid signature to the weaker header check.
  const authenticated = hmacHeader
    ? verifyRevenueCatWebhookSignature(request.rawBody, hmacHeader, expected)
    : safeSecretEquals(provided, expected);
  if (!expected || !authenticated) {
    response.status(401).json({ error: "unauthorized" });
    return;
  }
  const event = asRecord(asRecord(request.body).event);
  const type = typeof event.type === "string" ? event.type.trim().toUpperCase() : "";
  if (!REVENUECAT_WEBHOOK_EVENT_TYPES.has(type)) {
    response.status(400).json({ error: "unsupported_event_type" });
    return;
  }
  const eventId = typeof event.id === "string" && event.id.trim().length <= 160 ? event.id.trim() : null;
  const eventTimestampMs = parseWebhookMillis(event.event_timestamp_ms);
  const expirationMs = parseWebhookMillis(event.expiration_at_ms);
  const hasEventTimestampField = Object.prototype.hasOwnProperty.call(event, "event_timestamp_ms");
  const hasExpirationField = Object.prototype.hasOwnProperty.call(event, "expiration_at_ms");
  if ((hasEventTimestampField && event.event_timestamp_ms !== null && event.event_timestamp_ms !== undefined && eventTimestampMs === null)
    || (hasExpirationField && event.expiration_at_ms !== null && event.expiration_at_ms !== undefined && expirationMs === null)) {
    response.status(400).json({ error: "invalid_event_timestamp_or_expiration" });
    return;
  }

  const hasEntitlementField = Object.prototype.hasOwnProperty.call(event, "entitlement_id")
    || Object.prototype.hasOwnProperty.call(event, "entitlement_ids");
  const explicitlyTargetsEntitlement = targetsEntitlement(event, REVENUECAT_PRO_ENTITLEMENT);

  if (type === "TRANSFER") {
    // RevenueCat transfer payloads identify both sides of the move and may
    // omit app_user_id/entitlement_ids. Resolve the arrays before requiring a
    // single user ID; malformed arrays are rejected rather than ignored.
    const transferredFrom = parseTransferAppUserIds(event.transferred_from);
    const transferredTo = parseTransferAppUserIds(event.transferred_to);
    if (!transferredFrom || !transferredTo) {
      response.status(400).json({ error: "invalid_transfer_users" });
      return;
    }
    if (hasEntitlementField && !explicitlyTargetsEntitlement) {
      response.status(200).json({ received: true, ignored: true });
      return;
    }
    const result = await applyTransferEntitlement({
      transferredFrom,
      transferredTo,
      eventId,
      eventTimestampMs,
      expirationMs,
      productIdentifier: typeof event.product_id === "string" ? event.product_id.slice(0, 160) : null,
      explicitlyTargetsEntitlement: hasEntitlementField && explicitlyTargetsEntitlement,
    });
    response.status(200).json({ received: true, ignored: result === "ignored" });
    return;
  }

  // Webhook integrations can be shared by multiple entitlements. Ignore an
  // unrelated entitlement rather than granting TripTandem Pro accidentally.
  if (!explicitlyTargetsEntitlement) {
    response.status(200).json({ received: true, ignored: true });
    return;
  }

  const rawUid = event.app_user_id;
  if (!isSafeAppUserId(rawUid)) {
    response.status(400).json({ error: "missing_app_user_id" });
    return;
  }
  const uid = rawUid.trim();
  const billingRef = db.doc(`users/${uid}/billing/current`);
  const result = await db.runTransaction(async (transaction) => {
    const current = await transaction.get(billingRef);
    const currentState = {
      isActive: current.get("isActive") === true,
      lastEventTimestampMs: parseWebhookMillis(current.get("lastEventTimestampMs")) ?? undefined,
      lastEventId: typeof current.get("lastEventId") === "string" ? current.get("lastEventId") as string : undefined,
    };
    if (isStaleWebhook(eventTimestampMs, eventId, currentState)) return "ignored" as const;
    const previousExpiration = current.get("expirationAt");
    const expirationAt = expirationMs !== null
      ? Timestamp.fromMillis(expirationMs)
      : hasExpirationField
        ? null
        : previousExpiration ?? null;
    transaction.set(billingRef, {
      entitlement: REVENUECAT_PRO_ENTITLEMENT,
      isActive: resolveWebhookActive(
        type,
        expirationMs ?? (previousExpiration instanceof Timestamp ? previousExpiration.toMillis() : null),
        currentState.isActive,
      ),
      ...(typeof event.product_id === "string" ? { productIdentifier: event.product_id.slice(0, 160) } : {}),
      expirationAt,
      source: "revenuecat_webhook",
      lastEventType: type,
      ...(eventId ? { lastEventId: eventId } : {}),
      ...(eventTimestampMs !== null ? { lastEventTimestampMs: eventTimestampMs } : {}),
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });
    return "applied" as const;
  });
  response.status(200).json({ received: true, ignored: result === "ignored" });
});

type TransferEntitlementInput = {
  transferredFrom: string[];
  transferredTo: string[];
  eventId: string | null;
  eventTimestampMs: number | null;
  expirationMs: number | null;
  productIdentifier: string | null;
  explicitlyTargetsEntitlement: boolean;
};

/**
 * Mirrors a RevenueCat TRANSFER event across all known source/destination
 * aliases. Source access is revoked, while the destination inherits access
 * only from a verified active source or an explicit Pro entitlement field.
 */
async function applyTransferEntitlement(input: TransferEntitlementInput): Promise<"applied" | "ignored"> {
  const sourceIds = [...new Set(input.transferredFrom)];
  const destinationIds = [...new Set(input.transferredTo)];
  const allIds = [...new Set([...sourceIds, ...destinationIds])];
  const references = new Map(allIds.map((id) => [id, db.doc(`users/${id}/billing/current`)]));

  return db.runTransaction(async (transaction) => {
    const snapshots = new Map<string, DocumentSnapshot>();
    const loaded = await Promise.all(allIds.map(async (id) => [id, await transaction.get(references.get(id)!)] as const));
    loaded.forEach(([id, snapshot]) => snapshots.set(id, snapshot));
    const nowMs = Date.now();
    const activeSource = sourceIds
      .map((id) => ({ id, snapshot: snapshots.get(id)! }))
      .find(({ snapshot }) => verifiedProBillingAt(snapshot, nowMs));
    const sourceHasActiveEntitlement = activeSource !== undefined;
    const inheritedExpirationMs = input.expirationMs
      ?? (activeSource ? expirationMillis(activeSource.snapshot) : null);
    const inheritedProductIdentifier = input.productIdentifier
      ?? (activeSource ? stringField(activeSource.snapshot, "productIdentifier", 160) : null);
    let changed = false;

    for (const id of allIds) {
      const reference = references.get(id)!;
      const snapshot = snapshots.get(id)!;
      const currentIsActive = snapshot.exists && snapshot.get("isActive") === true;
      const currentState = {
        isActive: currentIsActive,
        lastEventTimestampMs: parseWebhookMillis(snapshot.get("lastEventTimestampMs")) ?? undefined,
        lastEventId: typeof snapshot.get("lastEventId") === "string" ? snapshot.get("lastEventId") as string : undefined,
      };
      if (isStaleWebhook(input.eventTimestampMs, input.eventId, currentState)) continue;

      const isDestination = destinationIds.includes(id);
      const shouldCreate = isDestination && (input.explicitlyTargetsEntitlement || sourceHasActiveEntitlement);
      if (!snapshot.exists && !shouldCreate) continue;

      const isActive = isDestination
        ? resolveTransferActive(
          input.explicitlyTargetsEntitlement,
          sourceHasActiveEntitlement,
          currentIsActive,
          inheritedExpirationMs,
          nowMs,
        )
        : false;
      const existingExpiration = expirationMillis(snapshot);
      const expirationAt = input.expirationMs !== null
        ? Timestamp.fromMillis(input.expirationMs)
        : inheritedExpirationMs !== null
          ? Timestamp.fromMillis(inheritedExpirationMs)
          : existingExpiration !== null
            ? Timestamp.fromMillis(existingExpiration)
            : null;
      const data: Record<string, unknown> = {
        entitlement: REVENUECAT_PRO_ENTITLEMENT,
        isActive,
        expirationAt,
        source: "revenuecat_webhook",
        lastEventType: "TRANSFER",
        updatedAt: FieldValue.serverTimestamp(),
      };
      if (inheritedProductIdentifier) data.productIdentifier = inheritedProductIdentifier;
      if (input.eventId) data.lastEventId = input.eventId;
      if (input.eventTimestampMs !== null) data.lastEventTimestampMs = input.eventTimestampMs;
      transaction.set(reference, data, { merge: true });
      changed = true;
    }
    return changed ? "applied" : "ignored";
  });
}

/** Short-retention cleanup; generated previews are not an archive. */
export const purgeExpiredGenerationJobs = onSchedule("every 24 hours", async () => {
  // Drain in bounded pages so a backlog cannot leave expired prompts/previews
  // behind for another day. Settling each reservation before deleting its job
  // keeps a retry safe if the scheduled invocation is interrupted.
  while (true) {
    const expired = await db.collection("generationJobs")
      .where("expiresAt", "<=", Timestamp.now())
      .limit(200)
      .get();
    // Stop draining jobs, but continue into the lock sweep below. Returning
    // here would leave orphaned locks behind whenever the job collection had
    // no expired documents in this invocation.
    if (expired.empty) break;
    for (const doc of expired.docs) {
      const job = doc.data();
      await settleGenerationJobReservation(
        doc.ref,
        String(job.requesterId ?? ""),
        typeof job.allowanceReservationMonth === "string" ? job.allowanceReservationMonth : null,
      );
      await bestEffortReleaseGenerationLock(
        typeof job.requesterId === "string" ? job.requesterId : "",
        typeof job.tripId === "string" ? job.tripId : "",
        doc.id,
      );
      await doc.ref.delete();
    }
  }
  // Recover orphaned locks when a worker was interrupted after its job was
  // removed. They are bounded by the same retention deadline as the job.
  while (true) {
    const expiredLocks = await db.collection(GENERATION_LOCKS_COLLECTION)
      .where("expiresAt", "<=", Timestamp.now())
      .limit(200)
      .get();
    if (expiredLocks.empty) return;
    for (const lock of expiredLocks.docs) await lock.ref.delete();
  }
});

function requireAuth(request: CallableRequest): string {
  const uid = request.auth?.uid?.trim();
  if (!uid) throw new HttpsError("unauthenticated", "Sign in to continue");
  return uid;
}

/** Uses a fixed-length server-only key instead of exposing UIDs in lock paths. */
function generationLockId(uid: string, tripId: string): string {
  return createHash("sha256").update(`${uid}\u0000${tripId}`).digest("hex");
}

function verifiedProBilling(snapshot: DocumentSnapshot): boolean {
  return verifiedProBillingAt(snapshot, Date.now());
}

/** Community and generation callables require a completed, age-confirmed profile. */
function isActiveProfileSnapshot(snapshot: DocumentSnapshot): boolean {
  return snapshot.exists
    && snapshot.get("accountStatus") === "active"
    && snapshot.get("ageConfirmed") === true;
}

function verifiedProBillingAt(snapshot: DocumentSnapshot, nowMs: number): boolean {
  if (!snapshot.exists
    || snapshot.get("entitlement") !== "triptandem_pro"
    || snapshot.get("isActive") !== true
    || snapshot.get("source") !== "revenuecat_webhook") {
    return false;
  }
  const expirationAt = snapshot.get("expirationAt");
  // Every supported TripTandem product is time-bounded (monthly/annual
  // subscriptions or a time-bounded judge trial). A missing or malformed
  // expiry must fail closed; treating it as a lifetime entitlement would let
  // stale/malformed webhook state unlock Pro indefinitely.
  return expirationAt instanceof Timestamp && expirationAt.toMillis() > nowMs;
}

function expirationMillis(snapshot: DocumentSnapshot): number | null {
  const value = snapshot.get("expirationAt");
  return value instanceof Timestamp && Number.isFinite(value.toMillis()) ? value.toMillis() : null;
}

function stringField(snapshot: DocumentSnapshot, field: string, maxLength: number): string | null {
  const value = snapshot.get(field);
  return typeof value === "string" && value.trim().length > 0
    ? value.trim().slice(0, maxLength)
    : null;
}

function asRecord(value: unknown): Record<string, any> {
  return typeof value === "object" && value !== null && !Array.isArray(value) ? value as Record<string, any> : {};
}

function requiredString(value: unknown, field: string, maxLength: number): string {
  if (typeof value !== "string" || value.trim().length === 0 || value.trim().length > maxLength) {
    throw new HttpsError("invalid-argument", `${field} is invalid`);
  }
  return value.trim();
}

/** Validates a caller-controlled Firestore document ID before path construction. */
function requiredDocumentId(value: unknown, field: string, maxLength: number): string {
  const normalized = requiredString(value, field, maxLength);
  if (normalized.includes("/")) throw new HttpsError("invalid-argument", `${field} is invalid`);
  return normalized;
}

function stringArray(value: unknown, maxItems: number, maxLength: number): string[] | null {
  if (!Array.isArray(value) || value.length > maxItems) return null;
  const output = value.filter((item): item is string => typeof item === "string").map((item) => item.trim());
  return output.length === value.length && output.every((item) => item.length > 0 && item.length <= maxLength)
    ? [...new Set(output)]
    : null;
}

function assertCanEditTrip(uid: string, trip: Record<string, any> | undefined, member: Record<string, any> | undefined): void {
  if (!trip) throw new HttpsError("not-found", "Trip not found");
  const role = trip.ownerId === uid ? "owner" : member?.status === "active" ? member.role : undefined;
  if (role !== "owner" && role !== "editor") throw new HttpsError("permission-denied", "Only the organizer or an editor can generate an itinerary");
}

function isDateInTrip(date: string, start: string, end: string): boolean {
  return isValidIsoDate(date) && isValidIsoDate(start) && isValidIsoDate(end) && date >= start && date <= end;
}

type GenerationPreviewContext = {
  tripStartDate?: string;
  tripEndDate?: string;
  scope?: string;
  dayDate?: string;
};

function publicJob(jobId: string, job: Record<string, any>, context: GenerationPreviewContext = {}): Record<string, any> {
  const rawState = typeof job.state === "string" ? job.state : "failed";
  const expired = EXPIRABLE_GENERATION_STATES.has(rawState) && isGenerationJobExpired(job);
  const schemaValid = job.schemaVersion === GENERATION_SCHEMA_VERSION;
  const invalidSchema = !schemaValid && ["succeeded", "partially_succeeded", "applied"].includes(rawState);
  const state = expired
    ? "expired"
    : invalidSchema
      ? "failed"
    : PUBLIC_GENERATION_STATES.has(rawState)
      ? rawState
      : "failed";
  // Rebuild the message from the coarse failure class instead of returning a
  // stored error string. This keeps a legacy/malformed job from ever leaking
  // provider text, prompts, or SDK-localized payloads through the callable.
  const rawFailureClass = typeof job.failureClass === "string" ? job.failureClass : null;
  const failureClass = expired
    ? "expired"
    : invalidSchema
      ? "invalid_provider_output"
    : rawFailureClass && PUBLIC_GENERATION_FAILURE_CLASSES.has(rawFailureClass)
      ? rawFailureClass
      : null;
  const output: Record<string, any> = {
    jobId,
    state,
    schemaVersion: GENERATION_SCHEMA_VERSION,
    generatedItemCount: boundedCount(job.generatedItemCount),
    rejectedItemCount: boundedCount(job.rejectedItemCount),
    failureClass,
    errorMessage: failureClass ? publicFailureMessage(failureClass) : null,
    preview: expired || invalidSchema ? null : job.preview ? sanitizePreview(job.preview, context) : null,
  };
  return output;
}

function sanitizePreview(value: unknown, context: GenerationPreviewContext = {}): GenerationPreview {
  const input = asRecord(value);
  if (input.schemaVersion !== GENERATION_SCHEMA_VERSION) {
    return {
      schemaVersion: GENERATION_SCHEMA_VERSION,
      assumptions: [],
      warnings: [],
      items: [],
      unverifiedInformationNotice: "AI suggestions may be outdated. Check details before you go.",
    };
  }
  const items = Array.isArray(input.items)
    ? input.items.map((entry) => sanitizePublicItem(entry, context)).filter((entry): entry is NonNullable<typeof entry> => entry !== null)
    : [];
  return {
    // Only the currently supported schema is exposed. Legacy or malformed
    // values cannot make an unknown contract look valid to a client.
    schemaVersion: GENERATION_SCHEMA_VERSION,
    assumptions: stringArray(input.assumptions, 20, 300) ?? [],
    warnings: stringArray(input.warnings, 40, 300) ?? [],
    items: items.slice(0, 120),
    // Keep this disclosure server-owned; a legacy/provider-supplied notice is
    // not allowed to cross the callable boundary.
    unverifiedInformationNotice: "AI suggestions may be outdated. Check details before you go.",
  };
}

function sanitizePublicItem(value: unknown, context: GenerationPreviewContext = {}): GenerationPreview["items"][number] | null {
  const input = asRecord(value);
  const type = input.type;
  if (!(["activity", "transport", "meal", "free"] as string[]).includes(String(type))) return null;
  const title = typeof input.title === "string" ? input.title.trim().slice(0, 160) : "";
  const id = typeof input.id === "string" ? input.id.trim().slice(0, 128) : "";
  const dayDate = typeof input.dayDate === "string" ? input.dayDate : "";
  if (typeof input.flexibleTime !== "boolean") return null;
  const flexibleTime = input.flexibleTime;
  const startTimeLabel = typeof input.startTimeLabel === "string" ? input.startTimeLabel.trim().slice(0, 32) : "";
  const durationMinutes = Number.isInteger(input.durationMinutes) ? Number(input.durationMinutes) : -1;
  if (!id || !title || !isValidIsoDate(dayDate) || durationMinutes < 0 || durationMinutes > 1440) return null;
  if (context.tripStartDate && context.tripEndDate && !isDateInTrip(dayDate, context.tripStartDate, context.tripEndDate)) return null;
  if (context.scope === "single_day" && context.dayDate && dayDate !== context.dayDate) return null;
  if (!flexibleTime && !/^(?:[01]\d|2[0-3]):[0-5]\d$/.test(startTimeLabel)) return null;
  if (flexibleTime && startTimeLabel && !/^(morning|late morning|afternoon|evening|night|anytime)$/i.test(startTimeLabel)) return null;
  return {
    id,
    type: type as GenerationPreview["items"][number]["type"],
    title,
    dayDate,
    ...(startTimeLabel ? { startTimeLabel } : {}),
    flexibleTime,
    durationMinutes,
    ...(typeof input.place === "string" && input.place.trim() ? { place: input.place.trim().slice(0, 200) } : {}),
    ...(typeof input.note === "string" && input.note.trim() ? { note: input.note.trim().slice(0, 1000) } : {}),
    ...(typeof input.duplicateOfItemId === "string" ? { duplicateOfItemId: input.duplicateOfItemId.slice(0, 128) } : {}),
    ...(typeof input.warning === "string" ? { warning: input.warning.slice(0, 300) } : {}),
  };
}

function isGenerationJobExpired(job: Record<string, any>): boolean {
  const expiresAt = job.expiresAt;
  // A missing or malformed retention deadline fails closed. New jobs always
  // carry a Firestore Timestamp; legacy jobs must not bypass retention by
  // omitting it.
  return !(expiresAt instanceof Timestamp) || expiresAt.toMillis() <= Date.now();
}

function boundedCount(value: unknown): number {
  const count = typeof value === "number" && Number.isFinite(value) ? Math.trunc(value) : 0;
  return Math.max(0, Math.min(120, count));
}

function currentUsageMonth(): string {
  const now = new Date();
  return `${now.getUTCFullYear()}-${String(now.getUTCMonth() + 1).padStart(2, "0")}`;
}

/** Reserves allowance before a provider call so concurrent jobs cannot overspend. */
async function reserveGenerationSlot(uid: string): Promise<{ allowed: boolean; month: string }> {
  const usageRef = db.doc(`users/${uid}/billing/generationUsage`);
  const billingRef = db.doc(`users/${uid}/billing/current`);
  return db.runTransaction(async (transaction) => {
    const [usage, billing] = await Promise.all([transaction.get(usageRef), transaction.get(billingRef)]);
    const isPro = verifiedProBilling(billing);
    const month = currentUsageMonth();
    const sameMonth = usage.get("month") === month;
    const count = sameMonth ? boundedNonNegativeInteger(usage.get("successfulCount"), MAX_PRO_GENERATIONS_PER_MONTH) : 0;
    const reservedCount = sameMonth ? boundedNonNegativeInteger(usage.get("reservedCount"), MAX_PRO_GENERATIONS_PER_MONTH) : 0;
    if (count === null || reservedCount === null) return { allowed: false, month };
    const limit = isPro ? MAX_PRO_GENERATIONS_PER_MONTH : MAX_FREE_GENERATIONS;
    if (count + reservedCount >= limit) return { allowed: false, month };
    transaction.set(usageRef, {
      month,
      successfulCount: count,
      reservedCount: reservedCount + 1,
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });
    return { allowed: true, month };
  });
}

/** Settles one reservation. Failed/cancelled jobs release it; success converts it to usage. */
async function settleGenerationSlot(uid: string, reservationMonth: string | null, success: boolean): Promise<boolean> {
  if (!uid || !reservationMonth) return false;
  const usageRef = db.doc(`users/${uid}/billing/generationUsage`);
  return db.runTransaction(async (transaction) => {
    const usage = await transaction.get(usageRef);
    if (!usage.exists || usage.get("month") !== reservationMonth) return false;
    const reservedCount = boundedNonNegativeInteger(usage.get("reservedCount"), MAX_PRO_GENERATIONS_PER_MONTH);
    const successfulCount = boundedNonNegativeInteger(usage.get("successfulCount"), MAX_PRO_GENERATIONS_PER_MONTH);
    if (reservedCount === null || successfulCount === null) return false;
    if (reservedCount <= 0) return false;
    if (success && successfulCount >= MAX_PRO_GENERATIONS_PER_MONTH) return false;
    transaction.set(usageRef, {
      reservedCount: Math.max(0, reservedCount - 1),
      ...(success ? { successfulCount: successfulCount + 1 } : {}),
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });
    return true;
  });
}

/**
 * Releases a job's allowance exactly once while it is being cancelled or
 * failed. The marker is written in the same transaction as the usage update,
 * so a worker, user cancellation, trip deletion, and scheduled purge can race
 * safely without decrementing another job's reservation.
 */
async function settleGenerationJobReservation(
  reference: DocumentReference,
  uid: string,
  reservationMonth: string | null,
): Promise<boolean> {
  if (!uid || !reservationMonth) return false;
  const usageRef = db.doc(`users/${uid}/billing/generationUsage`);
  return db.runTransaction(async (transaction) => {
    const [job, usage] = await Promise.all([transaction.get(reference), transaction.get(usageRef)]);
    if (!job.exists || job.get("requesterId") !== uid) return false;
    const allowanceSettledAt = job.get("allowanceSettledAt");
    if (allowanceSettledAt !== undefined && allowanceSettledAt !== null) return false;
    const state = String(job.get("state") ?? "");
    if (![...ACTIVE_JOB_STATES, "failed", "cancelled", "expired"].includes(state)) return false;
    if (!usage.exists || usage.get("month") !== reservationMonth) return false;
    const reservedCount = boundedNonNegativeInteger(usage.get("reservedCount"), MAX_PRO_GENERATIONS_PER_MONTH);
    if (reservedCount === null || reservedCount <= 0) return false;
    transaction.update(reference, { allowanceSettledAt: FieldValue.serverTimestamp() });
    transaction.set(usageRef, {
      reservedCount: Math.max(0, reservedCount - 1),
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });
    return true;
  });
}

/** Atomically publishes a preview and consumes its reserved allowance. */
async function completeJobWithUsage(
  reference: DocumentReference,
  uid: string,
  reservationMonth: string | null,
  data: Record<string, unknown>,
): Promise<"completed" | "cancelled" | "unaccounted"> {
  if (!uid || !reservationMonth) return "unaccounted";
  const usageRef = db.doc(`users/${uid}/billing/generationUsage`);
  return db.runTransaction(async (transaction) => {
    const [job, usage] = await Promise.all([transaction.get(reference), transaction.get(usageRef)]);
    if (!job.exists || job.get("state") === "cancelled") return "cancelled";
    if (!ACTIVE_JOB_STATES.includes(String(job.get("state") ?? ""))) return "unaccounted";
    const allowanceSettledAt = job.get("allowanceSettledAt");
    if (allowanceSettledAt !== undefined && allowanceSettledAt !== null) return "unaccounted";
    const reservedCount = boundedNonNegativeInteger(usage.get("reservedCount"), MAX_PRO_GENERATIONS_PER_MONTH);
    const successfulCount = boundedNonNegativeInteger(usage.get("successfulCount"), MAX_PRO_GENERATIONS_PER_MONTH);
    if (!usage.exists || usage.get("month") !== reservationMonth || reservedCount === null || successfulCount === null || reservedCount <= 0 || successfulCount >= MAX_PRO_GENERATIONS_PER_MONTH) {
      return "unaccounted";
    }
    transaction.update(reference, {
      ...data,
      allowanceSettledAt: FieldValue.serverTimestamp(),
    });
    transaction.set(usageRef, {
      reservedCount: Math.max(0, reservedCount - 1),
      successfulCount: successfulCount + 1,
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });
    return "completed";
  });
}

async function claimQueuedJob(reference: DocumentReference): Promise<boolean> {
  return db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(reference);
    if (!snapshot.exists || snapshot.get("state") !== "queued") return false;
    transaction.update(reference, {
      state: "running",
      startedAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    });
    return true;
  });
}

/**
 * Transitions an active generation job without clobbering a cancellation (or
 * another terminal state) that wins the race. The worker uses this for
 * feature-disabled, expiry, and provider-failure paths; applying the same
 * guard to every terminal update keeps allowance settlement idempotent.
 */
async function transitionActiveGenerationJob(
  reference: DocumentReference,
  data: Record<string, unknown>,
): Promise<"updated" | "cancelled" | "unchanged"> {
  return db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(reference);
    if (!snapshot.exists) return "unchanged";
    const state = String(snapshot.get("state") ?? "");
    if (state === "cancelled") return "cancelled";
    if (!ACTIVE_JOB_STATES.includes(state)) return "unchanged";
    transaction.update(reference, data);
    return "updated";
  });
}

function datesInRange(start: string, end: string): string[] {
  if (!isDateInTrip(start, start, end) || !isDateInTrip(end, start, end)) return [];
  const output: string[] = [];
  const cursor = new Date(`${start}T00:00:00Z`);
  const last = new Date(`${end}T00:00:00Z`);
  for (let index = 0; cursor <= last && index < 60; index += 1) {
    output.push(cursor.toISOString().slice(0, 10));
    cursor.setUTCDate(cursor.getUTCDate() + 1);
  }
  return output;
}

/**
 * Firestore keeps the canonical itinerary startTime as a timestamp. Flexible
 * labels intentionally use epoch zero (the same representation as the native
 * clients), while fixed labels are converted from the trip's destination-local
 * clock without sending any private user data to a provider.
 */
function localDateTimeToEpochMillis(
  dayDate: string,
  label: string | undefined,
  timezone: string,
  flexibleTime: boolean,
): number {
  if (flexibleTime || !label) return 0;
  const match = /^(\d{2}):(\d{2})$/.exec(label);
  if (!match || !isDateInTrip(dayDate, dayDate, dayDate)) return 0;
  const [year, month, day] = dayDate.split("-").map(Number);
  const hour = Number(match[1]);
  const minute = Number(match[2]);
  const desiredUtc = Date.UTC(year, month - 1, day, hour, minute, 0, 0);
  try {
    const formatter = new Intl.DateTimeFormat("en-US", {
      timeZone: timezone || "UTC",
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
      hourCycle: "h23",
    });
    let candidate = desiredUtc;
    // Two passes are enough for normal offsets and also converge across DST
    // transitions without pulling in a timezone package for this small field.
    for (let pass = 0; pass < 2; pass += 1) {
      const parts = Object.fromEntries(formatter.formatToParts(new Date(candidate))
        .filter((part) => part.type !== "literal")
        .map((part) => [part.type, Number(part.value)]));
      const displayedUtc = Date.UTC(
        parts.year,
        (parts.month ?? 1) - 1,
        parts.day ?? 1,
        parts.hour ?? 0,
        parts.minute ?? 0,
        parts.second ?? 0,
      );
      candidate = desiredUtc - (displayedUtc - candidate);
    }
    return candidate;
  } catch {
    // An invalid legacy timezone should not prevent the user from applying a
    // valid preview; UTC remains a valid canonical timestamp fallback.
    return desiredUtc;
  }
}

function toLockedGenerationWindow(snapshot: DocumentSnapshot): LockedGenerationWindow | null {
  const dayDate = typeof snapshot.get("dayDate") === "string" ? snapshot.get("dayDate") as string : "";
  if (!isValidIsoDate(dayDate)) return null;
  const startTimeLabel = typeof snapshot.get("startTimeLabel") === "string"
    ? (snapshot.get("startTimeLabel") as string).trim().slice(0, 32)
    : "";
  const flexibleTime = snapshot.get("flexibleTime") === true;
  const durationMinutes = Number(snapshot.get("durationMinutes"));
  if (!Number.isInteger(durationMinutes) || durationMinutes <= 0 || durationMinutes > 1440) return null;
  if (!flexibleTime && !VALID_CLOCK_LABEL.test(startTimeLabel)) return null;
  if (flexibleTime && startTimeLabel && !VALID_FLEXIBLE_LABEL.test(startTimeLabel)) return null;
  const type = typeof snapshot.get("type") === "string" ? snapshot.get("type") as string : undefined;
  return {
    dayDate,
    ...(startTimeLabel ? { startTimeLabel } : {}),
    flexibleTime,
    durationMinutes,
    ...(type ? { type } : {}),
  };
}

function buildPrompt(
  trip: Record<string, any>,
  input: GenerationInput,
  emptyDayDates: string[],
  lockedWindows: LockedGenerationWindow[] = [],
): string {
  // Deliberately list only trip planning fields. Never include member names,
  // email addresses, bios, contact details, exact lodging, or private notes.
  const safeContext = {
    destination: String(trip.destination ?? ""),
    startDate: String(trip.startDate ?? ""),
    endDate: String(trip.endDate ?? ""),
    destinationTimezone: String(trip.destinationTimezone ?? "UTC"),
    pace: input.pace ?? trip.pace ?? "balanced",
    budgetBand: input.budgetBand ?? trip.budgetBand ?? "moderate",
    interests: input.interests,
    dailyStartLabel: input.dailyStartLabel ?? null,
    dailyEndLabel: input.dailyEndLabel ?? null,
    accessibilityNotes: input.accessibilityNotes ?? null,
    dietNotes: input.dietNotes ?? null,
    scope: input.scope,
    dayDate: input.dayDate ?? null,
    emptyDayDates: input.scope === "fill_empty_days" ? emptyDayDates : undefined,
    // These are intentionally coarse scheduling constraints. Never include
    // the booked item's title, place, note, lodging, or any member identity.
    lockedWindows: lockedWindows.slice(0, 500).map((window) => ({
      dayDate: window.dayDate,
      startTimeLabel: window.startTimeLabel ?? null,
      flexibleTime: window.flexibleTime === true,
      durationMinutes: window.durationMinutes ?? null,
      type: typeof window.type === "string" ? window.type : null,
    })),
  };
  return [
    "You are TripTandem's itinerary planning assistant.",
    "Return JSON only. Suggest planning ideas, never claim availability, reservations, opening hours, safety, or verification.",
    "Do not invent exact lodging, personal data, contact details, or private meeting points.",
    "Use only the supplied destination and date range. Keep titles concise and useful.",
    "All dayDate values must be YYYY-MM-DD in the trip range. Use startTimeLabel HH:mm or a flexible label (morning, afternoon, evening).",
    "Do not schedule a suggestion over a locked booked window. Locked items are unchanged and must remain in the plan.",
    "Output an object with schemaVersion exactly " + GENERATION_SCHEMA_VERSION + " and an items array. Each item has type (activity, transport, meal, free), title, dayDate, startTimeLabel, flexibleTime, durationMinutes, place, note.",
    JSON.stringify(safeContext),
  ].join("\n");
}

async function callGemini(apiKey: string, model: string, prompt: string): Promise<unknown> {
  if (!apiKey) throw new GenerationFailure("provider_unconfigured");
  const schema = {
    type: "OBJECT",
    properties: {
      schemaVersion: { type: "STRING", enum: [GENERATION_SCHEMA_VERSION] },
      items: {
        type: "ARRAY",
        maxItems: 120,
        items: {
          type: "OBJECT",
          properties: {
            type: { type: "STRING", enum: ["activity", "transport", "meal", "free"] },
            title: { type: "STRING" },
            dayDate: { type: "STRING" },
            startTimeLabel: { type: "STRING" },
            flexibleTime: { type: "BOOLEAN" },
            durationMinutes: { type: "INTEGER" },
            place: { type: "STRING" },
            note: { type: "STRING" },
          },
          required: ["type", "title", "dayDate", "flexibleTime", "durationMinutes"],
        },
      },
    },
    required: ["schemaVersion", "items"],
  };
  // Keep the server-only key out of the URL. Query strings are more likely to
  // be copied into proxy/access logs; Gemini accepts the same key via the
  // request header, which keeps credential handling inside the Functions
  // request boundary.
  const url = `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(model)}:generateContent`;
  let result: Response;
  try {
    result = await fetch(url, {
      method: "POST",
      headers: { "content-type": "application/json", "x-goog-api-key": apiKey },
      body: JSON.stringify({
        contents: [{ role: "user", parts: [{ text: prompt }] }],
        // Gemini 3.x structured-output models no longer accept the legacy
        // sampling parameters (temperature/top_p/top_k). Keep the request
        // deterministic through the schema and bounded prompt instead of
        // sending a parameter the current model rejects.
        generationConfig: { responseMimeType: "application/json", responseSchema: schema },
      }),
      signal: AbortSignal.timeout(45_000),
    });
  } catch (error) {
    const errorName = typeof error === "object" && error !== null && "name" in error
      ? String((error as { name?: unknown }).name)
      : "";
    throw new GenerationFailure(errorName === "AbortError" || errorName === "TimeoutError" ? "provider_timeout" : "provider_error");
  }
  let body: Record<string, any> = {};
  try {
    body = await result.json() as Record<string, any>;
  } catch {
    // The response is classified below without retaining provider details.
  }
  const responseFailure = classifyGeminiResponse(result.status, body);
  if (responseFailure !== "ok") throw new GenerationFailure(responseFailure);
  const text = body.candidates?.[0]?.content?.parts?.map((part: any) => part.text).filter(Boolean).join("");
  if (typeof text !== "string" || text.length > 200_000) throw new GenerationFailure("invalid_provider_output");
  try {
    const parsed = JSON.parse(text);
    if (!isVersionedGenerationResponse(parsed)) throw new Error("schema_version");
    return parsed;
  } catch {
    throw new GenerationFailure("invalid_provider_output");
  }
}

function publicFailureMessage(code: string): string {
  switch (code) {
    case "quota_exceeded": return "Your generation allowance is used. Try Organizer Pro or build the plan manually.";
    case "provider_unconfigured": return "AI itinerary generation is not enabled yet. You can build the plan manually.";
    case "feature_disabled": return "AI itinerary generation is not enabled right now. Your inputs are preserved; plan manually.";
    case "provider_rate_limited": return "The AI service is busy right now. Your inputs are preserved; try again or plan manually.";
    case "provider_timeout": return "The AI service took too long to respond. Your inputs are preserved; try again or plan manually.";
    case "no_valid_suggestions": return "No safe suggestions were returned. Review your inputs or plan manually.";
    case "invalid_job_input": return "This draft could not be validated safely. Your inputs are preserved; try again or plan manually.";
    case "access_revoked": return "Your account or editing access changed before this draft finished. Your existing itinerary was not changed.";
    case "safety_blocked": return "This request isn't available for itinerary planning. You can build the plan manually.";
    case "cancelled": return "Generation cancelled. Your existing itinerary was not changed.";
    case "expired": return "This draft expired. Start a new generation when you're ready.";
    default: return "We couldn't generate a safe itinerary right now. Your inputs are preserved; try again or plan manually.";
  }
}

/**
 * Reads the reviewed Remote Config template with a short instance-local TTL.
 * Any missing value, conditional-only rollout, permission error, or network
 * failure disables the server operation. A client-side flag can therefore
 * never turn on provider work by itself.
 */
async function isAiGenerationEnabled(): Promise<boolean> {
  const now = Date.now();
  if (aiGenerationFlagCache && aiGenerationFlagCache.expiresAt > now) {
    return aiGenerationFlagCache.enabled;
  }
  if (!aiGenerationFlagRequest) {
    aiGenerationFlagRequest = (async () => {
      try {
        const template = await getRemoteConfig().getTemplate();
        return isRemoteConfigBooleanEnabled(template, AI_GENERATION_ENABLED_KEY);
      } catch {
        return false;
      }
    })();
  }
  try {
    const enabled = await aiGenerationFlagRequest;
    aiGenerationFlagCache = { enabled, expiresAt: Date.now() + REMOTE_CONFIG_CACHE_MS };
    return enabled;
  } finally {
    aiGenerationFlagRequest = undefined;
  }
}

class GenerationFailure extends Error {
  constructor(public readonly code: string) {
    super(code);
  }
}

function safeSecretEquals(actual: string, expected: string): boolean {
  const a = Buffer.from(actual);
  const b = Buffer.from(expected);
  return a.length === b.length && timingSafeEqual(a, b);
}

function isActiveSharedOwnedTrip(snapshot: DocumentSnapshot): boolean {
  const status = String(snapshot.get("status") ?? "");
  if (!ACTIVE_TRIP_STATUSES.includes(status as (typeof ACTIVE_TRIP_STATUSES)[number])) return false;
  const count = snapshot.get("activeMemberCount");
  // A malformed active-trip counter fails closed. Treating it as one could
  // let account deletion strand collaborators when the aggregate is corrupt.
  return typeof count !== "number" || !Number.isInteger(count) || count < 1 || count > 1;
}

/**
 * Checks the owner-trip deletion invariant using both the aggregate counter
 * and the active member documents. The counter is maintained transactionally
 * for normal writes, but an old repair/import or an administrative mistake
 * can leave it claiming one member while a collaborator is still active.
 * Reading up to two active members is enough to fail closed without loading
 * an unbounded roster; a single active member is safe only when it is the
 * owner document itself.
 */
async function hasActiveSharedOwnedTrips(
  ownerTrips: DocumentSnapshot[],
  readActiveMembers: (tripRef: DocumentReference) => Promise<DocumentSnapshot[]>,
): Promise<boolean> {
  for (const trip of ownerTrips) {
    const status = String(trip.get("status") ?? "");
    if (!ACTIVE_TRIP_STATUSES.includes(status as (typeof ACTIVE_TRIP_STATUSES)[number])) continue;
    if (isActiveSharedOwnedTrip(trip)) return true;
    const count = trip.get("activeMemberCount");
    if (typeof count !== "number" || !Number.isInteger(count) || count !== 1) return true;
    const activeMembers = await readActiveMembers(trip.ref);
    if (activeMembers.length !== 1) return true;
    const ownerId = trip.get("ownerId");
    const member = activeMembers[0];
    if (member.get("userId") !== ownerId || member.get("role") !== "owner") return true;
  }
  return false;
}

async function assertNoActiveSharedOwnedTrips(uid: string): Promise<void> {
  const ownerTrips = await db.collection("trips").where("ownerId", "==", uid).get();
  if (await hasActiveSharedOwnedTrips(ownerTrips.docs, (tripRef) =>
    tripRef.collection("members").where("status", "==", "active").limit(2).get()
      .then((snapshot) => snapshot.docs),
  )) {
    throw new HttpsError("failed-precondition", "ownership_required");
  }
}

/**
 * Marks a trip as archived and creates a server-owned deletion lock in one
 * transaction. The lock is intentionally separate from the trip document so
 * Rules can deny child writes while the cleanup drains every subcollection.
 * Repeating the call for the same owner is safe and continues an interrupted
 * cleanup.
 */
async function lockTripForDeletion(uid: string, tripRef: DocumentReference): Promise<"locked" | "missing"> {
  const profileRef = db.doc(`users/${uid}`);
  const lockRef = db.collection(TRIP_DELETION_LOCKS_COLLECTION).doc(tripRef.id);

  return db.runTransaction(async (transaction) => {
    const [profileSnapshot, tripSnapshot, lockSnapshot] = await Promise.all([
      transaction.get(profileRef),
      transaction.get(tripRef),
      transaction.get(lockRef),
    ]);
    if (!isActiveProfileSnapshot(profileSnapshot)) {
      throw new HttpsError("failed-precondition", "profile_required");
    }
    if (!tripSnapshot.exists) {
      if (lockSnapshot.exists && lockSnapshot.get("ownerId") === uid) transaction.delete(lockRef);
      return "missing";
    }
    if (tripSnapshot.get("ownerId") !== uid) {
      throw new HttpsError("permission-denied", "Only the organizer can delete this trip");
    }
    if (lockSnapshot.exists && lockSnapshot.get("ownerId") !== uid) {
      throw new HttpsError("failed-precondition", "trip_deletion_in_progress");
    }

    const revision = tripSnapshot.get("revision");
    if (typeof revision !== "number" || !Number.isInteger(revision) || revision < 0) {
      throw new HttpsError("failed-precondition", "Trip state is invalid");
    }
    transaction.set(lockRef, {
      ownerId: uid,
      tripId: tripRef.id,
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });
    transaction.update(tripRef, {
      status: "archived",
      revision: revision + 1,
      updatedAt: FieldValue.serverTimestamp(),
    });
    return "locked";
  });
}

/** Deletes all known trip subcollections in bounded pages. */
async function deleteTripSubcollections(tripRef: DocumentReference): Promise<number> {
  let deletedDocumentCount = 0;
  for (const collectionName of ACCOUNT_TRIP_SUBCOLLECTIONS) {
    const collection = tripRef.collection(collectionName);
    while (true) {
      const page = await collection.limit(ACCOUNT_CLEANUP_BATCH_SIZE).get();
      if (page.empty) break;
      await deleteDocumentReferences(page.docs.map((document) => document.ref));
      deletedDocumentCount += page.size;
    }
  }
  return deletedDocumentCount;
}

/**
 * Cancels active generation jobs and removes every server-owned artifact for
 * one trip. Jobs are settled through their own idempotency marker before the
 * documents are deleted, so an in-flight worker cannot consume another job's
 * allowance while a trip graph is being removed.
 */
async function deleteTripServerOwnedData(tripId: string): Promise<number> {
  await db.doc(`communityListings/${tripId}`).delete();
  await db.doc(`communityClosures/${tripId}`).delete();
  const [generationJobs, generationLocks] = await Promise.all([
    db.collection("generationJobs").where("tripId", "==", tripId).get(),
    db.collection(GENERATION_LOCKS_COLLECTION).where("tripId", "==", tripId).get(),
  ]);
  await releaseGenerationReservations(generationJobs.docs);
  await Promise.all(generationJobs.docs.map((job) => bestEffortReleaseGenerationLock(
    typeof job.get("requesterId") === "string" ? job.get("requesterId") as string : "",
    tripId,
    job.id,
  )));
  await deleteDocumentReferences([
    ...generationJobs.docs.map((document) => document.ref),
    ...generationLocks.docs.map((document) => document.ref),
  ]);
  return generationJobs.size + generationLocks.size;
}

/** Removes the root and its lock only after the graph cleanup is complete. */
async function finalizeTripDeletion(uid: string, tripRef: DocumentReference): Promise<boolean> {
  const lockRef = db.collection(TRIP_DELETION_LOCKS_COLLECTION).doc(tripRef.id);
  return db.runTransaction(async (transaction) => {
    const [tripSnapshot, lockSnapshot] = await Promise.all([
      transaction.get(tripRef),
      transaction.get(lockRef),
    ]);
    if (!tripSnapshot.exists) {
      if (lockSnapshot.exists && lockSnapshot.get("ownerId") === uid) transaction.delete(lockRef);
      return false;
    }
    if (tripSnapshot.get("ownerId") !== uid || !lockSnapshot.exists || lockSnapshot.get("ownerId") !== uid) {
      throw new HttpsError("failed-precondition", "trip_deletion_in_progress");
    }
    transaction.delete(tripRef);
    transaction.delete(lockRef);
    return true;
  });
}

/** Deletes an owner's trip roots and all known subcollections in bounded pages. */
async function cleanupOwnedTripData(uid: string): Promise<{
  deletedTripCount: number;
  deletedTripDocumentCount: number;
}> {
  const ownerTrips = await db.collection("trips").where("ownerId", "==", uid).get();
  let deletedTripCount = 0;
  let deletedTripDocumentCount = 0;
  for (const trip of ownerTrips.docs) {
    const state = await lockTripForDeletion(uid, trip.ref);
    if (state === "missing") continue;
    const deletedSubcollectionDocumentCount = await deleteTripSubcollections(trip.ref);
    const deletedServerOwnedDocumentCount = await deleteTripServerOwnedData(trip.id);
    const finalized = await finalizeTripDeletion(uid, trip.ref);
    if (finalized) {
      deletedTripCount += 1;
      deletedTripDocumentCount += deletedSubcollectionDocumentCount + deletedServerOwnedDocumentCount + 1;
    }
  }
  return { deletedTripCount, deletedTripDocumentCount };
}

async function cleanupServerOwnedData(uid: string): Promise<{
  deletedGenerationJobCount: number;
  deletedBillingDocumentCount: number;
  deletedGenerationLockCount: number;
}> {
  const [generationJobs, billingDocuments, generationLocks] = await Promise.all([
    db.collection("generationJobs").where("requesterId", "==", uid).get(),
    db.collection(`users/${uid}/billing`).get(),
    db.collection(GENERATION_LOCKS_COLLECTION).where("requesterId", "==", uid).get(),
  ]);

  const references = [
    ...billingDocuments.docs.map((document) => document.ref),
    ...generationLocks.docs.map((document) => document.ref),
  ];
  await releaseGenerationReservations(generationJobs.docs);
  await deleteDocumentReferences(references);
  await deleteDocumentReferences(generationJobs.docs.map((document) => document.ref));

  return {
    deletedGenerationJobCount: generationJobs.size,
    deletedBillingDocumentCount: billingDocuments.size,
    deletedGenerationLockCount: generationLocks.size,
  };
}

/** Settles reservations for jobs that are about to be removed. */
async function releaseGenerationReservations(documents: DocumentSnapshot[]): Promise<void> {
  for (const document of documents) {
    const state = String(document.get("state") ?? "");
    if (ACTIVE_JOB_STATES.includes(state)) {
      await transitionActiveGenerationJob(document.ref, {
        state: "cancelled",
        failureClass: "cancelled",
        errorMessage: publicFailureMessage("cancelled"),
        cancelledBy: "system_cleanup",
        cancelledAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      });
    }
    const requesterId = typeof document.get("requesterId") === "string"
      ? document.get("requesterId") as string
      : "";
    const reservationMonth = typeof document.get("allowanceReservationMonth") === "string"
      ? document.get("allowanceReservationMonth") as string
      : null;
    await settleGenerationJobReservation(document.ref, requesterId, reservationMonth);
  }
}

async function deleteDocumentReferences(references: DocumentReference[]): Promise<void> {
  for (let offset = 0; offset < references.length; offset += ACCOUNT_CLEANUP_BATCH_SIZE) {
    const batch = db.batch();
    references
      .slice(offset, offset + ACCOUNT_CLEANUP_BATCH_SIZE)
      .forEach((reference) => batch.delete(reference));
    await batch.commit();
  }
}

/** Releases only the lock owned by [jobId], so a replacement job is safe. */
async function releaseGenerationLock(uid: string, tripId: string, jobId: string): Promise<void> {
  if (!uid || !tripId || !jobId) return;
  const reference = db.collection(GENERATION_LOCKS_COLLECTION).doc(generationLockId(uid, tripId));
  await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(reference);
    if (!snapshot.exists || snapshot.get("jobId") !== jobId) return;
    transaction.delete(reference);
  });
}

/** A transient cleanup failure must not turn a completed generation into a retry. */
async function bestEffortReleaseGenerationLock(uid: string, tripId: string, jobId: string): Promise<void> {
  try {
    await releaseGenerationLock(uid, tripId, jobId);
  } catch {
    // The lock carries the job expiry and is recovered by the next create or
    // scheduled purge. No provider or user data belongs in this diagnostic.
  }
}

export function normalizedInputHash(input: GenerationInput): string {
  return createHash("sha256").update(JSON.stringify(input)).digest("hex");
}

export { communityAction, moderateCommunity, communityTripChanged, communityMemberChanged, communityItineraryChanged, communityBlockChanged, communityRetention, communityPushCreated } from "./community";
