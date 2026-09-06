import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");

function read(relativePath) {
  return fs.readFileSync(path.join(repositoryRoot, relativePath), "utf8");
}

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

function assertSameKeys(label, expected, actual) {
  const expectedKeys = [...expected].sort();
  const actualKeys = [...actual].sort();
  assert(
    JSON.stringify(expectedKeys) === JSON.stringify(actualKeys),
    `${label} mismatch\n  expected: ${expectedKeys.join(", ")}\n  actual: ${actualKeys.join(", ")}`,
  );
}

const expectedFlags = {
  AI_GENERATION_ENABLED: "ai_generation_enabled",
  OPEN_TRIP_PUBLISHING_ENABLED: "open_trip_publishing_enabled",
  DISCOVERY_ENABLED: "discovery_enabled",
  JOIN_REQUESTS_ENABLED: "join_requests_enabled",
  PUSH_ENABLED: "push_enabled",
};
const expectedKeys = Object.values(expectedFlags);

const template = JSON.parse(read("remote_config.json"));
const parameters = template.parameters ?? {};
assertSameKeys("Remote Config template parameters", expectedKeys, Object.keys(parameters));
for (const key of expectedKeys) {
  const parameter = parameters[key];
  assert(parameter.valueType === "BOOLEAN", `${key} must remain a BOOLEAN parameter`);
  assert(parameter.defaultValue?.value === "false", `${key} must default to false`);
}

const defaultsXml = read("mobile/app/src/main/res/xml/remote_config_defaults.xml");
for (const key of expectedKeys) {
  assert(
    new RegExp(`<key>${key.replaceAll("_", "\\_")}</key>\\s*<value>false</value>`).test(defaultsXml),
    `Android default missing or not false: ${key}`,
  );
}

const sharedFlags = read("mobile/shared/src/commonMain/kotlin/com/example/triptandem/shared/TripTandemFeatureFlags.kt");
const androidRuntime = read("mobile/app/src/main/java/com/example/triptandem/RemoteConfigRuntime.kt");
const iosHost = read("mobile/iosApp/TripTandem/TripTandemApp.swift");
for (const [constant, key] of Object.entries(expectedFlags)) {
  assert(sharedFlags.includes(`const val ${constant} = "${key}"`), `Shared flag constant missing: ${key}`);
  assert(androidRuntime.includes(`getString(TripTandemFeatureFlags.${constant})`), `Android runtime does not read strict flag value: ${key}`);
  assert(iosHost.includes(`"${key}": NSNumber(value: false)`), `iOS default missing: ${key}`);
  assert(iosHost.includes(`key: "${key}"`), `iOS runtime does not use strict flag parsing: ${key}`);
}

console.log(`Remote Config parity OK: ${expectedKeys.length} independent Boolean flags default safely to false.`);
