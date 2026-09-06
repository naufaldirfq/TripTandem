# TripTandem Design Guidelines

Status: Version 1.1 — aligned with Superdesign mockup  
Applies to: Android, iOS, store assets, and launch marketing

## 1. Brand idea

TripTandem should feel like a warm, capable travel companion: optimistic enough to inspire a trip, structured enough to trust with plans, and human enough to make meeting companions feel welcome.

Brand attributes:

- Trustworthy, not institutional
- Social, not performative
- Adventurous, not chaotic
- Helpful, not controlling
- Inclusive, not childish

The Superdesign mockup establishes a warm editorial travel-journal aesthetic: cream backgrounds, tactile white cards, confident coral actions, quiet sage planning cues, and friendly human imagery. The product story is two paths becoming one shared journey.

Avoid generic travel decoration such as airplane trails, passport stamps, flags, or an overloaded map aesthetic. A restrained route line, pin, destination photo, or avatar stack is enough to suggest travel and community.

## 2. Logo and app icon

The current concept icon is [assets/brand/triptandem-app-icon-1024.png](assets/brand/triptandem-app-icon-1024.png).

Concept:

- Two curved route forms represent two travelers moving together.
- Their combined silhouette creates a location marker.
- A coral waypoint represents a shared destination or decision point.
- Cream and coral distinguish two individuals without encoding gender or identity.

Usage rules:

- Use the full-bleed 1024×1024 source for store artwork.
- Do not add text, outlines, drop shadows, or a second container.
- Do not bake rounded corners into exported app icons; operating systems apply masks.
- Keep the symbol recognizable at 48×48 px before approving it.
- Generate Android adaptive foreground/background assets with Android Studio Image Asset Studio.
- Use the 1024×1024 source in the iOS asset catalog and let Xcode generate required sizes.
- Before final store submission, create a vector master by manually tracing and optically correcting the selected mark; AI raster output is concept artwork, not an editable brand source.
- Recolor the current teal concept before release: use Ink 900 as the deep background, Coral 500 as the social route, Cream 50 as the companion route, and Sage 500 for the waypoint. It must visually match this warm system.

## 3. Color system — Warm Wayfinding

The palette follows the Superdesign mockup. Cream creates a calm travel-journal canvas; coral makes a clear, social invitation; sage carries planning, progress, and grounded reassurance; ink gives the product warmth and editorial contrast.

### Brand primitives

| Token | Hex | Intended use |
|---|---:|---|
| Coral 50 | `#FDF4F0` | Soft social tint and selected-card background |
| Coral 100 | `#FCE8E0` | Chips, decorative panels, pressed light state |
| Coral 400 | `#F08A68` | Hero highlight and non-text accent |
| Coral 500 | `#E8704A` | Brand action and social emphasis in the mockup |
| Coral 600 | `#D45A35` | Hover/pressed primary action |
| Coral 700 | `#B84628` | Accessible coral action with white text |
| Sage 50 | `#F3F7F4` | Planning tip surface and subtle positive state |
| Sage 100 | `#E4EDE6` | Sage container and quiet selected state |
| Sage 500 | `#6B9E7F` | Progress, planning, and non-text icon accent |
| Sage 600 | `#55856A` | Strong sage icon/text accent |
| Sage 700 | `#446B55` | Accessible sage text/action with white text |
| Cream 50 | `#FBF8F3` | Default page background |
| Cream 100 | `#F5F0E8` | Recessed surface and muted card area |
| Cream 200 | `#EDE6DA` | Borders and dividers |
| Ink 900 | `#1A1816` | Primary text and dark hero background |
| Ink 800 | `#2C2824` | Elevated dark surface |
| Ink 700 | `#3D3832` | Secondary text |
| White | `#FFFFFF` | Raised surfaces and inverse text |

Coral 500 expresses the mockup’s identity but does not meet the normal-text contrast requirement with white. Use Coral 700 for buttons carrying white labels in production. Coral 500 remains appropriate for large visual blocks, icons, and accents. Use Sage 700 when a sage surface needs white text.

### Semantic tokens: light

| Semantic token | Value |
|---|---:|
| `color.primary` | `#B84628` |
| `color.onPrimary` | `#FFFFFF` |
| `color.primaryContainer` | `#FCE8E0` |
| `color.onPrimaryContainer` | `#5C2416` |
| `color.secondary` | `#446B55` |
| `color.onSecondary` | `#FFFFFF` |
| `color.secondaryContainer` | `#E4EDE6` |
| `color.onSecondaryContainer` | `#203B2B` |
| `color.tertiary` | `#6B9E7F` |
| `color.onTertiary` | `#FFFFFF` |
| `color.background` | `#FBF8F3` |
| `color.onBackground` | `#1A1816` |
| `color.surface` | `#FFFFFF` |
| `color.onSurface` | `#1A1816` |
| `color.surfaceVariant` | `#F5F0E8` |
| `color.onSurfaceVariant` | `#3D3832` |
| `color.outline` | `#CFC5B7` |
| `color.error` | `#B3261E` |
| `color.onError` | `#FFFFFF` |

### Semantic tokens: dark

| Semantic token | Value |
|---|---:|
| `color.primary` | `#F08A68` |
| `color.onPrimary` | `#4A160B` |
| `color.primaryContainer` | `#7A2D19` |
| `color.onPrimaryContainer` | `#FFDAD0` |
| `color.secondary` | `#A3C8AE` |
| `color.onSecondary` | `#123524` |
| `color.secondaryContainer` | `#294E38` |
| `color.onSecondaryContainer` | `#BFE8C9` |
| `color.tertiary` | `#E7C7A4` |
| `color.onTertiary` | `#422B13` |
| `color.background` | `#1A1816` |
| `color.onBackground` | `#EEE8DF` |
| `color.surface` | `#24201D` |
| `color.onSurface` | `#EEE8DF` |
| `color.surfaceVariant` | `#39332D` |
| `color.onSurfaceVariant` | `#D5C9BD` |
| `color.outline` | `#9E9185` |
| `color.error` | `#FFB4AB` |
| `color.onError` | `#690005` |

### Color behavior

- Coral is the default CTA and social-action color; use Coral 700 for accessible white-label buttons.
- Sage signals planning, itinerary progress, calm reassurance, and positive status; it does not signal approval by itself.
- Cream is the default canvas. White is reserved for raised cards, bottom navigation, and primary sheets.
- Ink is used for headings, high-emphasis text, and the welcome/hero backdrop.
- Do not communicate state by color alone; pair it with text and an icon.
- Maps use muted warm neutrals with coral pins and sage route/status accents.

## 4. Typography

Use Cabinet Grotesk for expressive headings and Satoshi for interface text, matching the Superdesign mockup. Bundle only required weights and confirm font licensing before production.

| Style | Size / line height | Weight | Use |
|---|---|---:|---|
| Display | 34 / 38 sp | Cabinet Grotesk 800 | Welcome hero |
| Headline | 28 / 34 sp | Cabinet Grotesk 800 | Screen title |
| Title large | 22 / 28 sp | Cabinet Grotesk 700 | Sections and sheets |
| Title | 17 / 24 sp | Cabinet Grotesk 700 | Trip cards and itinerary days |
| Body | 15 / 22 sp | Satoshi 500 | Default reading |
| Body small | 14 / 20 sp | Satoshi 500 | Supporting detail |
| Label | 13 / 18 sp | Satoshi 600 | Buttons, tabs, role badges |
| Caption | 12 / 16 sp | Satoshi 500 | Metadata only |

Rules:

- Support dynamic type without truncating essential actions.
- Use sentence case; avoid all caps.
- Use tabular figures for dates, times, prices, and compatibility values.
- Destination names may occupy two lines; never reduce them below body size to force a fit.

## 5. Layout and spacing

Use a 4 dp base grid.

- Screen horizontal margin: 20 dp
- Compact spacing: 4 or 8 dp
- Related-content spacing: 12 dp
- Component padding: 16 dp
- Section spacing: 24 or 32 dp
- Minimum interactive target: 48×48 dp
- Compact card radius: 12 dp
- Default card/sheet radius: 16–20 dp
- Hero radius: 28 dp
- Modal top radius: 28 dp
- Maximum readable content width on expanded layouts: 720 dp

Respect iOS safe areas and Android system bars. On foldables/tablets, use a list-detail layout rather than stretching a phone column.

## 6. Elevation and surfaces

- Prefer cream borders and tonal separation over hard shadows.
- Level 0: Cream 50 page background.
- Level 1: white card with a 1 dp Cream 200 outline and soft shadow.
- Level 2: coral CTA or floating control with a warm coral-tinted 8–16 dp shadow.
- Level 3: modal sheets only.
- Never stack more than two raised surfaces.

## 7. Core components

### Trip card

Shows destination, date range, visibility, host, available spots, pace, and budget band. Open-trip cards also show two or three textual compatibility reasons. Do not expose an exact lodging address.

### Itinerary timeline

Use a vertical route line with coral waypoint nodes and sage planning markers. Each item shows time, type icon, title, duration, and collaboration state. Drag handles appear only in edit mode. Conflicts use a clear message and warning icon, not coral decoration alone.

### Companion chip

Avatar, display name, and role/status. Use overlapping 28–32 dp avatar stacks with a 2 px cream border; a coral or sage ring may mark a defined role/status. Presence is never inferred unless explicitly supported.

### Compatibility summary

Lead with language such as “Strong date and pace fit,” followed by evidence chips. A numeric percentage may be secondary, never the only explanation. Avoid romantic/dating visual conventions such as hearts or swipe cards.

### Buttons

- Filled coral: one primary action per view; use Coral 700 with white text in production.
- White/outlined: secondary action with Cream 200 border.
- Text: low-emphasis action.
- Sage filled: a quiet planning action or positive, non-destructive status only; use Sage 700 with white text.
- Destructive: error red with confirmation proportional to impact.

### Status and privacy labels

Always show an icon and word: Private, Unlisted, Open, Pending, Approved, Declined, or Closed. Do not rely on padlock color alone.

## 8. Navigation

Phone destinations:

1. Trips
2. Discover
3. Activity
4. Profile

Use bottom navigation for four top-level destinations. Creating a trip is the prominent action from Trips and Discover; it should not become a permanent fifth destination. Within a trip, use Overview, Itinerary, and Members as anchored sections or tabs.

Deep links must resolve invitation and join-request destinations after authentication and return the user to the intended screen.

## 9. Screen patterns

### Home / Trips

- Cream header with logo at left, notification control and avatar at right
- Uppercase coral eyebrow, personal greeting, and short supporting line
- Full-width coral Create a new trip card with a white translucent icon tile
- White active-trip cards with role/visibility labels, destination thumbnail, date row, avatar stack, and itinerary-progress line
- Sage planning tip below active cards
- White bottom navigation; the active tab uses coral icon and label
- Draft/past sections and an empty state with “Create a trip” and a lightweight example

### Create trip

Use a short step flow: destination and dates → travel style → privacy and capacity → review. Save progress locally. Explain that exact details stay private before offering Open visibility.

### Itinerary

Day selector at top, timeline beneath, sticky coral “Add” action. AI generation begins with a parameter sheet and always ends at a reviewable preview. Keep the timeline on a cream canvas with white cards, coral waypoints, and sage planning/status accents.

### Discover

Search and filter first; avoid an infinite entertainment feed. Each result emphasizes dates, budget, pace, capacity, and why it matches.

### Join request

Use a confirmation sheet containing what the host will see, safety guidance, and an editable introduction. Sending a request is explicit and reversible while pending.

### Paywall

Show Organizer Pro at the point of value, after the free path is understood. State billing period, trial terms, renewal behavior, restoration, and what remains free. Do not use countdowns, fake scarcity, preselected consent, or visually hidden close controls.

## 10. Content design

Voice: clear, warm, direct, and non-judgmental.

- Say “Request to join,” not “Match now.”
- Say “This plan may need review,” not “AI failed.”
- Say “Only approved members can see this,” not vague “secure” claims.
- Prefer “travel companion” or “trip member” over “stranger.”
- Never promise that another user is safe, verified, or trustworthy without a defined verification process.
- Dates include month names where ambiguity is possible; prices always show currency.

## 11. Motion

- Standard transition: 240–250 ms, ease-in-out with an opacity crossfade.
- Initial content: a 0.55 s upward reveal, staggered by 0.06–0.08 s for adjacent sections.
- Reordering: spring motion with low overshoot.
- New itinerary item: fade and short vertical settle, no celebratory confetti.
- Join approval: a restrained route-merge animation may be used once.
- Respect reduced-motion preferences; replace movement with crossfades under 150 ms.
- Motion must explain hierarchy or state, not delay task completion.

## 12. Accessibility

- Meet WCAG 2.2 AA contrast for text and meaningful controls.
- Provide semantic labels for all icons, itinerary ordering, and member roles.
- Maintain logical focus order after sheets, generation previews, and drag operations.
- Provide non-drag alternatives for itinerary reordering.
- Support 200% text scaling for essential journeys.
- Use shapes/text in addition to color for trip and request status.
- Announce live changes such as “Request sent” without moving focus unexpectedly.

## 13. Empty, loading, error, and offline states

Every data-driven screen defines:

- Skeleton or progress state without fake content
- First-use empty state with one useful next action
- Filtered-empty state that preserves filter controls
- Recoverable error with retry
- Permission-denied state that does not leak entity existence
- Offline state with last-updated timestamp

Never show fabricated open trips as though they were real. Clearly label product tours or sample data.

## 14. Design tooling and workflow

### Recommended stack

1. **Superdesign** — the live mockup source for TripTandem. Continue screens in project `84aed4c3-942f-4180-b4ff-291c859875f4`; preserve its warm cream/coral/sage direction unless a deliberate rebrand is approved.
2. **Figma Design** — production design source of truth for components, tokens, detailed prototypes, and store-screen compositions. Create variables named exactly like the semantic tokens in this file and use Light/Dark modes.
3. **FigJam** — interview synthesis, user flows, and scope mapping; keep ideation separate from final UI pages.
4. **Compose Multiplatform previews and Hot Reload** — validate actual typography, localization, insets, and platform behavior continuously. The coded component is the final truth for runtime behavior.
5. **Android Studio Image Asset Studio** — generate Android adaptive, legacy, and Play launcher assets from an optically corrected icon master.
6. **Xcode asset catalogs** — generate iOS icon variations from the approved 1024×1024 source.
7. **Accessibility Scanner, Android TalkBack, and iOS VoiceOver** — required validation, not optional polish.

Avoid introducing a second high-fidelity UI design tool during the hackathon. Rive or ProtoPie can help with a single award-demo animation, but only after the two store builds and core accessibility pass are stable.

### Figma file structure

- `00 Cover & decisions`
- `01 Foundations`
- `02 Components`
- `03 Core trip flow`
- `04 AI & monetization`
- `05 Open trips & safety`
- `06 Prototype`
- `07 Store assets`
- `99 Archive`

Name frames `Platform / Flow / State`, for example `Shared / Create trip / Dates error`. Name components and variables to mirror Compose identifiers wherever practical.

### Handoff rule

No screen is ready to build until it includes default, loading, empty, error, offline, large-text, and dark-mode behavior—or explicitly records why a state cannot occur.

## 15. Design review checklist

- One obvious primary action
- Essential action usable with one hand where practical
- No private location or contact information in previews
- Light and dark themes checked
- 200% text and longest expected copy checked
- 48 dp targets and focus order checked
- Loading, empty, error, and offline states included
- Purchase language matches store configuration
- Component and token names match code
- Android and iOS platform conventions preserved where behavior differs
