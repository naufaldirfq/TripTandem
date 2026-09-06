/**
 * Small, dependency-free helpers for interpreting the server-side Remote
 * Config gate. Firebase Remote Config stores explicit values as strings, but
 * accepting booleans as well keeps this boundary defensive and easy to test.
 *
 * The server intentionally reads the template's default value only. Client
 * conditional rollouts are not an authorization mechanism; a capability must
 * be globally enabled in the reviewed template before a callable can create
 * work for it.
 */
export interface RemoteConfigTemplateLike {
  parameters?: Record<string, {
    defaultValue?: unknown;
    conditionalValues?: unknown;
  }>;
}

export function isRemoteConfigBooleanEnabled(
  template: RemoteConfigTemplateLike | null | undefined,
  key: string,
): boolean {
  if (!template || !key || typeof template.parameters !== "object" || template.parameters === null) {
    return false;
  }
  const parameter = template.parameters[key];
  const defaultValue = parameter?.defaultValue;
  const value = typeof defaultValue === "object" && defaultValue !== null && "value" in defaultValue
    ? (defaultValue as { value?: unknown }).value
    : undefined;
  if (typeof value === "boolean") return value;
  return typeof value === "string" && value.trim().toLowerCase() === "true";
}
