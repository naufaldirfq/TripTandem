import test from "node:test";
import assert from "node:assert/strict";
import { isRemoteConfigBooleanEnabled } from "./remoteconfig";

test("Remote Config gate requires an explicit true default", () => {
  assert.equal(isRemoteConfigBooleanEnabled({ parameters: { ai_generation_enabled: { defaultValue: { value: "true" } } } }, "ai_generation_enabled"), true);
  assert.equal(isRemoteConfigBooleanEnabled({ parameters: { ai_generation_enabled: { defaultValue: { value: " TRUE " } } } }, "ai_generation_enabled"), true);
  assert.equal(isRemoteConfigBooleanEnabled({ parameters: { ai_generation_enabled: { defaultValue: { value: "false" } } } }, "ai_generation_enabled"), false);
  assert.equal(isRemoteConfigBooleanEnabled({ parameters: { ai_generation_enabled: { conditionalValues: {} } } }, "ai_generation_enabled"), false);
});

test("missing, malformed, and unrelated templates fail closed", () => {
  assert.equal(isRemoteConfigBooleanEnabled(undefined, "ai_generation_enabled"), false);
  assert.equal(isRemoteConfigBooleanEnabled({ parameters: {} }, "ai_generation_enabled"), false);
  assert.equal(isRemoteConfigBooleanEnabled({ parameters: { ai_generation_enabled: { defaultValue: { value: "yes" } } } }, "ai_generation_enabled"), false);
  assert.equal(isRemoteConfigBooleanEnabled({ parameters: { other: { defaultValue: { value: "true" } } } }, "ai_generation_enabled"), false);
});
