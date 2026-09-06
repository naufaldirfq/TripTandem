# PRD 11 — Offline Access and Export

Phase: 4  
Priority: P1 read cache; P2 queued edits and richer export  
Owner: Client platform  
Status: Scoped for launch

## Outcome

Keep essential trip plans available during unreliable travel connectivity and let organizers share a privacy-safe itinerary summary outside the app.

## User stories

- As a traveler without connectivity, I can read the latest synchronized itinerary.
- As a traveler, I can see when cached information was last updated.
- As an organizer, I can export a concise itinerary summary.
- As a privacy-conscious user, an export never includes sensitive member or location information without explicit selection.

## Scope

### P1

- Encrypted local cache for joined trips and itinerary
- Last-synced/offline indicator
- Manual refresh
- Native share sheet with plain-text or image/PDF summary only if layout quality is verified
- Export preview and field selection
- Protected-cache clearing on sign out/removal

### P2

- Queued offline itinerary edits with conflict resolution
- Selective offline destination maps
- Calendar export

### Non-goals

- Full offline-first open-trip discovery
- Silent background download of large media/maps
- Export of reports, applicants, private profiles, or invite tokens

## Functional requirements

1. Cache stores only trips the current installation is authorized to access.
2. Protected content uses platform-appropriate encrypted storage/database keys.
3. Each cached trip shows a server revision and last successful sync timestamp.
4. P1 offline mode is read-only and clearly labeled; editing controls explain that connection is required.
5. On reconnect, fetch authorization and latest revision before displaying protected updates.
6. Sign out, account deletion, membership removal, block consequences, or device unlink clears protected cache.
7. Export defaults to destination, broad dates, and public/member-approved itinerary fields.
8. Exact lodging/meeting details, private notes, member names, and contact data are off by default and require explicit review if export is allowed at all.
9. Export preview is generated locally where practical and has a visible generation date/timezone.
10. Share sheet content contains no secret URL, invite token, analytics identifier, or internal record ID.
11. Screenshots/previews are protected from accidental sensitive display according to platform capabilities and threat model.
12. Cache size and media policy are bounded; provide clear-cache control.

## Edge cases

- User opens stale cache after being removed: app activation attempts authorization first; if offline, show protected lock state rather than indefinite access beyond the documented offline authorization window.
- Device clock wrong: freshness is computed from signed/server sync time when available.
- Export while data changes: export uses a captured revision and states its generated time.
- Unsupported characters/fonts: export must preserve Unicode text or fall back to plain text.
- Storage full: retain essentials or fail clearly without partial/corrupt state.

## Analytics

- `offline_cache_read(freshness_bucket)`
- `offline_refresh_completed(result)`
- `export_started(format)`
- `export_completed(format, included_field_count)`
- `protected_cache_cleared(reason)`

Never log exported content, file path, exact trip metadata, or share destination.

## Acceptance criteria

- A previously synchronized itinerary is readable after network loss and process restart.
- Last-sync time and read-only state are visible and accessible.
- Removal/sign-out clears protected content under the defined security policy.
- Export preview exactly matches shared output.
- Default export contains no member identity, contact, invite token, exact lodging/meeting point, or private note.
- Unicode, dark mode, large text, and multi-day layout tests pass.
- Cache corruption and storage-full failures do not crash or expose another account’s data.

## Dependencies and open decisions

- Define acceptable offline authorization duration during security review.
- Use plain text first if PDF/image output cannot meet accessibility and layout quality by release.
- Offline queued writes remain disabled until conflict UX passes PRD 03 requirements.
