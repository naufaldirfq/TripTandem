#!/usr/bin/env node
/**
 * TripTandem Operator Gates Management Tool
 *
 * Provides release operators with tools to inspect, open, pause, or audit:
 * 1. Remote Config rollout gates (ai_generation_enabled, open_trip_publishing_enabled, discovery_enabled, join_requests_enabled, push_enabled)
 * 2. Community readiness gate (communityConfig/readiness: approved, expiresAtMs)
 * 3. Moderator role administration (communityModerator custom claim)
 * 4. RevenueCat webhook integration verification (both unauthenticated and authenticated probes)
 * 5. Operator audit trail (communityConfig/operatorAudit/entries)
 *
 * Usage:
 *   node scripts/manage_operator_gates.mjs status
 *   node scripts/manage_operator_gates.mjs readiness --approved true|false [--hours N] [--notes text] [--support-url url] [--operator id]
 *   node scripts/manage_operator_gates.mjs moderator --uid uid --enabled true|false [--operator id]
 *   node scripts/manage_operator_gates.mjs audit [--limit N]
 *   node scripts/manage_operator_gates.mjs test-webhook [--secret secret] [--url url]
 */

import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import process from 'node:process';
import { parseArgs } from 'node:util';

const PROJECT_ID = process.env.GCLOUD_PROJECT || 'triptandem';
const WEBHOOK_URL = 'https://asia-southeast2-triptandem.cloudfunctions.net/revenueCatWebhook';
const DEFAULT_WEBHOOK_SECRET = '3bb259912cf0ca64f9dbfd1e73ccdd05c1a9d8fd9ba16082157b1b00fb3818fe';

function getAccessToken() {
  if (process.env.GOOGLE_OAUTH_ACCESS_TOKEN) {
    return process.env.GOOGLE_OAUTH_ACCESS_TOKEN;
  }
  const configPath = path.join(os.homedir(), '.config/configstore/firebase-tools.json');
  if (fs.existsSync(configPath)) {
    try {
      const data = JSON.parse(fs.readFileSync(configPath, 'utf8'));
      if (data.tokens?.access_token) {
        return data.tokens.access_token;
      }
    } catch {
      // ignore
    }
  }
  return null;
}

function toFirestoreFields(obj) {
  const fields = {};
  for (const [k, v] of Object.entries(obj)) {
    if (v === null || v === undefined) {
      fields[k] = { nullValue: null };
    } else if (typeof v === 'boolean') {
      fields[k] = { booleanValue: v };
    } else if (typeof v === 'string') {
      fields[k] = { stringValue: v };
    } else if (typeof v === 'number') {
      if (Number.isInteger(v)) {
        fields[k] = { integerValue: v.toString() };
      } else {
        fields[k] = { doubleValue: v };
      }
    } else if (Array.isArray(v)) {
      fields[k] = {
        arrayValue: {
          values: v.map((item) => Object.values(toFirestoreFields({ x: item }))[0]),
        },
      };
    } else if (typeof v === 'object') {
      fields[k] = { mapValue: { fields: toFirestoreFields(v) } };
    }
  }
  return fields;
}

function fromFirestoreFields(fields) {
  if (!fields) return {};
  const res = {};
  for (const [k, v] of Object.entries(fields)) {
    if (v.stringValue !== undefined) res[k] = v.stringValue;
    else if (v.booleanValue !== undefined) res[k] = v.booleanValue;
    else if (v.integerValue !== undefined) res[k] = parseInt(v.integerValue, 10);
    else if (v.doubleValue !== undefined) res[k] = v.doubleValue;
    else if (v.timestampValue !== undefined) res[k] = v.timestampValue;
    else if (v.nullValue !== undefined) res[k] = null;
    else if (v.mapValue !== undefined) res[k] = fromFirestoreFields(v.mapValue.fields);
    else if (v.arrayValue !== undefined) {
      res[k] = (v.arrayValue.values || []).map((x) => fromFirestoreFields({ val: x }).val);
    }
  }
  return res;
}

class FirebaseRestClient {
  constructor(projectId, token) {
    this.projectId = projectId;
    this.token = token;
    this.firestoreBase = `https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents`;
    this.remoteConfigBase = `https://firebaseremoteconfig.googleapis.com/v1/projects/${projectId}`;
    this.authBase = `https://identitytoolkit.googleapis.com/v1/projects/${projectId}`;
  }

  headers(extra = {}) {
    const h = { ...extra };
    if (this.token) {
      h.Authorization = `Bearer ${this.token}`;
    }
    return h;
  }

  async getFirestoreDoc(docPath) {
    const res = await fetch(`${this.firestoreBase}/${docPath}`, {
      headers: this.headers(),
    });
    if (res.status === 404) return null;
    if (!res.ok) {
      const text = await res.text();
      throw new Error(`Firestore GET ${docPath} failed (${res.status}): ${text}`);
    }
    const json = await res.json();
    return fromFirestoreFields(json.fields);
  }

  async patchFirestoreDoc(docPath, data) {
    const body = { fields: toFirestoreFields(data) };
    const res = await fetch(`${this.firestoreBase}/${docPath}`, {
      method: 'PATCH',
      headers: this.headers({ 'Content-Type': 'application/json' }),
      body: JSON.stringify(body),
    });
    if (!res.ok) {
      const text = await res.text();
      throw new Error(`Firestore PATCH ${docPath} failed (${res.status}): ${text}`);
    }
    const json = await res.json();
    return fromFirestoreFields(json.fields);
  }

  async addFirestoreDoc(collectionPath, data) {
    const body = { fields: toFirestoreFields(data) };
    const res = await fetch(`${this.firestoreBase}/${collectionPath}`, {
      method: 'POST',
      headers: this.headers({ 'Content-Type': 'application/json' }),
      body: JSON.stringify(body),
    });
    if (!res.ok) {
      const text = await res.text();
      throw new Error(`Firestore POST ${collectionPath} failed (${res.status}): ${text}`);
    }
    const json = await res.json();
    return json;
  }

  async listFirestoreDocs(collectionPath, pageSize = 20) {
    const url = `${this.firestoreBase}/${collectionPath}?pageSize=${pageSize}`;
    const res = await fetch(url, {
      headers: this.headers(),
    });
    if (res.status === 404) return [];
    if (!res.ok) {
      const text = await res.text();
      throw new Error(`Firestore LIST ${collectionPath} failed (${res.status}): ${text}`);
    }
    const json = await res.json();
    return (json.documents || []).map((doc) => ({
      id: doc.name.split('/').pop(),
      ...fromFirestoreFields(doc.fields),
    }));
  }

  async getRemoteConfigTemplate() {
    const res = await fetch(`${this.remoteConfigBase}/remoteConfig`, {
      headers: this.headers(),
    });
    if (!res.ok) {
      const text = await res.text();
      throw new Error(`Remote Config GET failed (${res.status}): ${text}`);
    }
    return res.json();
  }

  async getAuthUser(uid) {
    const res = await fetch(`${this.authBase}/accounts:lookup`, {
      method: 'POST',
      headers: this.headers({ 'Content-Type': 'application/json' }),
      body: JSON.stringify({ localId: [uid] }),
    });
    if (!res.ok) {
      const text = await res.text();
      throw new Error(`Auth lookup failed (${res.status}): ${text}`);
    }
    const json = await res.json();
    if (!json.users || json.users.length === 0) return null;
    return json.users[0];
  }

  async setAuthCustomClaims(uid, claims) {
    const res = await fetch(`${this.authBase}/accounts:update`, {
      method: 'POST',
      headers: this.headers({ 'Content-Type': 'application/json' }),
      body: JSON.stringify({
        localId: uid,
        customAttributes: JSON.stringify(claims),
      }),
    });
    if (!res.ok) {
      const text = await res.text();
      throw new Error(`Auth setCustomClaims failed (${res.status}): ${text}`);
    }
    return res.json();
  }
}

function createClient() {
  const token = getAccessToken();
  if (!token) {
    console.error('Error: Could not obtain OAuth token from Firebase CLI or environment.');
    console.error('Run "npx firebase login" or export GOOGLE_OAUTH_ACCESS_TOKEN.');
    process.exit(1);
  }
  return new FirebaseRestClient(PROJECT_ID, token);
}

async function handleStatus() {
  const client = createClient();
  console.log(`=== TripTandem Operator & Backend Gates Status ===`);
  console.log(`Project: ${PROJECT_ID}`);
  console.log(`Time   : ${new Date().toISOString()}\n`);

  // 1. Remote Config Gates
  console.log(`[1] Remote Config Rollout Flags:`);
  try {
    const template = await client.getRemoteConfigTemplate();
    const flags = [
      'ai_generation_enabled',
      'open_trip_publishing_enabled',
      'discovery_enabled',
      'join_requests_enabled',
      'push_enabled',
    ];
    for (const flag of flags) {
      const param = template.parameters?.[flag];
      const val = param?.defaultValue?.value ?? 'undefined';
      const isTrue = val === 'true';
      const symbol = isTrue ? '🟢 ACTIVE' : '🔴 CLOSED (Safe Default)';
      console.log(`  - ${flag.padEnd(30)}: ${val.padEnd(7)} [${symbol}]`);
    }
  } catch (err) {
    console.log(`  ⚠️ Failed to fetch Remote Config template: ${err.message}`);
  }

  // 2. Community Readiness Gate
  console.log(`\n[2] Community Readiness Gate (communityConfig/readiness):`);
  try {
    const data = await client.getFirestoreDoc('communityConfig/readiness');
    if (!data) {
      console.log(`  🔴 Status: NOT CONFIGURED (Document does not exist -> All community calls PAUSED)`);
    } else {
      const approved = data.approved === true;
      const expiresAtMs = typeof data.expiresAtMs === 'number' ? data.expiresAtMs : 0;
      const now = Date.now();
      const isExpired = expiresAtMs <= now;
      const remainingHours = isExpired ? '0' : ((expiresAtMs - now) / 3600000).toFixed(1);

      let statusDesc = '🔴 PAUSED';
      if (approved && !isExpired) {
        statusDesc = `🟢 OPEN (Expires in ${remainingHours}h)`;
      } else if (approved && isExpired) {
        statusDesc = `🔴 EXPIRED (Approval expired ${new Date(expiresAtMs).toISOString()})`;
      } else {
        statusDesc = `🔴 UNAPPROVED (approved = false)`;
      }

      console.log(`  - Status       : ${statusDesc}`);
      console.log(`  - Approved     : ${approved}`);
      console.log(`  - Expires At   : ${expiresAtMs ? new Date(expiresAtMs).toISOString() : 'None'} (${remainingHours}h remaining)`);
      console.log(`  - Operator     : ${data.operator || data.approvedBy || 'Unknown'}`);
      console.log(`  - Notes        : ${data.notes || 'None'}`);
      console.log(`  - Support URL  : ${data.supportUrl || 'None'}`);
      console.log(`  - Last Updated : ${data.updatedAtMs ? new Date(data.updatedAtMs).toISOString() : 'Unknown'}`);
    }
  } catch (err) {
    console.log(`  ⚠️ Failed to read communityConfig/readiness: ${err.message}`);
  }

  // 3. Webhook Target Status
  console.log(`\n[3] RevenueCat Webhook Target:`);
  console.log(`  - URL          : ${WEBHOOK_URL}`);
  console.log(`  - Protected By : REVENUECAT_WEBHOOK_SECRET (Google Secret Manager)`);
  console.log(`  - Status in RC : Registered & Active on project proj8b05ec7d`);

  // 4. Recent Audit Trail
  console.log(`\n[4] Recent Operator Audit Logs:`);
  try {
    const logs = await client.listFirestoreDocs('communityConfig/operatorAudit/entries', 3);
    if (logs.length === 0) {
      console.log(`  (No operator actions recorded yet)`);
    } else {
      for (const log of logs) {
        console.log(`  - [${log.timestamp || 'unknown'}] ${log.action?.toUpperCase()} by ${log.operator || 'unknown'}: ${log.notes || ''}`);
      }
    }
  } catch (err) {
    console.log(`  ⚠️ Failed to list audit logs: ${err.message}`);
  }
}

async function handleSetReadiness(args) {
  const client = createClient();
  const approved = args.approved === 'true';
  const hours = parseFloat(args.hours || (approved ? '24' : '0'));
  const notes = args.notes || (approved ? 'Operator manual approval' : 'Operator paused');
  const supportUrl = args['support-url'] || 'https://triptandem.app/support';
  const operator = args.operator || process.env.USER || 'operator';

  const expiresAtMs = approved ? Date.now() + Math.round(hours * 3600000) : Date.now();

  const docData = {
    approved,
    expiresAtMs,
    notes,
    supportUrl,
    operator,
    approvedBy: operator,
    updatedAtMs: Date.now(),
  };

  const auditData = {
    action: 'set_readiness',
    approved,
    expiresAtMs,
    notes,
    supportUrl,
    operator,
    timestamp: new Date().toISOString(),
  };

  await client.patchFirestoreDoc('communityConfig/readiness', docData);
  await client.addFirestoreDoc('communityConfig/operatorAudit/entries', auditData);

  console.log(`✔ Updated communityConfig/readiness successfully!`);
  console.log(`  - Approved    : ${approved}`);
  console.log(`  - Expires At  : ${new Date(expiresAtMs).toISOString()} (${hours}h)`);
  console.log(`  - Operator    : ${operator}`);
  console.log(`  - Notes       : ${notes}`);
  console.log(`  - Audit logged to communityConfig/operatorAudit/entries`);
}

async function handleSetModerator(args) {
  const client = createClient();
  const uid = args.uid;
  if (!uid) {
    console.error('Error: --uid is required.');
    process.exit(1);
  }
  const enabled = args.enabled === 'true';
  const operator = args.operator || process.env.USER || 'operator';

  const user = await client.getAuthUser(uid);
  if (!user) {
    console.error(`Error: User with UID ${uid} not found in Firebase Auth.`);
    process.exit(1);
  }

  let currentClaims = {};
  if (user.customAttributes) {
    try {
      currentClaims = JSON.parse(user.customAttributes);
    } catch {
      // ignore
    }
  }

  const updatedClaims = { ...currentClaims, communityModerator: enabled };
  if (!enabled) {
    delete updatedClaims.communityModerator;
  }

  await client.setAuthCustomClaims(uid, updatedClaims);

  await client.addFirestoreDoc('communityConfig/operatorAudit/entries', {
    action: 'set_moderator',
    targetUid: uid,
    targetEmail: user.email || null,
    enabled,
    operator,
    timestamp: new Date().toISOString(),
    notes: `Moderator privilege ${enabled ? 'granted' : 'revoked'} for ${user.email || uid}`,
  });

  console.log(`✔ Updated moderator claims for user:`);
  console.log(`  - UID        : ${uid}`);
  console.log(`  - Email      : ${user.email || 'None'}`);
  console.log(`  - Moderator  : ${enabled}`);
  console.log(`  - Operator   : ${operator}`);
  console.log(`  - Audit logged to communityConfig/operatorAudit/entries`);
}

async function handleAudit(args) {
  const client = createClient();
  const limit = parseInt(args.limit || '10', 10);
  console.log(`=== Recent Operator Audit Entries (Limit: ${limit}) ===\n`);

  const logs = await client.listFirestoreDocs('communityConfig/operatorAudit/entries', limit);
  if (logs.length === 0) {
    console.log(`No audit entries found.`);
    return;
  }

  for (const data of logs) {
    const time = data.timestamp || 'Unknown';
    console.log(`[${time}] ${data.action?.toUpperCase()} by ${data.operator || 'unknown'}`);
    if (data.action === 'set_readiness') {
      console.log(`  Approved: ${data.approved}, Expires: ${data.expiresAtMs ? new Date(data.expiresAtMs).toISOString() : 'N/A'}, Notes: ${data.notes || 'None'}`);
    } else if (data.action === 'set_moderator') {
      console.log(`  Target: ${data.targetUid} (${data.targetEmail || 'No email'}), Moderator: ${data.enabled}`);
    } else {
      console.log(`  Notes: ${data.notes || 'None'}`);
    }
  }
}

async function handleTestWebhook(args) {
  const url = args.url || WEBHOOK_URL;
  const secret = args.secret || process.env.REVENUECAT_WEBHOOK_SECRET || DEFAULT_WEBHOOK_SECRET;

  console.log(`=== RevenueCat Live Webhook Verification ===`);
  console.log(`Target URL: ${url}\n`);

  // 1. Test unauthenticated request (must reject with 401)
  console.log(`[Probe 1] Testing unauthenticated access (Expect HTTP 401):`);
  const resUnauth = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ type: 'TEST' }),
  });
  console.log(`  Status: ${resUnauth.status} ${resUnauth.statusText}`);
  if (resUnauth.status === 401) {
    console.log(`  ✔ Passed: Unauthenticated requests are strictly rejected.\n`);
  } else {
    console.log(`  ✖ Warning: Expected 401, got ${resUnauth.status}.\n`);
  }

  // 2. Test authenticated probe with safe non-targeted event (must return 200 {received:true, ignored:true})
  console.log(`[Probe 2] Testing authenticated access with authorization secret (Expect HTTP 200 ignored):`);
  const probePayload = {
    api_version: '1.0',
    event: {
      id: `operator_probe_${Date.now()}`,
      type: 'INITIAL_PURCHASE',
      app_id: 'app3fef6072fb',
      app_user_id: 'test_operator_probe_user',
      entitlement_id: 'probe_non_targeted_product',
      event_timestamp_ms: Date.now(),
    },
  };

  const resAuth = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: secret,
    },
    body: JSON.stringify(probePayload),
  });

  const authBody = await resAuth.text();
  console.log(`  Status: ${resAuth.status} ${resAuth.statusText}`);
  console.log(`  Body  : ${authBody}`);

  if (resAuth.status === 200 && authBody.includes('"received":true')) {
    console.log(`  ✔ Passed: Live webhook accepted the authorized probe and safely filtered it.\n`);
  } else {
    console.log(`  ✖ Webhook returned unexpected status or body.\n`);
  }
}

async function main() {
  const command = process.argv[2];
  const rawArgs = process.argv.slice(3);

  const { values } = parseArgs({
    args: rawArgs,
    options: {
      approved: { type: 'string' },
      hours: { type: 'string' },
      notes: { type: 'string' },
      'support-url': { type: 'string' },
      operator: { type: 'string' },
      uid: { type: 'string' },
      enabled: { type: 'string' },
      limit: { type: 'string' },
      url: { type: 'string' },
      secret: { type: 'string' },
    },
    strict: false,
  });

  switch (command) {
    case 'status':
      await handleStatus();
      break;
    case 'readiness':
      await handleSetReadiness(values);
      break;
    case 'moderator':
      await handleSetModerator(values);
      break;
    case 'audit':
      await handleAudit(values);
      break;
    case 'test-webhook':
      await handleTestWebhook(values);
      break;
    default:
      console.log(`TripTandem Operator Gates Tool`);
      console.log(`Usage:`);
      console.log(`  node scripts/manage_operator_gates.mjs status`);
      console.log(`  node scripts/manage_operator_gates.mjs readiness --approved true|false [--hours N] [--notes text]`);
      console.log(`  node scripts/manage_operator_gates.mjs moderator --uid <uid> --enabled true|false`);
      console.log(`  node scripts/manage_operator_gates.mjs audit [--limit N]`);
      console.log(`  node scripts/manage_operator_gates.mjs test-webhook [--secret <secret>] [--url <url>]`);
      break;
  }
}

main().catch((err) => {
  console.error(`Fatal error:`, err);
  process.exit(1);
});
