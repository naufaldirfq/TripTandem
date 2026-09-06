# UI review — 6 September 2026

Reference project: `84aed4c3-942f-4180-b4ff-291c859875f4`.
Read the current Superdesign HTML for Welcome, Sign Up, Profile Setup,
Account Settings, Trip Dashboard, Create Trip, Shared Itinerary, Trip Overview,
Trip Members, Invite Members, Add Activity, and Trip Settings.

## Changes

- Welcome uses the approved warm background, dashed route, bundled traveler
  photos, coral final headline, and Create a free account / Log in actions.
- Sign-up opens authentication. New accounts only need a display name and
  explicit age/policy confirmation before reaching the dashboard. Travel
  preferences are edited later; no consent or location is inferred.
- Apple sign-in is iOS-only. Both providers have bundled logo assets.
- Every shared text input uses the custom persistent-label field, including
  authentication, profiles, trip settings, activities, generation and invites.
- Dashboard branding reads TripTandem.
- Profile opens Account settings. Edit profile opens the profile editor;
  saving/cancelling returns to settings. The onboarding progress bar is removed.
- Profile uses a region chooser, wrapped selection chips, pace cards and
  budget cards. Existing account recovery, Pro and deletion behavior remains.
- Create Trip uses destination/dates, travel style, and privacy/group steps.
  Dates use a custom calendar; destination and timezone use separate pickers.
- Invalid restored dates, reversed dates, impossible dates, trips over 60 days,
  invalid timezones, missing destinations/titles and invalid currency codes
  receive specific errors. Local validation runs before the repository call.
  Server errors remain visible next to the action instead of being hidden
  at the bottom of the scrolling form.
- Edit Trip and Add Activity use full-page forms with fixed actions. Trip edit
  and generation day selection also use calendar cards.
- Enabled shared Android resource packaging; without it the generated image
  accessors compiled but the APK omitted their assets.

## Verification

- Android debug APK builds.
- Shared iOS simulator Kotlin source compiles (not an iOS runtime test).
- 18 unit tests pass, including malformed-draft and date-validation regressions.
- Emulator regression suite covers account/login routing, Android provider
  visibility, settings/editor routing, and three-step trip creation using local
  repositories. The captured pass is in `files/`; the final source then adds
  the full-page activity/trip-settings form shell and the picker refinements.
- These checks do not authenticate a live Google/Apple account or create a
  production Firebase trip.

## Scope of mockup parity

Existing supported flows receive the shared component updates. Mockup-only
features without a working repository/API (profile photo upload, phone
verification, notification preferences, export, blocking/reporting, public
trip discovery) have not been represented as working controls. The app's
existing feature gates remain in place. This is not a claim that every
mockup-only feature has been implemented.

Packaging reference:
https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-multiplatform-resources-setup.html
