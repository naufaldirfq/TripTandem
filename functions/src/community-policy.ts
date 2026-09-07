import { createHash } from "node:crypto";
import { isValidIsoDate } from "./generation";

export type RecordData = Record<string, any>;
export const REPORT_CATEGORIES = ["harassment", "scam", "exploitation", "impersonation", "dangerous_activity", "privacy", "spam", "underage", "other"];
export const REPORT_STATES = ["received", "triaged", "investigating", "actioned", "no_action", "appealed", "closed"];
export const DESTINATIONS: Record<string, string> = {
  "id-bali": "Bali, Indonesia", "id-yogyakarta": "Yogyakarta, Indonesia", "id-jakarta": "Jakarta, Indonesia",
  "jp-tokyo": "Tokyo, Japan", "jp-kyoto": "Kyoto, Japan", "th-bangkok": "Bangkok, Thailand",
  "sg-singapore": "Singapore", "my-kuala-lumpur": "Kuala Lumpur, Malaysia", "vn-hanoi": "Hanoi, Vietnam",
  "au-sydney": "Sydney, Australia", "fr-paris": "Paris, France", "gb-london": "London, United Kingdom",
};
export function safeId(value: unknown): value is string {
  return typeof value === "string" && /^[A-Za-z0-9_-]{1,128}$/.test(value);
}
/** Conservative screening, never an automated irreversible account decision. */
export function safePublicText(value: unknown, min = 1, max = 300): value is string {
  if (typeof value !== "string" || value.trim().length < min || value.length > max) return false;
  const text = value.normalize("NFKC");
  return !/[\u0000-\u001f\u007f\u200b-\u200f\u202a-\u202e\u2060-\u206f]/u.test(text)
    && !/(https?:|www\.|\b[a-z0-9-]+\.(com|net|org|io|me)\b|@|\+?\d[\d\s().-]{6,}\d|\b(whatsapp|telegram|wechat|paypal|venmo|cashapp|deposit|wire transfer|pay me|send money|kill you|rape|traffick|escort|sexual|nudes)\b|\b\d+\s+\w+\s+(street|road|avenue|lane|st|rd)\b|\b(hotel|hostel|apartment|room|villa|meet at|meeting point|coordinates)\b)/iu.test(text);
}
export function profileProjection(profile: RecordData) {
  // Initials only: no avatar uploads, biography, home region, or contact surface.
  const initials = String(profile.displayName ?? "Traveler").split(/\s+/u).slice(0, 2).map(v => v[0] ?? "").join("").replace(/[^\p{L}]/gu, "").slice(0, 4) || "T";
  const languages = [profile.primaryLanguage, ...(profile.additionalLanguages ?? [])].filter(v => safePublicText(v, 1, 40)).slice(0, 12);
  return { initials, pace: profile.pace ?? null, budgetBand: profile.budgetBand ?? null,
    interests: (profile.interests ?? []).filter((v: unknown) => safePublicText(v, 1, 40)).slice(0, 12), languages };
}
export function publicProjection(tripId: string, trip: RecordData, profile: RecordData, input: RecordData) {
  if (!DESTINATIONS[input.destinationId] || !safePublicText(input.title, 3, 100)
    || !safePublicText(input.expectationNote, 20, 300) || !safePublicText(input.summary, 3, 200)) throw new Error("public_content_needs_review");
  if (!isValidIsoDate(trip.startDate) || !isValidIsoDate(trip.endDate) || trip.startDate > trip.endDate
    || !["relaxed", "balanced", "packed", "slow", "fast"].includes(trip.pace)
    || !["budget", "moderate", "comfort", "premium", "mid", "flexible"].includes(trip.budgetBand)) throw new Error("trip_essentials_required");
  const host = profileProjection(profile);
  if (!host.languages.length) throw new Error("language_required");
  return { tripId, title: input.title.trim(), destinationId: input.destinationId, destination: DESTINATIONS[input.destinationId],
    startDate: trip.startDate, endDate: trip.endDate, datesFlexible: trip.datesFlexible === true,
    pace: trip.pace, budgetBand: trip.budgetBand, capacity: trip.capacity,
    remainingSpots: trip.capacity - trip.activeMemberCount, host, languages: host.languages,
    interests: (trip.interests ?? []).filter((v: unknown) => safePublicText(v, 1, 40)).slice(0, 12),
    expectationNote: input.expectationNote.trim(), summary: input.summary.trim() };
}
export function projectionHash(projection: RecordData): string { return createHash("sha256").update(JSON.stringify(projection)).digest("hex"); }
export function eligibleTrip(trip: RecordData | undefined, owner: RecordData | undefined, today: string): boolean {
  return !!trip && !!owner && owner.accountStatus === "active" && owner.ageConfirmed === true
    && typeof owner.displayName === "string" && owner.displayName.trim().length >= 2
    && ["planning", "confirmed"].includes(trip.status) && isValidIsoDate(trip.startDate) && trip.startDate > today
    && Number.isInteger(trip.capacity) && Number.isInteger(trip.activeMemberCount)
    && trip.activeMemberCount >= 1 && trip.capacity > trip.activeMemberCount;
}
export function compatibility(listing: RecordData, profile: RecordData, filters: RecordData = {}) {
  const reasons: string[] = []; let score = 0; let known = 0; const missing: string[] = [];
  if (isValidIsoDate(filters.startDate) && isValidIsoDate(filters.endDate)) {
    known += 35;
    if (filters.startDate <= listing.endDate && filters.endDate >= listing.startDate) {
      const exact = filters.startDate === listing.startDate && filters.endDate === listing.endDate;
      score += exact ? 35 : 25; reasons.push(exact ? "Exact date overlap" : "Partial date overlap");
    } else reasons.push("Dates do not overlap");
  } else missing.push("dates");
  if (profile.pace) { known += 20; if (profile.pace === listing.pace) { score += 20; reasons.push("Same travel pace"); } else reasons.push("Different travel pace"); } else missing.push("pace");
  const bands = ["budget", "moderate", "comfort", "premium"];
  const band = (v: string) => v === "mid" ? 1 : bands.indexOf(v);
  if (band(profile.budgetBand) >= 0 && band(listing.budgetBand) >= 0) {
    known += 20; const distance = Math.abs(band(profile.budgetBand) - band(listing.budgetBand));
    score += Math.max(0, 20 - distance * 10); reasons.push(distance === 0 ? "Same budget band" : distance === 1 ? "Nearby budget bands" : "Different budget bands");
  } else missing.push("budget");
  if (profile.interests?.length) { known += 15; const shared = listing.interests.filter((v: string) => profile.interests.includes(v)); score += Math.min(15, shared.length * 5); if (shared.length) reasons.push("Shared travel interests"); } else missing.push("interests");
  const languages = [profile.primaryLanguage, ...(profile.additionalLanguages ?? [])].filter(Boolean);
  if (languages.length) { known += 10; if (listing.languages.some((v: string) => languages.includes(v))) { score += 10; reasons.push("A shared language"); } else reasons.push("No shared language listed"); } else missing.push("language");
  return { label: missing.length ? "Not enough information" : score / Math.max(known, 1) >= .75 ? "Strong fit" : score / Math.max(known, 1) >= .4 ? "Mixed fit" : "Limited fit", reasons: reasons.length ? reasons : ["Review the trip expectations"], missingFields: missing, score };
}
export function matchesFilters(listing: RecordData, filters: RecordData): boolean {
  return listing.destinationId === filters.destinationId
    && (!filters.startDate || listing.endDate >= filters.startDate)
    && (!filters.endDate || listing.startDate <= filters.endDate)
    && (!filters.pace || listing.pace === filters.pace)
    && (!filters.budgetBand || listing.budgetBand === filters.budgetBand)
    && (!filters.language || listing.languages.includes(filters.language))
    && (!filters.interest || listing.interests.includes(filters.interest))
    && listing.remainingSpots >= (Number(filters.spots) || 1);
}
export function requestState(request: RecordData, trip: RecordData | undefined, owner: RecordData | undefined, blocked: boolean, now: number): string {
  if (request.status !== "pending") return request.status;
  if (request.expiresAtMs <= now || (trip?.startDate && trip.startDate <= new Date(now).toISOString().slice(0, 10))) return "expired";
  if (blocked || !trip || !owner || owner.accountStatus !== "active" || trip.visibility !== "open" || !["planning", "confirmed"].includes(trip.status) || trip.capacity <= trip.activeMemberCount) return "invalidated";
  return "pending";
}

export function pushCategory(type: string): string | undefined {
  if (type.startsWith("request_") || type === "join_request_received") return "requests";
  if (["invitation_accepted", "role_changed", "membership_removed", "ownership_transfer_request"].includes(type)) return "membership";
  if (["trip_cancelled", "trip_changed"].includes(type)) return "trip_changes";
  return undefined;
}
export function pushAllowed(preferences: RecordData, type: string, now: Date): boolean {
  const category = pushCategory(type);
  if (!category || preferences.push !== true || preferences[category] !== true) return false;
  try {
    const hour = Number(new Intl.DateTimeFormat("en-GB", { hour: "2-digit", hourCycle: "h23", timeZone: preferences.timezone || "UTC" }).format(now));
    const start = preferences.quietStart ?? 22, end = preferences.quietEnd ?? 8;
    return !(start < end ? hour >= start && hour < end : start > end && (hour >= start || hour < end));
  } catch { return false; }
}
