import fs from 'node:fs/promises';
import { createRequire } from 'node:module';

const projectId = 'triptandem';
const database = '(default)';
const firestoreBaseUrl = emulatorBaseUrl(process.env.FIRESTORE_EMULATOR_HOST, 'https://firestore.googleapis.com');
const authBaseUrl = emulatorBaseUrl(process.env.FIREBASE_AUTH_EMULATOR_HOST, 'https://identitytoolkit.googleapis.com');
// `emulators:exec` does not always export FUNCTIONS_EMULATOR_HOST to the
// child script, while the Functions emulator itself listens on 5001 by
// default. The fallback keeps this security test local and deterministic.
const functionsBaseUrl = emulatorBaseUrl(process.env.FUNCTIONS_EMULATOR_HOST ?? '127.0.0.1:5001', 'https://asia-southeast2-triptandem.cloudfunctions.net');
const authEmulator = Boolean(process.env.FIREBASE_AUTH_EMULATOR_HOST);
const googleServices = JSON.parse(await fs.readFile(
  new URL('../mobile/app/google-services.json', import.meta.url),
  'utf8',
));
const apiKey = googleServices.client[0].api_key[0].current_key;
const requireFromFunctions = createRequire(new URL('../functions/package.json', import.meta.url));
const { getApps, initializeApp } = requireFromFunctions('firebase-admin/app');
const { getFirestore } = requireFromFunctions('firebase-admin/firestore');

function emulatorBaseUrl(host, productionUrl) {
  if (!host) return productionUrl;
  return host.startsWith('http://') || host.startsWith('https://') ? host : `http://${host}`;
}

function authEndpoint(path) {
  return authEmulator
    ? `${authBaseUrl}/identitytoolkit.googleapis.com/v1/${path}`
    : `${authBaseUrl}/v1/${path}`;
}

async function jsonRequest(url, {method = 'GET', token, body} = {}) {
  const response = await fetch(url, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? {Authorization: `Bearer ${token}`} : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await response.text();
  let payload;
  try {
    payload = text ? JSON.parse(text) : null;
  } catch {
    payload = text;
  }
  return {status: response.status, payload};
}

async function anonymousSession() {
  const result = await jsonRequest(
    authEndpoint(`accounts:signUp?key=${apiKey}`),
    {method: 'POST', body: {returnSecureToken: true}},
  );
  if (result.status !== 200) throw new Error(`anonymous sign-up failed: ${JSON.stringify(result.payload)}`);
  return {uid: result.payload.localId, token: result.payload.idToken};
}

function firestoreValue(value) {
  if (value === null) return {nullValue: null};
  if (value && typeof value === 'object' && Object.hasOwn(value, 'timestampValue')) return value;
  if (typeof value === 'string') return {stringValue: value};
  if (typeof value === 'number' && Number.isInteger(value)) return {integerValue: String(value)};
  if (typeof value === 'boolean') return {booleanValue: value};
  if (Array.isArray(value)) return {arrayValue: {values: value.map(firestoreValue)}};
  throw new Error(`unsupported Firestore test value: ${JSON.stringify(value)}`);
}

function fields(values) {
  return Object.fromEntries(Object.entries(values).map(([key, value]) => [key, firestoreValue(value)]));
}

const now = () => ({timestampValue: new Date().toISOString()});
const documentUrl = (path) =>
  `${firestoreBaseUrl}/v1/projects/${projectId}/databases/${encodeURIComponent(database)}/documents/${path}`;

async function firestore(method, path, token, values) {
  return jsonRequest(documentUrl(path), {
    method,
    token,
    body: values === undefined ? undefined : {fields: fields(values)},
  });
}

async function callable(name, token, data) {
  const result = await jsonRequest(`${functionsBaseUrl}/${projectId}/asia-southeast2/${name}`, {
    method: 'POST',
    token,
    body: {data},
  });
  return result;
}

function updateWrite(path, values) {
  return {
    update: {
      name: `projects/${projectId}/databases/${database}/documents/${path}`,
      fields: fields(values),
    },
  };
}

function patchWrite(path, values) {
  return {
    update: {
      name: `projects/${projectId}/databases/${database}/documents/${path}`,
      fields: fields(values),
    },
    updateMask: {fieldPaths: Object.keys(values)},
  };
}

async function commit(token, writes) {
  return jsonRequest(
    `${firestoreBaseUrl}/v1/projects/${projectId}/databases/${encodeURIComponent(database)}/documents:commit`,
    {method: 'POST', token, body: {writes}},
  );
}

async function runQuery(token, structuredQuery) {
  const response = await fetch(
    `${firestoreBaseUrl}/v1/projects/${projectId}/databases/${encodeURIComponent(database)}/documents:runQuery`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? {Authorization: `Bearer ${token}`} : {}),
      },
      body: JSON.stringify({structuredQuery}),
    },
  );
  const raw = await response.text();
  let payload;
  try {
    payload = JSON.parse(raw);
  } catch {
    // The REST runQuery endpoint may return newline-delimited JSON, one result
    // per line, rather than a single JSON array.
    payload = raw.trim().split('\n').filter(Boolean).map((line) => JSON.parse(line));
  }
  return {status: response.status, payload};
}

function queryDocumentIds(result) {
  const entries = Array.isArray(result.payload) ? result.payload : [result.payload];
  return entries
    .map((entry) => entry?.document?.name?.split('/').pop())
    .filter(Boolean);
}

function expectStatus(label, result, expected) {
  if (result.status !== expected) {
    throw new Error(`${label}: expected ${expected}, got ${result.status}: ${JSON.stringify(result.payload)}`);
  }
  console.log(`PASS ${label} (${result.status})`);
}

function expectCallableError(label, result, expectedStatus, expectedText) {
  expectStatus(label, result, expectedStatus);
  if (!JSON.stringify(result.payload).includes(expectedText)) {
    throw new Error(`${label}: expected ${expectedText}, got ${JSON.stringify(result.payload)}`);
  }
}

const a = await anonymousSession();
const b = await anonymousSession();
const adminDb = process.env.FIRESTORE_EMULATOR_HOST
  ? getFirestore(getApps()[0] ?? initializeApp({projectId}))
  : null;
const profilePath = `users/${a.uid}`;
const profileBPath = `users/${b.uid}`;
let tripPath;
let ownerMemberPath;
const atomicTripPath = `trips/rules-smoke-atomic-${a.uid}`;
const fullTripPath = `trips/rules-smoke-full-${a.uid}`;
const capacityLoweringPath = `trips/rules-smoke-capacity-lowering-${a.uid}`;
const deletionTripPath = `trips/rules-smoke-delete-${a.uid}`;
const corruptSharedTripPath = `trips/rules-smoke-corrupt-shared-${a.uid}`;
const atomicOwnerMemberPath = `${atomicTripPath}/members/${a.uid}`;
const fullOwnerMemberPath = `${fullTripPath}/members/${a.uid}`;
const deletionOwnerMemberPath = `${deletionTripPath}/members/${a.uid}`;
const corruptSharedOwnerMemberPath = `${corruptSharedTripPath}/members/${a.uid}`;
const corruptSharedMemberPath = `${corruptSharedTripPath}/members/${b.uid}`;
const deletionItineraryPath = `${deletionTripPath}/itinerary/rules-smoke-delete-item`;
const deletionGenerationJobPath = `generationJobs/rules-smoke-delete-job-${a.uid}`;
const deletionGenerationLockPath = `generationLocks/rules-smoke-delete-lock-${a.uid}`;
const deletionGenerationUsagePath = `users/${a.uid}/billing/generationUsage`;
const previewGenerationJobPath = `generationJobs/rules-smoke-preview-job-${b.uid}`;
let memberPath;
const inviteId = 'a'.repeat(64);
let invitePath;
let itineraryPath;
let joinRequestPath;
const profile = {
  uid: a.uid,
  displayName: 'Rules Smoke A',
  homeRegion: 'Indonesia',
  primaryLanguage: 'English',
  ageConfirmed: true,
  visibility: 'private',
  additionalLanguages: ['Bahasa Indonesia'],
  interests: ['Food', 'Nature'],
  termsVersion: '2026-09-03',
  privacyVersion: '2026-09-03',
  consentedAt: now(),
  accountStatus: 'active',
  createdAt: now(),
  updatedAt: now(),
};
const profileB = {
  ...profile,
  uid: b.uid,
  displayName: 'Rules Smoke B',
};
const trip = {
  ownerId: a.uid,
  title: 'Rules Smoke Trip',
  destination: 'Singapore',
  startDate: '2026-10-01',
  endDate: '2026-10-03',
  destinationTimezone: 'Asia/Singapore',
  datesFlexible: false,
  visibility: 'private',
  capacity: 4,
  status: 'planning',
  activeMemberCount: 1,
  interests: ['Food'],
  coverColor: '#E8704A',
  revision: 0,
  createdAt: now(),
  updatedAt: now(),
};
const ownerMember = {
  userId: a.uid,
  displayName: 'Rules Smoke A',
  role: 'owner',
  status: 'active',
  joinedAt: now(),
  createdAt: now(),
  updatedAt: now(),
};
const tripInput = {
  title: trip.title,
  destination: trip.destination,
  startDate: trip.startDate,
  endDate: trip.endDate,
  destinationTimezone: trip.destinationTimezone,
  datesFlexible: trip.datesFlexible,
  visibility: trip.visibility,
  capacity: trip.capacity,
  status: trip.status,
  interests: trip.interests,
  coverColor: trip.coverColor,
};

try {
  expectStatus('owner creates profile', await firestore('PATCH', profilePath, a.token, profile), 200);
  expectStatus('owner reads profile', await firestore('GET', profilePath, a.token), 200);
  expectStatus('owner cannot delete profile directly', await firestore('DELETE', profilePath, a.token), 403);
  expectStatus('owner updates mutable profile fields without rewriting consent', await firestore('PATCH', profilePath, a.token, {
    ...profile,
    displayName: 'Rules Smoke A Updated',
    updatedAt: now(),
  }), 200);
  expectStatus('profile age gate rejects an unconfirmed profile', await firestore('PATCH', profileBPath, b.token, {
    ...profileB,
    ageConfirmed: false,
  }), 403);
  expectStatus('invitee creates age-confirmed profile', await firestore('PATCH', profileBPath, b.token, profileB), 200);
  expectStatus('profile list elements must be bounded strings', await firestore('PATCH', `users/rules-smoke-invalid-list-${a.uid}`, a.token, {
    ...profile,
    interests: ['Food', 42],
  }), 403);
  const createdTrip = await callable('createTrip', a.token, tripInput);
  expectStatus('server creates active trip transactionally', createdTrip, 200);
  const createdTripId = createdTrip.payload?.result?.tripId;
  if (typeof createdTripId !== 'string' || !createdTripId) throw new Error(`createTrip did not return an id: ${JSON.stringify(createdTrip)}`);
  tripPath = `trips/${createdTripId}`;
  ownerMemberPath = `${tripPath}/members/${a.uid}`;
  memberPath = `${tripPath}/members/${b.uid}`;
  invitePath = `${tripPath}/invites/${inviteId}`;
  itineraryPath = `${tripPath}/itinerary/rules-smoke-item`;
  joinRequestPath = `${tripPath}/joinRequests/rules-smoke-request`;
  expectStatus('owner reads trip', await firestore('GET', tripPath, a.token), 200);
  expectStatus('owner cannot delete a trip root directly', await firestore('DELETE', tripPath, a.token), 403);
  expectCallableError('deleteTrip rejects nested document IDs', await callable('deleteTrip', a.token, {
    tripId: `${createdTripId}/nested`,
  }), 400, 'tripId is invalid');
  const ownerQuery = await runQuery(a.token, {
    from: [{collectionId: 'trips'}],
    where: {fieldFilter: {
      field: {fieldPath: 'ownerId'},
      op: 'EQUAL',
      value: {stringValue: a.uid},
    }},
  });
  expectStatus('owner collection query returns only owned trips', ownerQuery, 200);
  if (!queryDocumentIds(ownerQuery).includes(createdTripId)) {
    throw new Error(`owner collection query omitted ${createdTripId}: ${JSON.stringify(ownerQuery.payload)}`);
  }
  console.log('PASS owner collection query includes the server-created trip');
  expectStatus('unscoped trip collection query is denied', await runQuery(a.token, {
    from: [{collectionId: 'trips'}],
  }), 403);
  const activeMemberQuery = await runQuery(a.token, {
    from: [{collectionId: 'members', allDescendants: true}],
    where: {compositeFilter: {
      op: 'AND',
      filters: [
        {fieldFilter: {
          field: {fieldPath: 'userId'},
          op: 'EQUAL',
          value: {stringValue: a.uid},
        }},
        {fieldFilter: {
          field: {fieldPath: 'status'},
          op: 'EQUAL',
          value: {stringValue: 'active'},
        }},
      ],
    }},
  });
  expectStatus('active membership collection-group query is allowed', activeMemberQuery, 200);
  if (!queryDocumentIds(activeMemberQuery).includes(a.uid)) {
    throw new Error(`active membership query omitted ${a.uid}: ${JSON.stringify(activeMemberQuery.payload)}`);
  }
  console.log('PASS active membership collection-group query includes the caller record');
  expectStatus('membership collection-group query without caller filter is denied', await runQuery(a.token, {
    from: [{collectionId: 'members', allDescendants: true}],
    where: {fieldFilter: {
      field: {fieldPath: 'status'},
      op: 'EQUAL',
      value: {stringValue: 'active'},
    }},
  }), 403);
  if (adminDb) {
    // A webhook mirror without a store-reported expiry is malformed. It must
    // not be treated as a lifetime Pro entitlement by the trusted callables.
    await adminDb.doc(`users/${a.uid}/billing/current`).set({
      entitlement: 'triptandem_pro',
      isActive: true,
      source: 'revenuecat_webhook',
    });
    expectCallableError('malformed Pro entitlement without expiry is rejected', await callable('createTrip', a.token, {
      ...tripInput,
      title: 'Malformed entitlement must not unlock Pro',
    }), 400, 'organizer_pro_required');
    await adminDb.doc(`users/${a.uid}/billing/current`).delete();
  }
  expectCallableError('free organizer cannot create a second active trip', await callable('createTrip', a.token, {
    ...tripInput,
    title: 'Second active trip should require Pro',
  }), 400, 'organizer_pro_required');
  expectStatus('client cannot create active trip directly', await firestore('PATCH', `trips/rules-smoke-direct-active-${a.uid}`, a.token, trip), 403);
  expectStatus('open-trip publishing stays disabled', await firestore('PATCH', `trips/rules-smoke-open-${a.uid}`, a.token, {
    ...trip,
    status: 'completed',
    visibility: 'open',
  }), 403);
  expectStatus('owner cannot write malformed ISO date to inactive trip', await firestore('PATCH', `trips/rules-smoke-invalid-date-${a.uid}`, a.token, {
    ...trip,
    title: 'Malformed date should be blocked',
    status: 'completed',
    startDate: '2026-1-01',
  }), 403);
  expectStatus('owner cannot write reversed date range to inactive trip', await firestore('PATCH', `trips/rules-smoke-reversed-date-${a.uid}`, a.token, {
    ...trip,
    title: 'Reversed date range should be blocked',
    status: 'completed',
    startDate: '2026-10-03',
    endDate: '2026-10-01',
  }), 403);
  expectStatus('owner cannot write a member count above capacity', await firestore('PATCH', `trips/rules-smoke-over-capacity-${a.uid}`, a.token, {
    ...trip,
    title: 'Over-capacity count should be blocked',
    status: 'completed',
    activeMemberCount: 5,
  }), 403);
  const capacityLoweringTrip = {
    ...trip,
    title: 'Capacity can be lowered without evicting members',
    status: 'completed',
    capacity: 4,
    activeMemberCount: 3,
  };
  expectStatus('owner creates a trip with a valid member count', await firestore('PATCH', capacityLoweringPath, a.token, capacityLoweringTrip), 200);
  expectStatus('owner may lower capacity below unchanged membership', await firestore('PATCH', capacityLoweringPath, a.token, {
    ...capacityLoweringTrip,
    capacity: 2,
    revision: 1,
  }), 200);
  expectStatus('client cannot atomically create active trip and owner membership', await commit(a.token, [
    updateWrite(atomicTripPath, {...trip, title: 'Atomic Rules Smoke Trip'}),
    updateWrite(atomicOwnerMemberPath, {
      userId: a.uid,
      displayName: 'Rules Smoke A',
      role: 'owner',
      status: 'active',
      joinedAt: now(),
      createdAt: now(),
      updatedAt: now(),
    }),
  ]), 403);
  const fullTrip = {...trip, title: 'Full Rules Smoke Trip', capacity: 2, status: 'completed'};
  expectStatus('owner creates capacity-bound trip', await firestore('PATCH', fullTripPath, a.token, fullTrip), 200);
  expectStatus('owner creates capacity-bound owner membership', await firestore('PATCH', fullOwnerMemberPath, a.token, ownerMember), 200);
  expectCallableError('free organizer cannot reactivate an inactive trip', await callable('updateTrip', a.token, {
    tripId: fullTripPath.slice('trips/'.length),
    expectedRevision: 0,
    trip: {...tripInput, title: fullTrip.title, capacity: 2, status: 'planning'},
  }), 400, 'organizer_pro_required');
  expectStatus('client cannot reactivate an inactive trip directly', await firestore('PATCH', fullTripPath, a.token, {
    ...fullTrip,
    status: 'planning',
    revision: 1,
  }), 403);
  expectCallableError('free organizer cannot request Pro-sized trip', await callable('createTrip', a.token, {
    ...tripInput,
    title: 'Pro-sized trip should require Pro',
    capacity: 7,
    status: 'completed',
  }), 400, 'organizer_pro_required');
  expectStatus('client cannot create Pro-sized trip directly', await firestore('PATCH', `trips/rules-smoke-direct-capacity-${a.uid}`, a.token, {
    ...fullTrip,
    title: 'Direct Pro-sized trip should be blocked',
    capacity: 7,
  }), 403);
  expectStatus('owner cannot change member counter without member mutation', await commit(a.token, [
    patchWrite(fullTripPath, {activeMemberCount: 2, revision: 1, updatedAt: now()}),
  ]), 403);
  expectStatus('server blocks counter above trip capacity', await commit(a.token, [
    patchWrite(fullTripPath, {activeMemberCount: 3, revision: 2, updatedAt: now()}),
  ]), 403);
  expectStatus('other user cannot read profile', await firestore('GET', profilePath, b.token), 403);
  expectStatus('other user cannot read trip', await firestore('GET', tripPath, b.token), 403);
  expectStatus('owner cannot add unknown field', await firestore('PATCH', `trips/rules-smoke-extra-${a.uid}`, a.token, {
    ...trip,
    extra: 'blocked',
  }), 403);
  expectStatus('owner cannot write invalid capacity type', await firestore('PATCH', `trips/rules-smoke-invalid-${a.uid}`, a.token, {
    ...trip,
    capacity: '4',
  }), 403);
  expectStatus('owner cannot create trip for another uid', await firestore('PATCH', `trips/rules-smoke-hijack-${a.uid}`, a.token, {
    ...trip,
    ownerId: b.uid,
  }), 403);
  expectStatus('owner cannot change profile uid', await firestore('PATCH', profilePath, a.token, {
    ...profile,
    uid: b.uid,
  }), 403);
  expectStatus('other user cannot update trip', await firestore('PATCH', tripPath, b.token, {
    ...trip,
    title: 'Hijacked',
  }), 403);
  expectStatus('server creates owner membership with trip', await firestore('GET', ownerMemberPath, a.token), 200);
  const invite = {
    tokenHash: 'a'.repeat(64),
    role: 'viewer',
    expiresAt: {timestampValue: new Date(Date.now() + 60 * 60 * 1000).toISOString()},
    maxUses: 2,
    uses: 0,
    revokedAt: null,
    createdBy: a.uid,
    createdAt: now(),
    updatedAt: now(),
  };
  expectStatus('owner creates invite', await firestore('PATCH', invitePath, a.token, invite), 200);
  expectStatus('invite collection enumeration is denied', await firestore('GET', `${tripPath}/invites`, a.token), 403);
  const member = {
    userId: b.uid,
    displayName: 'Rules Smoke B',
    inviteId,
    role: 'viewer',
    status: 'active',
    joinedAt: now(),
    createdAt: now(),
    updatedAt: now(),
  };
  expectStatus('invitee cannot increment the counter with an inactive membership', await commit(b.token, [
    patchWrite(tripPath, {activeMemberCount: 2, revision: 1, updatedAt: now()}),
    updateWrite(memberPath, {...member, status: 'invited'}),
    updateWrite(invitePath, {...invite, uses: 1, updatedAt: now()}),
  ]), 403);
  expectStatus('invitee cannot mutate trip details while joining', await commit(b.token, [
    patchWrite(tripPath, {activeMemberCount: 2, revision: 1, title: 'Joiner must not rename trip', updatedAt: now()}),
    updateWrite(memberPath, member),
    updateWrite(invitePath, {...invite, uses: 1, updatedAt: now()}),
  ]), 403);
  expectStatus('invitee accepts with atomic count and invite use', await commit(b.token, [
    patchWrite(tripPath, {activeMemberCount: 2, revision: 1, updatedAt: now()}),
    updateWrite(memberPath, member),
    updateWrite(invitePath, {...invite, uses: 1, updatedAt: now()}),
  ]), 200);
  expectStatus('active member reads trip', await firestore('GET', tripPath, b.token), 200);
  expectCallableError('account deletion blocks an active shared trip', await callable('deleteAccountProfile', a.token, {}), 400, 'ownership_required');
  expectStatus('active viewer cannot update membership', await firestore('PATCH', memberPath, b.token, member), 403);
  expectStatus('owner can promote active member to editor', await firestore('PATCH', memberPath, a.token, {
    ...member,
    role: 'editor',
    updatedAt: now(),
  }), 200);
  expectStatus('owner can change editor back to viewer', await firestore('PATCH', memberPath, a.token, {
    ...member,
    updatedAt: now(),
  }), 200);
  expectStatus('owner cannot remove active member without count update', await firestore('PATCH', memberPath, a.token, {
    ...member,
    status: 'removed',
    updatedAt: now(),
  }), 403);
  expectStatus('owner cannot delete active member without count update', await firestore('DELETE', memberPath, a.token), 403);
  expectCallableError('non-owner cannot remove a member through the server boundary', await callable('removeTripMember', b.token, {
    tripId: createdTripId,
    userId: a.uid,
  }), 403, 'organizer');
  expectStatus('owner cannot change the member counter without a member mutation', await commit(a.token, [
    patchWrite(tripPath, {activeMemberCount: 1, revision: 2, updatedAt: now()}),
  ]), 403);
  expectStatus('owner removes member through the trusted callable', await callable('removeTripMember', a.token, {
    tripId: createdTripId,
    userId: b.uid,
  }), 200);
  expectStatus('removed member loses trip access immediately', await firestore('GET', tripPath, b.token), 403);
  expectStatus('removed member can rejoin with a revalidated invite', await commit(b.token, [
    patchWrite(tripPath, {activeMemberCount: 2, revision: 3, updatedAt: now()}),
    updateWrite(memberPath, {...member, status: 'active', updatedAt: now()}),
    updateWrite(invitePath, {...invite, uses: 2, updatedAt: now()}),
  ]), 200);
  expectStatus('rejoined member can read trip', await firestore('GET', tripPath, b.token), 200);
  if (adminDb) {
    // Retained previews contain destination and generated place suggestions.
    // The callable must reauthorize the requester at read time, not only when
    // the job was created, so a later suspension or membership removal cannot
    // expose private trip context during the seven-day retention window.
    // Keep the REST-created timestamp fields intact; update only the role and
    // status fields with Admin SDK-native values so this fixture remains a
    // valid member document for the later Rules-backed leave transaction.
    await adminDb.doc(memberPath).update({role: 'editor', status: 'active', updatedAt: new Date()});
    await adminDb.doc(previewGenerationJobPath).set({
      schemaVersion: '2026-09-04.v1',
      requesterId: b.uid,
      tripId: createdTripId,
      scope: 'whole_trip',
      input: {scope: 'whole_trip', interests: [], lockedItemIds: []},
      state: 'succeeded',
      generatedItemCount: 1,
      rejectedItemCount: 0,
      preview: {
        schemaVersion: '2026-09-04.v1',
        assumptions: ['Planning ideas are not bookings.'],
        warnings: [],
        items: [{
          id: 'rules-smoke-preview-item',
          type: 'activity',
          title: 'National Museum',
          dayDate: '2026-10-02',
          startTimeLabel: '09:00',
          flexibleTime: false,
          durationMinutes: 60,
          place: 'National Museum',
        }],
        unverifiedInformationNotice: 'Check details before you go.',
      },
      expiresAt: new Date(Date.now() + 60 * 60 * 1000),
      createdAt: new Date(),
      updatedAt: new Date(),
    });
    try {
      expectStatus('active editor can read retained generation preview', await callable('getItineraryGenerationJob', b.token, {
        jobId: previewGenerationJobPath.slice('generationJobs/'.length),
      }), 200);
      await adminDb.doc(profileBPath).update({accountStatus: 'suspended', updatedAt: new Date()});
      expectCallableError('suspended requester cannot read retained generation preview', await callable('getItineraryGenerationJob', b.token, {
        jobId: previewGenerationJobPath.slice('generationJobs/'.length),
      }), 403, 'PERMISSION_DENIED');
      await adminDb.doc(profileBPath).update({accountStatus: 'active', updatedAt: new Date()});
      await adminDb.doc(memberPath).update({status: 'removed', updatedAt: new Date()});
      expectCallableError('removed editor cannot read retained generation preview', await callable('getItineraryGenerationJob', b.token, {
        jobId: previewGenerationJobPath.slice('generationJobs/'.length),
      }), 403, 'PERMISSION_DENIED');
      console.log('PASS retained generation preview rechecks profile and editor membership');
    } finally {
      await adminDb.doc(profileBPath).update({accountStatus: 'active', updatedAt: new Date()});
      await adminDb.doc(memberPath).update({role: 'viewer', status: 'active', updatedAt: new Date()});
      await adminDb.doc(previewGenerationJobPath).delete();
    }
  }
  const itinerary = {
    type: 'activity',
    title: 'Rules Smoke Activity',
    startTime: now(),
    flexibleTime: true,
    durationMinutes: 90,
    visibility: 'trip',
    status: 'planned',
    position: 0,
    revision: 0,
    createdAt: now(),
    updatedAt: now(),
  };
  expectStatus('owner creates itinerary item', await firestore('PATCH', itineraryPath, a.token, itinerary), 200);
  expectStatus('owner cannot write malformed itinerary day date', await firestore('PATCH', `${tripPath}/itinerary/rules-smoke-invalid-day-date`, a.token, {
    ...itinerary,
    dayDate: '2026/10/01',
  }), 403);
  expectStatus('owner cannot write itinerary day outside trip range', await firestore('PATCH', `${tripPath}/itinerary/rules-smoke-out-of-range-day`, a.token, {
    ...itinerary,
    dayDate: '2027-01-01',
  }), 403);
  expectStatus('owner cannot write invalid fixed clock label', await firestore('PATCH', `${tripPath}/itinerary/rules-smoke-invalid-clock`, a.token, {
    ...itinerary,
    flexibleTime: false,
    startTimeLabel: '25:00',
  }), 403);
  expectStatus('owner cannot write unknown flexible time label', await firestore('PATCH', `${tripPath}/itinerary/rules-smoke-invalid-flexible-label`, a.token, {
    ...itinerary,
    startTimeLabel: 'midday',
  }), 403);
  expectStatus('owner can write a case-insensitive flexible time label', await firestore('PATCH', `${tripPath}/itinerary/rules-smoke-flexible-case`, a.token, {
    ...itinerary,
    startTimeLabel: 'Morning',
  }), 200);
  expectStatus('owner cannot spoof AI attribution', await firestore('PATCH', `${tripPath}/itinerary/rules-smoke-spoof`, a.token, {
    ...itinerary,
    generatedBy: 'triptandem_ai',
    generationJobId: 'forged-job',
  }), 403);
  expectStatus('active member reads itinerary', await firestore('GET', itineraryPath, b.token), 200);
  expectStatus('viewer cannot update itinerary', await firestore('PATCH', itineraryPath, b.token, {
    ...itinerary,
    title: 'Hijacked Activity',
    revision: 1,
  }), 403);
  expectStatus('member cannot mutate trip details while leaving', await commit(b.token, [
    patchWrite(tripPath, {activeMemberCount: 1, revision: 4, visibility: 'unlisted', updatedAt: now()}),
    updateWrite(memberPath, {...member, status: 'removed', updatedAt: now()}),
  ]), 403);
  expectStatus('member can leave with atomic count decrement', await commit(b.token, [
    patchWrite(tripPath, {activeMemberCount: 1, revision: 4, updatedAt: now()}),
    updateWrite(memberPath, {...member, status: 'removed', updatedAt: now()}),
  ]), 200);
  expectStatus('left member cannot read trip', await firestore('GET', tripPath, b.token), 403);
  const joinRequest = {
    applicantId: b.uid,
    message: 'Rules smoke request',
    status: 'pending',
    createdAt: now(),
    updatedAt: now(),
  };
  expectStatus('applicant cannot create join request directly', await firestore('PATCH', joinRequestPath, b.token, joinRequest), 403);
  expectStatus('owner cannot read join request directly', await firestore('GET', joinRequestPath, a.token), 403);
  expectStatus('owner cannot approve join request directly', await firestore('PATCH', joinRequestPath, a.token, {
    ...joinRequest,
    status: 'approved',
    updatedAt: now(),
  }), 403);
  if (adminDb) {
    // Seed a trusted lifecycle record to preserve trip-graph deletion coverage.
    await adminDb.doc(joinRequestPath).set({ ...joinRequest, createdAt: new Date(), updatedAt: new Date() });
    // The owner-trip aggregate may be repaired incorrectly after an import or
    // manual admin edit. A counter of one must still be rejected when two
    // active member documents exist, otherwise account deletion could strand
    // the collaborator. This fixture exercises the server-side integrity
    // check before it is removed again through the Admin SDK.
    await adminDb.doc(corruptSharedTripPath).set({
      ...trip,
      title: 'Corrupt aggregate shared trip',
      status: 'planning',
      activeMemberCount: 1,
    });
    await adminDb.doc(corruptSharedOwnerMemberPath).set({
      userId: a.uid,
      displayName: 'Rules Smoke A',
      role: 'owner',
      status: 'active',
      joinedAt: new Date(),
      createdAt: new Date(),
      updatedAt: new Date(),
    });
    await adminDb.doc(corruptSharedMemberPath).set({
      ...member,
      status: 'active',
      role: 'viewer',
    });
    expectCallableError('account deletion blocks a corrupt shared-trip aggregate', await callable('deleteAccountProfile', a.token, {}), 400, 'ownership_required');
    await adminDb.doc(corruptSharedMemberPath).delete();
    await adminDb.doc(corruptSharedOwnerMemberPath).delete();
    await adminDb.doc(corruptSharedTripPath).delete();

    // Parent deletion must clean nested documents, and inactive/cancelled trips
    // must remain read-only to direct clients while the server-owned cleanup
    // runs. Seed a nested item with Admin SDK credentials so this covers the
    // graph-drain path without weakening client Rules.
    const deletionTrip = {
      ...trip,
      title: 'Server-owned deletion smoke trip',
      status: 'completed',
      activeMemberCount: 1,
      revision: 0,
    };
    expectStatus('owner creates inactive trip for deletion test', await firestore('PATCH', deletionTripPath, a.token, deletionTrip), 200);
    expectStatus('owner creates deletion-test membership', await firestore('PATCH', deletionOwnerMemberPath, a.token, ownerMember), 200);
    await adminDb.doc(deletionItineraryPath).set({
      type: 'activity',
      title: 'Seeded deletion item',
      startTime: new Date(),
      durationMinutes: 60,
      visibility: 'trip',
      status: 'planned',
      position: 0,
      revision: 0,
      createdAt: new Date(),
      updatedAt: new Date(),
    });
    const reservationMonth = `${new Date().getUTCFullYear()}-${String(new Date().getUTCMonth() + 1).padStart(2, '0')}`;
    await adminDb.doc(deletionGenerationUsagePath).set({
      month: reservationMonth,
      successfulCount: 0,
      reservedCount: 1,
      updatedAt: new Date(),
    });
    await adminDb.doc(deletionGenerationJobPath).set({
      schemaVersion: 1,
      requesterId: a.uid,
      tripId: deletionTripPath.slice('trips/'.length),
      scope: 'whole_trip',
      input: {scope: 'whole_trip'},
      // Use a pre-cancelled job so the Firestore trigger does not attempt a
      // provider call during this deletion-only emulator fixture.
      state: 'cancelled',
      failureClass: 'cancelled',
      allowanceReservationMonth: reservationMonth,
      expiresAt: new Date(Date.now() + 60 * 60 * 1000),
      createdAt: new Date(),
      updatedAt: new Date(),
    });
    await adminDb.doc(deletionGenerationLockPath).set({
      requesterId: a.uid,
      tripId: deletionTripPath.slice('trips/'.length),
      jobId: deletionGenerationJobPath.slice('generationJobs/'.length),
      expiresAt: new Date(Date.now() + 60 * 60 * 1000),
    });
    expectStatus('owner can cancel the deletion-test trip', await firestore('PATCH', deletionTripPath, a.token, {
      ...deletionTrip,
      status: 'cancelled',
      revision: 1,
      updatedAt: now(),
    }), 200);
    expectStatus('cancelled trip itinerary is read-only', await firestore('PATCH', deletionItineraryPath, a.token, {
      ...itinerary,
      title: 'Cancelled trip must stay read-only',
      revision: 1,
    }), 403);
    expectCallableError('owner cannot remove members from a cancelled trip', await callable('removeTripMember', a.token, {
      tripId: deletionTripPath.slice('trips/'.length),
      userId: b.uid,
    }), 400, 'trip_read_only');
    const deletedTrip = await callable('deleteTrip', a.token, {
      tripId: deletionTripPath.slice('trips/'.length),
    });
    expectStatus('server-owned deleteTrip callable cleans the graph', deletedTrip, 200);
    if (deletedTrip.payload?.result?.deleted !== true || deletedTrip.payload?.result?.deletedDocumentCount !== 5) {
      throw new Error(`deleteTrip callable did not report the expected graph cleanup: ${JSON.stringify(deletedTrip.payload)}`);
    }
    for (const path of [deletionTripPath, deletionOwnerMemberPath, deletionItineraryPath, deletionGenerationJobPath, deletionGenerationLockPath]) {
      const snapshot = await adminDb.doc(path).get();
      if (snapshot.exists) throw new Error(`deleteTrip callable left data behind at ${path}`);
    }
    const usageAfterDeletion = await adminDb.doc(deletionGenerationUsagePath).get();
    if (!usageAfterDeletion.exists || usageAfterDeletion.get('reservedCount') !== 0) {
      throw new Error('deleteTrip callable did not release the generation reservation');
    }
    const repeatedDeletedTrip = await callable('deleteTrip', a.token, {
      tripId: deletionTripPath.slice('trips/'.length),
    });
    expectStatus('server-owned deleteTrip callable is idempotent', repeatedDeletedTrip, 200);
    if (repeatedDeletedTrip.payload?.result?.deleted !== false) {
      throw new Error(`repeated deleteTrip should report deleted=false: ${JSON.stringify(repeatedDeletedTrip.payload)}`);
    }
    console.log('PASS deleteTrip callable removes nested trip data and is idempotent');
  }
  expectStatus('unauthenticated read is denied', await firestore('GET', `trips/rules-smoke-missing-${a.uid}`), 403);
  expectStatus('report reads are denied', await firestore('GET', `reports/rules-smoke-${a.uid}`, a.token), 403);
  if (adminDb) {
    await adminDb.doc(`users/${a.uid}/billing/current`).set({
      entitlement: 'triptandem_pro',
      isActive: true,
      source: 'revenuecat_webhook',
    });
    await adminDb.doc(`generationLocks/rules-smoke-${a.uid}`).set({
      requesterId: a.uid,
      tripId: 'rules-smoke-lock-trip',
      jobId: 'rules-smoke-lock-job',
      expiresAt: new Date(Date.now() + 60 * 60 * 1000),
    });
  }
  const firstCleanup = await callable('cleanupAccountData', a.token, {});
  expectStatus('account cleanup callable is authenticated', firstCleanup, 200);
  const firstCleanupResult = firstCleanup.payload?.result;
  const expectedGenerationJobCount = 0;
  const expectedBillingDocumentCount = adminDb ? 2 : 0;
  const expectedGenerationLockCount = adminDb ? 1 : 0;
  if (firstCleanupResult?.deletedGenerationJobCount !== expectedGenerationJobCount
      || firstCleanupResult?.deletedBillingDocumentCount !== expectedBillingDocumentCount
      || firstCleanupResult?.deletedGenerationLockCount !== expectedGenerationLockCount) {
    throw new Error(`unexpected smoke-account cleanup counts: ${JSON.stringify(firstCleanupResult)}`);
  }
  if (adminDb) {
    const billingAfterCleanup = await adminDb.doc(`users/${a.uid}/billing/current`).get();
    if (billingAfterCleanup.exists) {
      throw new Error('account cleanup callable left server-owned billing metadata behind');
    }
    const lockAfterCleanup = await adminDb.doc(`generationLocks/rules-smoke-${a.uid}`).get();
    if (lockAfterCleanup.exists) {
      throw new Error('account cleanup callable left server-owned generation lock metadata behind');
    }
    console.log('PASS account cleanup removes server-owned metadata');
  }
  expectStatus('account cleanup callable is idempotent', await callable('cleanupAccountData', a.token, {}), 200);
  const deletedProfile = await callable('deleteAccountProfile', a.token, {});
  expectStatus('account profile deletion uses the trusted callable', deletedProfile, 200);
  if (deletedProfile.payload?.result?.deleted !== true) {
    throw new Error(`account profile deletion did not report deleted=true: ${JSON.stringify(deletedProfile.payload)}`);
  }
  if (adminDb) {
    // The fixture owns the primary trip plus the completed capacity-lowering
    // and capacity-bound trips created above; all three roots must be removed.
    if (deletedProfile.payload?.result?.deletedTripCount !== 3
        || deletedProfile.payload?.result?.deletedTripDocumentCount !== 10) {
      throw new Error(`account profile deletion did not report trip-graph cleanup: ${JSON.stringify(deletedProfile.payload)}`);
    }
    const profileAfterDeletion = await adminDb.doc(profilePath).get();
    if (profileAfterDeletion.exists) {
      throw new Error('account profile deletion callable left the profile document behind');
    }
    const tripGraph = [
      tripPath,
      ownerMemberPath,
      memberPath,
      invitePath,
      itineraryPath,
      `${tripPath}/itinerary/rules-smoke-flexible-case`,
      joinRequestPath,
      capacityLoweringPath,
      fullTripPath,
      fullOwnerMemberPath,
    ];
    for (const path of tripGraph) {
      const snapshot = await adminDb.doc(path).get();
      if (snapshot.exists) throw new Error(`account profile deletion left trip data behind at ${path}`);
    }
    console.log('PASS account profile deletion removes the profile document');
    console.log('PASS account profile deletion removes the owned trip graph');
  }
  const repeatedProfileDeletion = await callable('deleteAccountProfile', a.token, {});
  expectStatus('account profile deletion callable is idempotent', repeatedProfileDeletion, 200);
  if (repeatedProfileDeletion.payload?.result?.deleted !== false) {
    throw new Error(`repeated account profile deletion should report deleted=false: ${JSON.stringify(repeatedProfileDeletion.payload)}`);
  }
  console.log('Firestore rules smoke test passed.');
} finally {
  if (adminDb) {
    if (profilePath) await adminDb.doc(profilePath).delete();
    if (profileBPath) await adminDb.doc(profileBPath).delete();
    await adminDb.doc(previewGenerationJobPath).delete();
  } else {
    if (profilePath) await firestore('DELETE', profilePath, a.token);
    if (profileBPath) await firestore('DELETE', profileBPath, b.token);
  }
  if (joinRequestPath) await firestore('DELETE', joinRequestPath, a.token);
  if (itineraryPath) await firestore('DELETE', itineraryPath, a.token);
  if (invitePath) await firestore('DELETE', invitePath, a.token);
  if (ownerMemberPath) await firestore('DELETE', ownerMemberPath, a.token);
  if (memberPath) await firestore('DELETE', memberPath, a.token);
  if (tripPath) await firestore('DELETE', tripPath, a.token);
  await firestore('DELETE', atomicOwnerMemberPath, a.token);
  await firestore('DELETE', fullOwnerMemberPath, a.token);
  await firestore('DELETE', fullTripPath, a.token);
  await firestore('DELETE', capacityLoweringPath, a.token);
  if (adminDb) {
    await adminDb.doc(corruptSharedMemberPath).delete();
    await adminDb.doc(corruptSharedOwnerMemberPath).delete();
    await adminDb.doc(corruptSharedTripPath).delete();
  }
  await firestore('DELETE', atomicTripPath, a.token);
  await jsonRequest(authEndpoint(`accounts:delete?key=${apiKey}`), {
    method: 'POST',
    body: {idToken: a.token},
  });
  await jsonRequest(authEndpoint(`accounts:delete?key=${apiKey}`), {
    method: 'POST',
    body: {idToken: b.token},
  });
}
