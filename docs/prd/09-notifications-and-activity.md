# PRD 09 — Notifications and Activity

Phase: 3  
Priority: P1  
Owner: Product + engagement  
Status: Ready after collaboration states stabilize

## Outcome

Notify users only when an action or material trip change needs attention, while keeping a durable in-app activity record and protecting private details on lock screens.

## User stories

- As an owner, I know when a join request needs review.
- As an applicant, I know when my request is approved, declined, expired, or invalidated.
- As a member, I know when dates, destination, cancellation, or my role changes.
- As a user, I can control categories and avoid noisy itinerary updates.
- As a privacy-conscious traveler, lock-screen messages reveal no sensitive trip details by default.

## Channels and priority

### Transactional push + activity

- Invitation accepted
- Join request received
- Request approved/declined/expired/invalidated
- Role changed or membership removed
- Trip cancelled
- Material destination/date change
- Ownership transfer request

### Activity only by default

- Itinerary item added/edited/deleted
- AI generation completed when the requester is active in-app
- Trip reached capacity

### Never send

- Engagement bait, popularity updates, or “someone viewed your profile”
- Exact meeting/lodging details
- Member introduction or report text
- Marketing without explicit consent

## Functional requirements

1. In-app activity is the authoritative user-facing history; push is a delivery convenience.
2. Notification creation is server-side, idempotent, and triggered only after the source transaction commits.
3. Default lock-screen copy uses generic text such as “A TripTandem request needs your attention.”
4. Tapping resolves to current state after authentication. Stale notifications never recreate expired actions.
5. Users control push globally and by `requests`, `membership`, `trip_changes`, and `reminders` categories.
6. Safety and account-security notices may be mandatory but remain non-marketing.
7. Itinerary edits are grouped into a digest per trip rather than sent individually.
8. Activity supports read/unread, mark all read, pagination, and deletion according to retention policy.
9. Badge count reflects actionable unread items, not every historical event.
10. Delivery tokens are associated with user and installation, rotated safely, and deleted on sign out/account deletion.
11. Quiet-hour preference uses user timezone; urgent account-security notices bypass it.
12. If OneSignal is used for the sponsor category, App ID and campaign evidence are documented without embedding privileged credentials.

## Deep-link behavior

- Signed out: authenticate, then continue to intended current-state screen.
- No permission: show neutral unavailable state without leaking private data.
- Entity deleted: show resolved activity with explanation.
- Request already decided: show final status, not old action buttons.
- User removed from trip: clear protected trip cache and show membership-ended screen.

## Edge cases

- Duplicate provider webhook: idempotency prevents duplicate activity/push.
- Multiple devices: read state synchronizes; installation preferences may differ while account category preferences remain shared.
- Push denied by OS: activity center remains complete and preferences explain device setting.
- Owner receives many requests: group count notification with no applicant names on lock screen.
- Timezone changes during travel: quiet hours follow explicit preference or current device timezone according to setting.

## Analytics

- `notification_permission_prompted(context)`
- `notification_permission_result(result)`
- `notification_created(type, channel)`
- `notification_opened(type, age_bucket)`
- `activity_action_completed(type)`
- `notification_preference_changed(category, enabled)`

Never log push token, title/body, trip/user ID, destination, request introduction, or private change content.

## Acceptance criteria

- Every transactional event creates at most one logical activity item.
- Lock-screen previews contain no destination, dates, names, exact place, or request text by default.
- Push opens the current authoritative state across cold/warm starts on both platforms.
- Disabling a category prevents future optional push while retaining required in-app transaction records.
- Removing/signing out an account invalidates that installation’s delivery token.
- Digest behavior prevents one push per itinerary mutation.
- Screen-reader focus lands on the relevant status/action after deep-link navigation.

## Dependencies and open decisions

- Depends on stable invitation, request, trip, and moderation state machines.
- Ask for push permission only after the user performs an action whose updates have clear value.
- Saved-search alerts remain P2.
