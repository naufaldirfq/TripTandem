/**
 * Server-side trip creation validation and entitlement policy.
 *
 * Active-trip limits are deliberately evaluated in the callable transaction
 * rather than in the client.  Keeping this parser pure also makes the policy
 * easy to exercise without needing a live Firebase project.
 */

export const ACTIVE_TRIP_STATUSES = ["draft", "planning", "confirmed"] as const;
export const TRIP_STATUSES = [
  ...ACTIVE_TRIP_STATUSES,
  "completed",
  "cancelled",
  "archived",
] as const;
export const TRIP_VISIBILITIES = ["private", "unlisted", "open"] as const;
export const TRIP_PACES = ["relaxed", "balanced", "packed", "slow", "fast"] as const;
export const TRIP_BUDGET_BANDS = ["budget", "moderate", "comfort", "premium", "mid", "flexible"] as const;
export const FREE_TRIP_CAPACITY = 6;

export type TripCreateInput = {
  title: string;
  destination: string;
  startDate: string;
  endDate: string;
  destinationTimezone: string;
  datesFlexible: boolean;
  visibility: (typeof TRIP_VISIBILITIES)[number];
  capacity: number;
  status: (typeof TRIP_STATUSES)[number];
  currency?: string;
  budgetBand?: (typeof TRIP_BUDGET_BANDS)[number];
  pace?: (typeof TRIP_PACES)[number];
  expectationNote?: string;
  interests: string[];
  coverColor?: string;
};

export type NormalizedTripInput =
  | { ok: true; value: TripCreateInput }
  | { ok: false; field: string };

const MAX_KEYS = new Set([
  "title", "destination", "startDate", "endDate", "destinationTimezone",
  "datesFlexible", "visibility", "capacity", "status", "currency",
  "budgetBand", "pace", "expectationNote", "interests", "coverColor",
]);

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function requiredString(value: unknown, field: string, max: number): string | null {
  if (typeof value !== "string") return null;
  const trimmed = value.trim();
  return trimmed.length > 0 && trimmed.length <= max ? trimmed : null;
}

function optionalString(value: unknown, max: number): string | undefined | null {
  if (value === undefined || value === null) return undefined;
  if (typeof value !== "string") return null;
  const trimmed = value.trim();
  return trimmed.length > 0 && trimmed.length <= max ? trimmed : null;
}

function stringArray(value: unknown, maxItems: number, maxLength: number): string[] | null {
  if (!Array.isArray(value) || value.length > maxItems) return null;
  const values = value.map((item) => typeof item === "string" ? item.trim() : "");
  if (values.some((item) => item.length === 0 || item.length > maxLength)) return null;
  return [...new Set(values)];
}

function validIsoDate(value: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const parsed = new Date(`${value}T00:00:00Z`);
  return !Number.isNaN(parsed.valueOf()) && parsed.toISOString().slice(0, 10) === value;
}

function tripLengthInDays(start: string, end: string): number {
  if (!validIsoDate(start) || !validIsoDate(end)) return 0;
  const first = Date.parse(`${start}T00:00:00Z`);
  const last = Date.parse(`${end}T00:00:00Z`);
  return Math.floor((last - first) / 86_400_000) + 1;
}

/** Normalizes the client payload and rejects unknown or malformed fields. */
export function normalizeTripCreateInput(value: unknown): NormalizedTripInput {
  if (!isRecord(value)) return { ok: false, field: "input" };
  if (Object.keys(value).some((key) => !MAX_KEYS.has(key))) return { ok: false, field: "input" };

  const title = requiredString(value.title, "title", 120);
  const destination = requiredString(value.destination, "destination", 160);
  const startDate = requiredString(value.startDate, "startDate", 10);
  const endDate = requiredString(value.endDate, "endDate", 10);
  const destinationTimezone = requiredString(value.destinationTimezone, "destinationTimezone", 80);
  const interests = stringArray(value.interests ?? [], 12, 80);
  if (!title || !destination || !startDate || !endDate || !destinationTimezone || !interests) {
    return { ok: false, field: "required" };
  }
  if (!validIsoDate(startDate) || !validIsoDate(endDate) || startDate > endDate || tripLengthInDays(startDate, endDate) > 60) {
    return { ok: false, field: "dates" };
  }
  if (typeof value.datesFlexible !== "boolean") return { ok: false, field: "datesFlexible" };
  if (typeof value.capacity !== "number" || !Number.isInteger(value.capacity) || value.capacity < 2 || value.capacity > 12) {
    return { ok: false, field: "capacity" };
  }
  if (typeof value.visibility !== "string" || !TRIP_VISIBILITIES.includes(value.visibility as (typeof TRIP_VISIBILITIES)[number])) {
    return { ok: false, field: "visibility" };
  }
  if (typeof value.status !== "string" || !TRIP_STATUSES.includes(value.status as (typeof TRIP_STATUSES)[number])) {
    return { ok: false, field: "status" };
  }
  if (value.visibility === "open") return { ok: false, field: "visibility" };

  const currency = optionalString(value.currency, 3);
  const expectationNote = optionalString(value.expectationNote, 500);
  const coverColor = optionalString(value.coverColor, 16);
  if (currency === null || expectationNote === null || coverColor === null) return { ok: false, field: "optional" };
  if (currency !== undefined && !/^[A-Z]{3}$/.test(currency)) return { ok: false, field: "currency" };
  if (coverColor !== undefined && !/^#[0-9A-Fa-f]{6}$/.test(coverColor)) return { ok: false, field: "coverColor" };

  const budgetBand = value.budgetBand === undefined || value.budgetBand === null
    ? undefined
    : (typeof value.budgetBand === "string" && TRIP_BUDGET_BANDS.includes(value.budgetBand as (typeof TRIP_BUDGET_BANDS)[number])
      ? value.budgetBand as (typeof TRIP_BUDGET_BANDS)[number]
      : null);
  const pace = value.pace === undefined || value.pace === null
    ? undefined
    : (typeof value.pace === "string" && TRIP_PACES.includes(value.pace as (typeof TRIP_PACES)[number])
      ? value.pace as (typeof TRIP_PACES)[number]
      : null);
  if (budgetBand === null) return { ok: false, field: "budgetBand" };
  if (pace === null) return { ok: false, field: "pace" };

  return {
    ok: true,
    value: {
      title,
      destination,
      startDate,
      endDate,
      destinationTimezone,
      datesFlexible: value.datesFlexible,
      visibility: value.visibility as (typeof TRIP_VISIBILITIES)[number],
      capacity: value.capacity,
      status: value.status as (typeof TRIP_STATUSES)[number],
      ...(currency === undefined ? {} : { currency }),
      ...(budgetBand === undefined ? {} : { budgetBand }),
      ...(pace === undefined ? {} : { pace }),
      ...(expectationNote === undefined ? {} : { expectationNote }),
      interests,
      ...(coverColor === undefined ? {} : { coverColor }),
    },
  };
}

/** Free organizers may own one active trip; Pro is intentionally fair-use. */
export function canCreateActiveTrip(activeTripCount: number, isPro: boolean): boolean {
  return isPro || activeTripCount < 1;
}

/** A free organizer may plan trips for up to six travelers; Pro is fair-use. */
export function canUseTripCapacity(capacity: number, isPro: boolean): boolean {
  return isPro || capacity <= FREE_TRIP_CAPACITY;
}

/**
 * Existing Pro-sized trips remain editable after entitlement expiry. A free
 * organizer may keep the current capacity, but may not expand it; reactivating
 * an inactive Pro-sized trip is a new gated action and must be rejected.
 */
export function canUpdateTripCapacity(
  currentCapacity: number,
  requestedCapacity: number,
  isPro: boolean,
  reactivating: boolean,
): boolean {
  return isPro
    || requestedCapacity <= FREE_TRIP_CAPACITY
    || (requestedCapacity === currentCapacity && !reactivating);
}
