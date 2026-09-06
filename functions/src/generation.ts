/**
 * Pure generation contracts and validation. This module deliberately has no
 * Firebase imports so it can be tested on a laptop and reused by the worker.
 * The server is the source of truth; clients only receive these bounded,
 * privacy-safe fields.
 */

export const GENERATION_SCHEMA_VERSION = "2026-09-04.v1";
export const GENERATED_ITEM_TYPES = ["activity", "transport", "meal", "free"] as const;
export type GeneratedItemType = (typeof GENERATED_ITEM_TYPES)[number];
export const GENERATION_SCOPES = ["whole_trip", "single_day", "fill_empty_days"] as const;
export type GenerationScope = (typeof GENERATION_SCOPES)[number];

/** Reads a bounded non-negative counter without coercing malformed values. */
export function boundedNonNegativeInteger(value: unknown, max: number): number | null {
  if (value === undefined || value === null) return 0;
  if (typeof value !== "number" || !Number.isSafeInteger(value) || value < 0 || value > max) return null;
  return value;
}

/** Ensures provider output uses the currently supported response contract. */
export function isVersionedGenerationResponse(value: unknown): value is { schemaVersion: string; items: unknown[] } {
  return isRecord(value)
    && value.schemaVersion === GENERATION_SCHEMA_VERSION
    && Array.isArray(value.items);
}

export type GeminiResponseFailure =
  | "ok"
  | "provider_rate_limited"
  | "provider_timeout"
  | "safety_blocked"
  | "provider_error";

const GEMINI_SAFETY_REASONS = new Set([
  "SAFETY",
  "BLOCKLIST",
  "PROHIBITED_CONTENT",
  "SPII",
  "IMAGE_SAFETY",
]);

/**
 * Maps provider responses to the small public failure vocabulary used by the
 * job state machine. Provider text is never surfaced or persisted verbatim.
 */
export function classifyGeminiResponse(status: number, body: unknown): GeminiResponseFailure {
  if (status === 429) return "provider_rate_limited";
  if (status === 408 || status === 504) return "provider_timeout";

  const record = isRecord(body) ? body : {};
  const error = isRecord(record.error) ? record.error : {};
  const candidates = Array.isArray(record.candidates) ? record.candidates : [];
  const candidate = isRecord(candidates[0]) ? candidates[0] : {};
  const promptFeedback = isRecord(record.promptFeedback) ? record.promptFeedback : {};
  const reasonText = [
    promptFeedback.blockReason,
    candidate.finishReason,
    error.status,
    error.code,
    error.message,
  ]
    .filter((value): value is string => typeof value === "string")
    .join(" ")
    .toUpperCase();
  if ([...GEMINI_SAFETY_REASONS].some((reason) => reasonText.includes(reason))) {
    return "safety_blocked";
  }

  return status >= 200 && status < 300 ? "ok" : "provider_error";
}

/**
 * The generation endpoint is intentionally narrow: it is for trip planning,
 * not requests that facilitate harm, evasion, exploitation, or illegal trade.
 * This is a coarse pre-filter only; provider safety controls remain enabled.
 */
const UNSAFE_INTENT_PATTERNS = [
  /\b(?:weapon|bomb|explosive|poison|traffick(?:ing)?|human\s*traffick(?:ing)?)\b/i,
  /\b(?:kill|murder|hurt|harm|attack|assault|kidnap|abduct)\b/i,
  /\b(?:evade|avoid)\s+(?:police|law\s*enforcement|immigration|border)\b/i,
  /\b(?:hack|exploit|bypass)\b/i,
];

export function hasUnsafeGenerationIntent(input: Pick<GenerationInput, "interests" | "accessibilityNotes" | "dietNotes" | "dailyStartLabel" | "dailyEndLabel">): boolean {
  const text = [
    ...input.interests,
    input.accessibilityNotes ?? "",
    input.dietNotes ?? "",
    input.dailyStartLabel ?? "",
    input.dailyEndLabel ?? "",
  ].join(" ");
  return UNSAFE_INTENT_PATTERNS.some((pattern) => pattern.test(text));
}

export interface GenerationInput {
  scope: GenerationScope;
  dayDate?: string;
  pace?: "relaxed" | "balanced" | "packed";
  budgetBand?: "budget" | "moderate" | "comfort" | "premium";
  interests: string[];
  dailyStartLabel?: string;
  dailyEndLabel?: string;
  accessibilityNotes?: string;
  dietNotes?: string;
  lockedItemIds: string[];
}

export interface GeneratedItem {
  id: string;
  type: GeneratedItemType;
  title: string;
  dayDate: string;
  startTimeLabel?: string;
  flexibleTime: boolean;
  durationMinutes: number;
  place?: string;
  note?: string;
  duplicateOfItemId?: string;
  warning?: string;
}

/**
 * The fields an organizer may change while a generated item is still a
 * preview. Type and dayDate stay tied to the validated provider suggestion so
 * an edit cannot move an item outside the requested scope.
 */
export interface GeneratedItemEdit {
  id?: unknown;
  title?: unknown;
  startTimeLabel?: unknown;
  flexibleTime?: unknown;
  durationMinutes?: unknown;
  place?: unknown;
  note?: unknown;
}

export interface GenerationPreview {
  schemaVersion: string;
  assumptions: string[];
  warnings: string[];
  items: GeneratedItem[];
  unverifiedInformationNotice: string;
}

export interface RawGeneratedItem {
  type?: unknown;
  title?: unknown;
  dayDate?: unknown;
  startTimeLabel?: unknown;
  flexibleTime?: unknown;
  durationMinutes?: unknown;
  place?: unknown;
  note?: unknown;
}

export interface ValidationContext {
  tripStartDate: string;
  tripEndDate: string;
  scope: GenerationScope;
  dayDate?: string;
  existingItems: Array<ExistingGenerationItem>;
  /** Coarse booked windows used to keep suggestions from colliding with locked items. */
  lockedItems?: Array<LockedGenerationWindow>;
  /** Dates with no scheduled item when the user chose fill-empty-days. */
  emptyDayDates?: string[];
}

/**
 * Only schedule metadata is allowed into the provider/validator. Titles,
 * places, notes, lodging details, and member data are deliberately excluded.
 */
export interface LockedGenerationWindow {
  dayDate: string;
  startTimeLabel?: string | null;
  flexibleTime?: boolean | null;
  durationMinutes?: number | null;
  type?: GeneratedItemType | string | null;
}

/** The minimum existing-item context needed to flag likely duplicate ideas. */
export interface ExistingGenerationItem {
  id: string;
  title: string;
  dayDate?: string | null;
  place?: string | null;
  startTimeLabel?: string | null;
  flexibleTime?: boolean | null;
}

/**
 * Re-checks the authorization context immediately before a worker sends any
 * planning data to the provider. A queued job can outlive a profile, trip, or
 * membership change, so creation-time authorization is not sufficient.
 */
export function hasGenerationAccess(
  requesterId: unknown,
  profile: { exists: boolean; accountStatus?: unknown; ageConfirmed?: unknown },
  trip: { exists: boolean; ownerId?: unknown; status?: unknown },
  member: { exists: boolean; status?: unknown; role?: unknown },
): boolean {
  const uid = typeof requesterId === "string" ? requesterId.trim() : "";
  if (!uid || !profile.exists || profile.accountStatus !== "active" || profile.ageConfirmed !== true) return false;
  if (!trip.exists || !["draft", "planning", "confirmed"].includes(String(trip.status ?? ""))) return false;
  return trip.ownerId === uid
    || (member.exists && member.status === "active" && ["owner", "editor"].includes(String(member.role ?? "")));
}

export interface ItineraryRevisionSnapshot {
  id: string;
  revision: number;
}

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;
const CLOCK_LABEL = /^(?:[01]\d|2[0-3]):[0-5]\d$/;
const FLEXIBLE_LABEL = /^(morning|late morning|afternoon|evening|night|anytime)$/i;

export function normalizeGenerationInput(data: unknown):
  | { ok: true; value: GenerationInput }
  | { ok: false; field: string } {
  if (!isRecord(data)) return { ok: false, field: "input" };
  const allowedKeys = new Set([
    "scope", "dayDate", "pace", "budgetBand", "interests", "dailyStartLabel",
    "dailyEndLabel", "accessibilityNotes", "dietNotes", "lockedItemIds",
  ]);
  if (Object.keys(data).some((key) => !allowedKeys.has(key))) return { ok: false, field: "input" };
  const scope = data.scope;
  if (!GENERATION_SCOPES.includes(scope as GenerationScope)) return { ok: false, field: "scope" };
  const dayDate = optionalString(data.dayDate, 10);
  if (data.dayDate !== undefined && data.dayDate !== null && data.dayDate !== "" && !dayDate) {
    return { ok: false, field: "dayDate" };
  }
  if (dayDate && !isValidIsoDate(dayDate)) return { ok: false, field: "dayDate" };
  if (scope === "single_day" && !dayDate) return { ok: false, field: "dayDate" };
  if (scope !== "single_day" && dayDate) return { ok: false, field: "dayDate" };
  const pace = optionalEnum(data.pace, ["relaxed", "balanced", "packed"] as const);
  const budgetBand = optionalEnum(data.budgetBand, ["budget", "moderate", "comfort", "premium"] as const);
  if (data.pace !== undefined && data.pace !== null && data.pace !== "" && !pace) return { ok: false, field: "pace" };
  if (data.budgetBand !== undefined && data.budgetBand !== null && data.budgetBand !== "" && !budgetBand) {
    return { ok: false, field: "budgetBand" };
  }
  const interests = stringArray(data.interests, 12, 60);
  if (!interests) return { ok: false, field: "interests" };
  const lockedItemIds = stringArray(data.lockedItemIds, 500, 80);
  if (!lockedItemIds) return { ok: false, field: "lockedItemIds" };
  const dailyStartLabel = optionalString(data.dailyStartLabel, 32);
  const dailyEndLabel = optionalString(data.dailyEndLabel, 32);
  const accessibilityNotes = optionalString(data.accessibilityNotes, 500);
  const dietNotes = optionalString(data.dietNotes, 500);
  if (data.dailyStartLabel !== undefined && dailyStartLabel === undefined) return { ok: false, field: "dailyStartLabel" };
  if (data.dailyEndLabel !== undefined && dailyEndLabel === undefined) return { ok: false, field: "dailyEndLabel" };
  if (data.accessibilityNotes !== undefined && accessibilityNotes === undefined) return { ok: false, field: "accessibilityNotes" };
  if (data.dietNotes !== undefined && dietNotes === undefined) return { ok: false, field: "dietNotes" };
  return {
    ok: true,
    value: {
      scope: scope as GenerationScope,
      ...(dayDate ? { dayDate } : {}),
      ...(pace ? { pace } : {}),
      ...(budgetBand ? { budgetBand } : {}),
      interests,
      ...(dailyStartLabel ? { dailyStartLabel } : {}),
      ...(dailyEndLabel ? { dailyEndLabel } : {}),
      ...(accessibilityNotes ? { accessibilityNotes } : {}),
      ...(dietNotes ? { dietNotes } : {}),
      lockedItemIds: [...new Set(lockedItemIds)],
    },
  };
}

export function validateGeneratedItems(
  raw: unknown,
  context: ValidationContext,
): { items: GeneratedItem[]; rejectedCount: number; warnings: string[] } {
  const values = Array.isArray(raw)
    ? raw
    : isRecord(raw) && Array.isArray(raw.items)
      ? raw.items
      : [];
  const warnings: string[] = [];
  const items: GeneratedItem[] = [];
  let rejectedCount = Math.max(0, values.length - 120);
  values.slice(0, 120).forEach((candidate, index) => {
    const parsed = parseGeneratedItem(candidate, index, context);
    if (!parsed) {
      rejectedCount += 1;
      return;
    }
    const lockedConflict = context.lockedItems?.find((locked) => lockedWindowConflict(parsed, locked));
    if (lockedConflict) {
      rejectedCount += 1;
      warnings.push(`A suggestion on ${parsed.dayDate} overlaps a booked item and was omitted.`);
      return;
    }
    const duplicate = context.existingItems.find((existing) => isLikelyDuplicate(parsed, existing, context.tripStartDate));
    const inPreview = items.find((existing) => isLikelyDuplicate(parsed, existing, context.tripStartDate));
    if (duplicate || inPreview) {
      parsed.warning = "Similar to an existing suggestion; review before applying.";
      parsed.duplicateOfItemId = duplicate?.id ?? inPreview?.id;
      warnings.push(`Duplicate suggestion detected for day ${parsed.dayDate}.`);
    }
    items.push(parsed);
  });
  if (values.length > 120) warnings.push("Some suggestions were omitted because the preview is limited to 120 items.");
  return { items, rejectedCount, warnings: [...new Set(warnings)] };
}

/** Applies and validates organizer edits without accepting provider metadata. */
export function applyGeneratedItemEdits(
  items: GeneratedItem[],
  rawEdits: unknown,
): { ok: true; items: GeneratedItem[] } | { ok: false; field: string } {
  if (rawEdits === undefined || rawEdits === null) return { ok: true, items };
  if (!Array.isArray(rawEdits) || rawEdits.length > 120) return { ok: false, field: "editedItems" };
  const byId = new Map(items.map((item) => [item.id, item]));
  const seen = new Set<string>();
  const updated = new Map<string, GeneratedItem>();
  const allowedKeys = new Set([
    "id", "title", "startTimeLabel", "flexibleTime", "durationMinutes", "place", "note",
  ]);
  for (const raw of rawEdits) {
    if (!isRecord(raw)) return { ok: false, field: "editedItems" };
    const id = typeof raw.id === "string" ? raw.id.trim() : "";
    if (!id || seen.has(id) || !byId.has(id)) return { ok: false, field: "editedItems" };
    if (Object.keys(raw).some((key) => !allowedKeys.has(key))) return { ok: false, field: "editedItems" };
    seen.add(id);
    const original = byId.get(id)!;

    const title = raw.title === undefined ? original.title : optionalEditedText(raw.title, 160, true);
    if (title === null) return { ok: false, field: "editedItems.title" };
    const flexibleTime = raw.flexibleTime === undefined ? original.flexibleTime : raw.flexibleTime;
    if (typeof flexibleTime !== "boolean") return { ok: false, field: "editedItems.flexibleTime" };
    const label = raw.startTimeLabel === undefined
      ? original.startTimeLabel
      : raw.startTimeLabel === null
        ? undefined
        : optionalEditedText(raw.startTimeLabel, 32, true);
    if (label === null) return { ok: false, field: "editedItems.startTimeLabel" };
    if (!flexibleTime && (!label || !CLOCK_LABEL.test(label))) return { ok: false, field: "editedItems.startTimeLabel" };
    if (flexibleTime && label && !FLEXIBLE_LABEL.test(label)) return { ok: false, field: "editedItems.startTimeLabel" };

    const durationMinutes = raw.durationMinutes === undefined ? original.durationMinutes : raw.durationMinutes;
    if (typeof durationMinutes !== "number" || !Number.isInteger(durationMinutes) || durationMinutes < 0 || durationMinutes > 1440) {
      return { ok: false, field: "editedItems.durationMinutes" };
    }
    const place = raw.place === undefined
      ? original.place
      : raw.place === null
        ? undefined
        : optionalEditedText(raw.place, 200, false);
    const note = raw.note === undefined
      ? original.note
      : raw.note === null
        ? undefined
        : optionalEditedText(raw.note, 1000, false);
    if (place === null) return { ok: false, field: "editedItems.place" };
    if (note === null) return { ok: false, field: "editedItems.note" };
    updated.set(id, {
      ...original,
      title,
      ...(label ? { startTimeLabel: label } : { startTimeLabel: undefined }),
      flexibleTime,
      durationMinutes,
      ...(place ? { place } : { place: undefined }),
      ...(note ? { note } : { note: undefined }),
    });
  }
  return { ok: true, items: items.map((item) => updated.get(item.id) ?? item) };
}

export function isDateInRange(date: string, start: string, end: string): boolean {
  return isValidIsoDate(date) && isValidIsoDate(start) && isValidIsoDate(end) && date >= start && date <= end;
}

/**
 * Returns true when the itinerary has changed since a generation preview was
 * created. Comparing the document set as well as each revision catches an
 * item that was added or removed without relying on a parent-trip revision.
 */
export function hasItineraryRevisionConflict(
  expected: Record<string, number>,
  current: ItineraryRevisionSnapshot[],
): boolean {
  const expectedIds = Object.keys(expected);
  if (expectedIds.length !== current.length) return true;
  const currentIds = new Set(current.map((item) => item.id));
  if (currentIds.size !== current.length) return true;
  if (expectedIds.some((id) => !currentIds.has(id))) return true;
  return current.some((item) => expected[item.id] !== item.revision);
}

function parseGeneratedItem(candidate: unknown, index: number, context: ValidationContext): GeneratedItem | null {
  if (!isRecord(candidate)) return null;
  const type = candidate.type;
  const title = typeof candidate.title === "string" ? candidate.title.trim() : "";
  const dayDate = typeof candidate.dayDate === "string" ? candidate.dayDate : "";
  if (typeof candidate.flexibleTime !== "boolean") return null;
  const flexibleTime = candidate.flexibleTime;
  const startTimeLabel = typeof candidate.startTimeLabel === "string" ? candidate.startTimeLabel.trim() : "";
  const durationMinutes = typeof candidate.durationMinutes === "number" && Number.isInteger(candidate.durationMinutes)
    ? candidate.durationMinutes
    : Number.NaN;
  const place = typeof candidate.place === "string" ? candidate.place.trim() : "";
  const note = typeof candidate.note === "string" ? candidate.note.trim() : "";
  if (!GENERATED_ITEM_TYPES.includes(type as GeneratedItemType)) return null;
  if (title.length < 1 || title.length > 160) return null;
  if (!isDateInRange(dayDate, context.tripStartDate, context.tripEndDate)) return null;
  if (context.scope === "single_day" && dayDate !== context.dayDate) return null;
  if (context.scope === "fill_empty_days" && !context.emptyDayDates?.includes(dayDate)) return null;
  if (!Number.isInteger(durationMinutes) || durationMinutes < 0 || durationMinutes > 1440) return null;
  if (place.length > 200 || note.length > 1000) return null;
  if (!flexibleTime && startTimeLabel && !CLOCK_LABEL.test(startTimeLabel)) return null;
  if (flexibleTime && startTimeLabel && !FLEXIBLE_LABEL.test(startTimeLabel)) return null;
  if (!flexibleTime && !startTimeLabel) return null;
  return {
    id: `suggestion-${index + 1}`,
    type: type as GeneratedItemType,
    title,
    dayDate,
    ...(startTimeLabel ? { startTimeLabel } : {}),
    flexibleTime,
    durationMinutes,
    ...(place ? { place } : {}),
    ...(note ? { note } : {}),
  };
}

function normalizeText(value: string): string {
  return value
    .toLocaleLowerCase()
    .replace(/[^\p{L}\p{N}]+/gu, " ")
    .replace(/\s+/g, " ")
    .trim();
}

export function isValidIsoDate(value: string): boolean {
  if (!ISO_DATE.test(value)) return false;
  const parsed = new Date(`${value}T00:00:00Z`);
  return !Number.isNaN(parsed.valueOf()) && parsed.toISOString().slice(0, 10) === value;
}

function isLikelyDuplicate(
  candidate: Pick<GeneratedItem, "title" | "dayDate" | "place" | "startTimeLabel" | "flexibleTime">,
  existing: ExistingGenerationItem,
  fallbackDayDate: string,
): boolean {
  if ((existing.dayDate ?? fallbackDayDate) !== candidate.dayDate) return false;
  const titleSimilar = similarText(candidate.title, existing.title);
  const placeSimilar = Boolean(candidate.place && existing.place && similarText(candidate.place, existing.place));
  const timeSimilar = similarTime(candidate.startTimeLabel, candidate.flexibleTime, existing.startTimeLabel, existing.flexibleTime);
  // An exact/similar title or place on the same day is enough to warn. A
  // nearby time is useful when titles are phrased differently, but only when
  // they still share at least one meaningful title token.
  return titleSimilar || placeSimilar || (timeSimilar && tokenOverlap(candidate.title, existing.title) >= 0.25);
}

/**
 * Reject only definite collisions. A flexible booked label cannot be mapped
 * to a clock interval, so it is left for the organizer to review rather than
 * silently discarding a potentially useful suggestion.
 */
function lockedWindowConflict(candidate: GeneratedItem, locked: LockedGenerationWindow): boolean {
  if (locked.dayDate !== candidate.dayDate || candidate.flexibleTime || locked.flexibleTime) return false;
  const candidateStart = clockMinutes(candidate.startTimeLabel);
  const lockedStart = clockMinutes(locked.startTimeLabel);
  const candidateDuration = boundedDuration(candidate.durationMinutes);
  const lockedDuration = boundedDuration(locked.durationMinutes);
  if (candidateStart === null || lockedStart === null || candidateDuration === null || lockedDuration === null) return false;
  return candidateStart < lockedStart + lockedDuration && lockedStart < candidateStart + candidateDuration;
}

function boundedDuration(value: number | null | undefined): number | null {
  return typeof value === "number" && Number.isInteger(value) && value > 0 && value <= 1440 ? value : null;
}

function similarText(left: string | null | undefined, right: string | null | undefined): boolean {
  const a = normalizeText(left ?? "");
  const b = normalizeText(right ?? "");
  if (!a || !b) return false;
  if (a === b || a.includes(b) || b.includes(a)) return true;
  return tokenOverlap(a, b) >= 0.6;
}

function tokenOverlap(left: string, right: string): number {
  const aTokens = new Set(normalizeText(left).split(" ").filter(Boolean));
  const bTokens = new Set(normalizeText(right).split(" ").filter(Boolean));
  const intersection = [...aTokens].filter((token) => bTokens.has(token)).length;
  const union = new Set([...aTokens, ...bTokens]).size;
  return union > 0 ? intersection / union : 0;
}

function similarTime(
  leftLabel: string | null | undefined,
  leftFlexible: boolean | null | undefined,
  rightLabel: string | null | undefined,
  rightFlexible: boolean | null | undefined,
): boolean {
  if (!leftLabel || !rightLabel) return false;
  if (Boolean(leftFlexible) || Boolean(rightFlexible)) return normalizeText(leftLabel) === normalizeText(rightLabel);
  const left = clockMinutes(leftLabel);
  const right = clockMinutes(rightLabel);
  return left !== null && right !== null && Math.abs(left - right) <= 90;
}

function clockMinutes(label: string | null | undefined): number | null {
  if (typeof label !== "string") return null;
  const match = /^(\d{2}):(\d{2})$/.exec(label);
  if (!match) return null;
  return Number(match[1]) * 60 + Number(match[2]);
}

function optionalEditedText(value: unknown, maxLength: number, required: boolean): string | null {
  if (typeof value !== "string") return null;
  const trimmed = value.trim();
  if ((required && trimmed.length < 1) || trimmed.length > maxLength) return null;
  return trimmed;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function optionalString(value: unknown, max: number): string | undefined {
  if (value === undefined || value === null || value === "") return undefined;
  return typeof value === "string" && value.trim().length <= max ? value.trim() : undefined;
}

function stringArray(value: unknown, maxItems: number, maxLength: number): string[] | undefined {
  if (value === undefined || value === null) return [];
  if (!Array.isArray(value) || value.length > maxItems) return undefined;
  if (!value.every((entry) => typeof entry === "string")) return undefined;
  const result = value.map((entry) => (entry as string).trim());
  return result.every((entry) => entry.length <= maxLength) ? result.filter(Boolean) : undefined;
}

function optionalEnum<T extends string>(value: unknown, allowed: readonly T[]): T | undefined {
  if (value === undefined || value === null || value === "") return undefined;
  return allowed.includes(value as T) ? (value as T) : undefined;
}
