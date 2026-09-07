/** Operator CLI. Never pass bearer credentials in command arguments or commit them. */
import { readFile } from 'node:fs/promises';
const [inputFile] = process.argv.slice(2);
if (!inputFile || !process.env.MODERATOR_ID_TOKEN_FILE || !process.env.MODERATOR_APP_CHECK_TOKEN_FILE) {
  console.error('Usage: set MODERATOR_ID_TOKEN_FILE and MODERATOR_APP_CHECK_TOKEN_FILE to private credential files, then run node scripts/moderate_community.mjs action.json');
  process.exit(2);
}
const action = JSON.parse(await readFile(inputFile, 'utf8'));
const operations = new Set(['queue', 'publish_reviewed', 'unpublish', 'suspend', 'report_status']);
if (!operations.has(action.operation)) throw new Error('Unknown moderation operation');
const token = (await readFile(process.env.MODERATOR_ID_TOKEN_FILE, 'utf8')).trim();
const appCheck = (await readFile(process.env.MODERATOR_APP_CHECK_TOKEN_FILE, 'utf8')).trim();
const response = await fetch('https://asia-southeast2-triptandem.cloudfunctions.net/moderateCommunity', {
  method: 'POST', headers: { 'content-type': 'application/json', authorization: `Bearer ${token}`, 'X-Firebase-AppCheck': appCheck }, body: JSON.stringify({ data: action }),
});
const result = await response.json();
if (!response.ok) { console.error('Moderation action unavailable. Check authorization, current state, and policy basis.'); process.exit(1); }
console.log(JSON.stringify(result.result, null, 2));
