import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");

function read(relativePath) {
  return fs.readFileSync(path.join(repositoryRoot, relativePath), "utf8");
}

function quotedValues(value) {
  return [...value.matchAll(/"([^"\\]*(?:\\.[^"\\]*)*)"/g)].map((match) => match[1]);
}

function extractBlock(source, startMarker, endMarker) {
  const start = source.indexOf(startMarker);
  if (start < 0) throw new Error(`Missing marker: ${startMarker}`);
  const contentStart = start + startMarker.length;
  const end = source.indexOf(endMarker, contentStart);
  if (end < 0) throw new Error(`Missing marker: ${endMarker}`);
  return source.slice(contentStart, end);
}

function assertEqual(label, expected, actual) {
  const expectedSorted = [...new Set(expected)].sort();
  const actualSorted = [...new Set(actual)].sort();
  if (JSON.stringify(expectedSorted) === JSON.stringify(actualSorted)) return;
  const missing = expectedSorted.filter((value) => !actualSorted.includes(value));
  const unexpected = actualSorted.filter((value) => !expectedSorted.includes(value));
  throw new Error(
    `${label} mismatch\n` +
      `  missing: ${missing.length ? missing.join(", ") : "none"}\n` +
      `  unexpected: ${unexpected.length ? unexpected.join(", ") : "none"}`,
  );
}

function assertMapEqual(label, expected, actual) {
  const expectedKeys = Object.keys(expected).sort();
  const actualKeys = Object.keys(actual).sort();
  assertEqual(`${label} event keys`, expectedKeys, actualKeys);
  for (const key of expectedKeys) {
    assertEqual(`${label} parameters for ${key}`, expected[key], actual[key] ?? []);
  }
}

const taxonomy = read("mobile/shared/src/commonMain/kotlin/com/example/triptandem/shared/TripTandemAnalytics.kt");
const android = read("mobile/app/src/main/java/com/example/triptandem/FirebaseAnalyticsTracker.kt");
const ios = read("mobile/iosApp/TripTandem/TripTandemApp.swift");

const constants = Object.fromEntries(
  [...taxonomy.matchAll(/const val ([A-Z][A-Z0-9_]*) = "([a-z][a-z0-9_]*)"/g)].map((match) => [match[1], match[2]]),
);
const taxonomyEvents = Object.values(constants);
if (!taxonomyEvents.length) throw new Error("No analytics events found in shared taxonomy");

const androidEventsBlock = extractBlock(
  android,
  "val allowedEvents = setOf(",
  "        )\n\n        val allowedParameters",
);
const androidEvents = [...androidEventsBlock.matchAll(/TripTandemAnalytics\.Events\.([A-Z][A-Z0-9_]*)/g)]
  .map((match) => constants[match[1]])
  .filter(Boolean);

const iosEventsBlock = extractBlock(
  ios,
  "private static let allowedEvents: Set<String> = [",
  "    ]\n\n    private static let allowedScreenNames",
);
const iosEvents = quotedValues(iosEventsBlock);

assertEqual("Android analytics event", taxonomyEvents, androidEvents);
assertEqual("iOS analytics event", taxonomyEvents, iosEvents);

const androidParametersBlock = extractBlock(
  android,
  "val allowedParameters = mapOf(",
  "        )\n\n        val allowedScreenNames",
);
const androidParameters = {};
for (const match of androidParametersBlock.matchAll(
  /TripTandemAnalytics\.Events\.([A-Z][A-Z0-9_]*)\s+to\s+setOf\(([^)]*)\)/g,
)) {
  const event = constants[match[1]];
  if (!event) throw new Error(`Android parameter map references unknown event constant ${match[1]}`);
  androidParameters[event] = quotedValues(match[2]);
}

const iosParametersBlock = extractBlock(
  ios,
  "private static let allowedParameters: [String: Set<String>] = [",
  "    ]\n}",
);
const iosParameters = {};
for (const match of iosParametersBlock.matchAll(/^\s*"([a-z][a-z0-9_]*)": \[([^\]]*)\],?$/gm)) {
  iosParameters[match[1]] = quotedValues(match[2]);
}

assertMapEqual("Platform analytics parameter allowlist", androidParameters, iosParameters);

console.log(
  `Analytics parity OK: ${taxonomyEvents.length} events, ` +
    `${Object.keys(androidParameters).length} parameter schemas, Android/iOS aligned.`,
);
