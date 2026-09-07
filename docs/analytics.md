# Analytics taxonomy and funnel reconciliation

TripTandem has a shared, privacy-safe analytics interface. Android forwards it
to Firebase Analytics, and the iOS host forwards the same event names through
its Firebase bridge. Both platform wrappers apply an event and parameter
allowlist, so the interface remains intentionally coarse: it records workflow
metadata, never user-generated trip/profile content.

## Implemented events

| Event | Allowed parameters | Funnel meaning |
| --- | --- | --- |
| `screen_view` | coarse `screen_name` | app surface reached |
| `auth_anonymous_succeeded` | none | authenticated session available |
| `auth_anonymous_failed` | coarse `error_type` (exception class) | activation failure |
| `auth_session_restored` | none | returning session |
| `sign_up_started` | coarse `method` | profile flow started |
| `profile_essentials_completed` | none | required profile saved |
| `profile_optional_completed` | bounded `field_count_bucket` | optional profile saved |
| `profile_preview_opened` | none | visibility preview viewed |
| `trip_create_started` | coarse `source` | organizer intent |
| `trip_created` | `visibility`, `capacity_bucket` | private trip created |
| `trip_visibility_changed` | coarse `from`, `to` | access setting changed |
| `trip_conflict_detected` | none | stale trip edit rejected |
| `trip_cancelled` | `member_count_bucket` | trip made read-only |
| `trip_archived` | none | trip archived |
| `trip_deleted` | none | trip deleted |
| `itinerary_item_created` | `type`, `entry_method` | plan item added |
| `itinerary_item_updated` | bounded `field_group` | plan item edited |
| `itinerary_item_reordered` | bounded `method` | plan order changed |
| `itinerary_item_deleted` | none | plan item removed |
| `itinerary_conflict_detected` | bounded `conflict_type` | stale item edit rejected |
| `invite_created` | `role`, `expiry_bucket` | secure invite generated |
| `invite_share_opened` | `channel_category` | system share opened |
| `invite_accepted` | `role` | collaborator joined |
| `invite_failed` | bounded `reason_class` | invite could not be used |
| `member_role_changed` | bounded `from`, `to` | owner changed a role |
| `member_removed` | none | owner removed a member |
| `member_left` | none | member left a trip |
| `ownership_transferred` | none | owner transferred a trip |
| `itinerary_undo_restored` | none | removed item restored during Undo |
| `itinerary_day_viewed` | bounded `relative_day_bucket` | day selector used |
| `paywall_viewed` | `trigger`, `offering_id`, `entitlement_state` | value-triggered Pro offer viewed |
| `package_selected` | bounded `package_type` | monthly/annual choice made |
| `purchase_started` | bounded `package_type` | store purchase started |
| `purchase_completed` | `package_type`, `trial`, `localized_price_bucket`, `active` | RevenueCat purchase returned; `trial` is a coarse store-reported eligibility bucket |
| `purchase_cancelled` | bounded `package_type` | user dismissed store purchase |
| `purchase_failed` | bounded `package_type`, coarse `error_class` | store purchase failed |
| `restore_started` | none | restore requested |
| `restore_completed` | `result_class`, `active` | restore returned |
| `entitlement_changed` | `from`, `to`, `source` | Pro access changed |
| `generation_started` | `scope`, `entitlement_state`, `existing_item_bucket` | AI draft requested |
| `generation_completed` | `scope`, `latency_bucket`, `result_status`, `item_count_bucket` | AI job produced a preview |
| `generation_preview_edited` | none | preview selection or edit changed |
| `generation_applied` | `selected_count_bucket`, `duplicate_warning` | suggestions applied to itinerary |
| `generation_failed` | coarse `failure_class` | AI job failed or timed out |
| `generation_paywall_viewed` | coarse `trigger` | AI allowance gate viewed |
| `offline_cache_read` | bounded `freshness_bucket` | protected offline trip cache read |
| `offline_refresh_completed` | coarse `result` | manual or reconnect refresh attempted |
| `export_started` | bounded `format` | itinerary export preview opened |
| `export_completed` | bounded `format`, bounded `included_field_count` | itinerary summary shared or copied |
| `protected_cache_cleared` | bounded `reason` | protected local cache wiped |

The Phase 2 purchase and generation events and Phase 4 offline/export events above are implemented in the shared
UI and forwarded through platform allowlists. They remain intentionally coarse:
no purchase receipt, transaction ID, prompt, destination, dates, notes, or
generated text is included.

## Privacy contract

Never add itinerary text, destination strings, exact locations, dates, member
names, profile biographies, invite URLs/tokens, email addresses, message text,
or purchase identifiers as event parameters. Use coarse enums such as
`success`, `cancelled`, or a bounded error category. Analytics does not replace
the final privacy policy or a regional consent decision.

## Planned Phase 1–2 funnel

The release dashboard should reconcile these steps with distinct, documented
event definitions:

1. install/first open;
2. authenticated session;
3. profile essentials completed;
4. first trip created or joined;
5. three itinerary items created/generated;
6. first invitation or join request;
7. collaborator accepts or request is approved;
8. paywall view → package selection → purchase/restore outcome.

The current UI emits the events listed above when the corresponding feature is
available; Remote Config can still keep AI and community funnels disabled. Add a new event only when a
real user action exists, the event is represented in the PRD, and its
parameters pass a privacy review. Define the denominator, time window, and
platform split before comparing rates.

## Manual dashboard reconciliation

No production user dataset or dashboard export was available for this Phase 0
workspace. The release owner must create the Firebase Analytics/BigQuery or
equivalent privacy-reviewed view, reconcile install → activation →
collaboration → purchase, and document invite attribution. Report actual
counts and dates; do not present PRODUCT.md targets as measured results.

Before publishing results, verify that:

- Android and iOS event names and parameter types match;
- anonymous sessions are not double-counted after account linking;
- purchase outcomes are joined to entitlement state without exposing store
  transaction IDs;
- open-trip events are excluded or clearly labeled while the safety flags are
  false; and
- the final privacy disclosure and store declarations describe the collection.
