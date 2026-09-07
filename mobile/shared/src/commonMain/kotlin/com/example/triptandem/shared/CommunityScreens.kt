package com.triptandem.shared

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

private fun JsonObject.text(key: String): String = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
private fun JsonObject.obj(key: String): JsonObject = get(key) as? JsonObject ?: JsonObject(emptyMap())
private fun JsonObject.rows(key: String = "items"): List<JsonObject> = (get(key) as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
private fun JsonObject.strings(key: String): List<String> = (get(key) as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
private val reportCategories = linkedMapOf("harassment" to "Harassment or hate", "scam" to "Scam or payment request", "exploitation" to "Sexual or exploitative content", "impersonation" to "Impersonation", "dangerous_activity" to "Dangerous activity", "privacy" to "Privacy or location exposure", "spam" to "Spam", "underage" to "Underage-safety concern", "other" to "Other")

/** Shared screens use the application's semantic typography, light/dark surfaces and 48dp controls. */
@Composable
fun CommunityScreen(
    repository: CommunityRepository,
    mode: String,
    flags: TripTandemFeatureFlags,
    analytics: TripTandemAnalytics,
    tripId: String = "",
    onCreateTrip: () -> Unit = {},
    onOpenTrip: (String) -> Unit = {},
    onBack: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    var data by remember(mode, tripId) { mutableStateOf(JsonObject(emptyMap())) }
    var rows by remember(mode, tripId) { mutableStateOf<List<JsonObject>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var lastAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var destination by rememberSaveable { mutableStateOf("") }
    var destinations by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var start by rememberSaveable { mutableStateOf("") }; var end by rememberSaveable { mutableStateOf("") }
    var pace by rememberSaveable { mutableStateOf("") }; var budget by rememberSaveable { mutableStateOf("") }
    var language by rememberSaveable { mutableStateOf("") }; var interest by rememberSaveable { mutableStateOf("") }
    var spots by rememberSaveable { mutableStateOf("1") }
    var title by rememberSaveable { mutableStateOf("") }; var expectations by rememberSaveable { mutableStateOf("") }; var summary by rememberSaveable { mutableStateOf("") }
    var acknowledged by rememberSaveable { mutableStateOf(false) }
    var detail by remember { mutableStateOf<JsonObject?>(null) }
    var introduction by rememberSaveable { mutableStateOf("") }
    var showRequests by remember { mutableStateOf(false) }
    var requestTripId by remember { mutableStateOf(tripId) }
    var showSafety by remember { mutableStateOf(false) }
    var safetySubject by remember { mutableStateOf("") }
    var safetyType by remember { mutableStateOf("trip") }
    var showPreferences by remember { mutableStateOf(false) }
    var reportCategory by rememberSaveable { mutableStateOf("other") }
    var reportDetail by rememberSaveable { mutableStateOf("") }
    var reportKey by remember { mutableStateOf("report-${kotlin.random.Random.nextLong().toString().replace('-', 'n')}") }
    var pendingChoice by rememberSaveable { mutableStateOf("invalidate") }
    var reopenMode by rememberSaveable { mutableStateOf(false) }

    fun call(operation: String, input: Map<String, String> = emptyMap(), append: Boolean = false, done: (JsonObject) -> Unit = {}) {
        if (busy) return
        busy = true; error = false; message = ""
        scope.launch {
            when (val result = repository.execute(operation, input)) {
                is DataResult.Success -> {
                    val parsed = runCatching { Json.parseToJsonElement(result.value).jsonObject }.getOrNull()
                    if (parsed == null) { error = true; message = "This update could not be read. Please retry." }
                    else {
                        data = parsed
                        if (parsed.containsKey("items")) rows = if (append) (rows + parsed.rows()).distinctBy { it.text("tripId") + it.text("id") + it.text("applicantId") } else parsed.rows()
                        done(parsed)
                    }
                }
                is DataResult.Failure -> {
                    error = true
                    message = when (result.error) {
                        TripTandemError.Offline -> "You’re offline or the service is unavailable. Reconnect and retry; your draft is still here."
                        TripTandemError.Unauthenticated -> "Sign in to continue."
                        TripTandemError.PermissionDenied -> "This content is unavailable. Your access may have changed."
                        else -> "This action is unavailable or needs review. Refresh the current status, check your details, and try again."
                    }
                }
            }
            busy = false
        }
    }
    fun filters(cursor: String = "") = mapOf("destinationId" to destination, "startDate" to start, "endDate" to end, "pace" to pace, "budgetBand" to budget, "language" to language, "interest" to interest, "spots" to spots, "cursor" to cursor)
    fun search(append: Boolean = false) {
        call("search", filters(if (append) data.text("cursor") else ""), append) {
            analytics.logEvent("discover_search_performed", mapOf("result_count_bucket" to if (it.rows().isEmpty()) "0" else "1_plus", "filter_count" to filters().count { entry -> entry.value.isNotEmpty() && entry.key != "cursor" }.toString()))
            if (it.rows().isEmpty()) analytics.logEvent("discover_empty_viewed", mapOf("filter_count" to filters().count { entry -> entry.value.isNotEmpty() && entry.key != "cursor" }.toString()))
        }
    }
    fun load() {
        when (mode) {
            "discover", "publish" -> if (flags.communityDiscoveryEnabled) call("destinations") { destinations = it.rows(); rows = emptyList() }
            "requests" -> call("requests", if (tripId.isEmpty()) emptyMap() else mapOf("tripId" to tripId))
            "activity" -> call("activity")
            "safety" -> call("blocks")
        }
    }
    LaunchedEffect(mode, tripId) { load() }

    if (showRequests) {
        CommunityScreen(repository, "requests", flags, analytics, requestTripId, onOpenTrip = onOpenTrip, onBack = { showRequests = false }); return
    }
    if (showSafety && safetySubject.isEmpty()) {
        CommunityScreen(repository, "safety", flags, analytics, onBack = { showSafety = false }); return
    }
    Column(Modifier.fillMaxSize().widthIn(max = 720.dp).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        onBack?.let { TextButton(onClick = it) { Text("Back") } }
        Text(when(mode) { "discover" -> "Find your travel people"; "publish" -> "Open your trip"; "requests" -> "Join requests"; "activity" -> "Activity"; else -> "Safety & community" }, style = MaterialTheme.typography.headlineMedium)
        if (busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Loading current information…") }
        if (message.isNotEmpty()) {
            Text(message, color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            if (error) OutlinedButton(onClick = { lastAction?.invoke() ?: load() }, enabled = !busy) { Text("Retry") }
        }
        if (mode in listOf("discover", "publish") && !flags.communityDiscoveryEnabled) {
            CommunityCard {
                Text("Community trips are not open yet", style = MaterialTheme.typography.titleLarge)
                Text("Plan a private trip and invite friends while the community prepares to welcome travelers.")
                Button(onClick = onCreateTrip) { Text("Create a private trip") }
                Text("Already planning? Open a trip’s Members tab to invite friends.")
            }
        } else when (mode) {
            "discover" -> {
                Text("Search by destination, then choose dates and the travel style that works for you.")
                CommunityChoice("Destination", destination, destinations.associate { it.text("id") to it.text("label") }) { destination = it }
                CommunityField("Start date (YYYY-MM-DD, optional)", start, 10) { start = it }
                CommunityField("End date (YYYY-MM-DD, optional)", end, 10) { end = it }
                CommunityChoice("Pace", pace, linkedMapOf("" to "Any pace", "relaxed" to "Relaxed", "balanced" to "Balanced", "packed" to "Packed")) { pace = it }
                CommunityChoice("Budget", budget, linkedMapOf("" to "Any budget", "budget" to "Budget", "moderate" to "Moderate", "comfort" to "Comfort", "premium" to "Premium")) { budget = it }
                CommunityField("Language (optional)", language, 40) { language = it }
                CommunityField("Interest (optional)", interest, 40) { interest = it }
                CommunityField("Available spots needed", spots, 2) { spots = it.filter(Char::isDigit) }
                Button(onClick = { lastAction = { search() }; search() }, enabled = !busy && destination.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Find open trips") }
                if (!busy && rows.isEmpty()) CommunityCard {
                    Text("Room for a new adventure", style = MaterialTheme.typography.titleMedium)
                    Text("No open trips to show. Adjust your filters, or create a private trip and invite friends.")
                    OutlinedButton(onClick = onCreateTrip) { Text("Create a private trip") }
                }
                rows.forEachIndexed { index, listing -> CommunityCard {
                    ProjectionSummary(listing)
                    listing.obj("fit").strings("reasons").take(3).forEach { Text("• $it") }
                    OutlinedButton(onClick = {
                        call("detail", filters() + ("tripId" to listing.text("tripId"))) { detail = it; acknowledged = false; introduction = ""; analytics.logEvent("compatibility_viewed", mapOf("label" to it.obj("fit").text("label"), "reason_count" to it.obj("fit").strings("reasons").size.toString(), "missing_field_count" to it.obj("fit").strings("missingFields").size.toString())); analytics.logEvent("join_request_started") }
                        analytics.logEvent("discover_result_opened", mapOf("rank_bucket" to if (index < 3) "top_3" else "4_plus", "reason_count" to listing.obj("fit").strings("reasons").size.toString()))
                    }, enabled = !busy) { Text("Review trip") }
                } }
                if (data.text("cursor").isNotEmpty()) OutlinedButton(onClick = { search(true) }, enabled = !busy) { Text("More results") }
            }
            "publish" -> {
                Text("Only approved members see exact itinerary details. Review every public field before sending your listing for review.")
                CommunityChoice("Broad destination", destination, destinations.associate { it.text("id") to it.text("label") }) { destination = it; data = JsonObject(emptyMap()) }
                CommunityField("Public title", title, 100) { title = it; data = JsonObject(emptyMap()) }
                CommunityField("Group expectations (20–300 characters)", expectations, 300) { expectations = it; data = JsonObject(emptyMap()) }
                CommunityField("Broad itinerary themes", summary, 200) { summary = it; data = JsonObject(emptyMap()) }
                Text("No contact details, payments, hotel names, exact meeting points, or external messaging handles.")
                val input = mapOf("tripId" to tripId, "destinationId" to destination, "title" to title, "expectationNote" to expectations, "summary" to summary)
                OutlinedButton(onClick = { call("preview", input); analytics.logEvent("open_publish_started") }, enabled = !busy) { Text("Preview public listing") }
                if (data.containsKey("projection")) CommunityCard {
                    Text("Your exact public preview", style = MaterialTheme.typography.titleLarge)
                    ProjectionSummary(data.obj("projection"))
                    CommunityCheck("I acknowledge host responsibilities and community rules", acknowledged) { acknowledged = it }
                    Button(onClick = { call(if (reopenMode) "reopen" else "publish", input + mapOf("previewHash" to data.text("previewHash"), "acknowledged" to "true")) {
                        message = "Your listing is waiting for review. It is not public yet."
                        analytics.logEvent("open_publish_completed", mapOf("capacity_bucket" to "standard", "trip_length_bucket" to "unknown"))
                        analytics.logEvent("public_content_held", mapOf("reason_class" to "moderation_review"))
                    } }, enabled = !busy && acknowledged) { Text(if (reopenMode) "Send reopening for review" else "Send for review") }
                }
                HorizontalDivider()
                CommunityChoice("When closing discovery", pendingChoice, linkedMapOf("invalidate" to "Close pending requests", "keep" to "Keep pending requests for a later reopening")) { pendingChoice = it }
                OutlinedButton(onClick = { call("close", mapOf("tripId" to tripId, "pendingChoice" to pendingChoice)) { message = "Discovery closed. Approved members still have their trip."; reopenMode = true; analytics.logEvent("open_trip_closed", mapOf("reason_class" to pendingChoice)) } }, enabled = !busy) { Text("Close discovery") }
                OutlinedButton(onClick = { reopenMode = true; call("preview", input) }, enabled = !busy) { Text("Preview reopening") }
                TextButton(onClick = { showRequests = true }) { Text("Review join requests") }
            }
            "requests" -> {
                Text("Compatibility is informational. Review expectations and use the safety controls whenever needed.")
                OutlinedButton(onClick = { call("requests", if (tripId.isEmpty()) emptyMap() else mapOf("tripId" to tripId)) }, enabled = !busy) { Text("Refresh current status") }
                if (!busy && rows.isEmpty()) Text("No requests here yet. You can still plan privately and invite friends.")
                rows.forEach { row -> CommunityCard {
                    Text(row.text("status").replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleMedium)
                    if (row.text("status") == "pending") {
                        ProfileSummary(row.obj("sharedProfile"))
                        Text(row.obj("fit").text("label").ifEmpty { "Not enough information" })
                        row.obj("fit").strings("reasons").forEach { Text("• $it") }
                        if (row.text("introduction").isNotEmpty()) Text(row.text("introduction"))
                        Text("Profile shown is current. Requests expire within seven days or when the trip begins.")
                        if (tripId.isNotEmpty()) {
                            Button(onClick = { call("approve", mapOf("tripId" to tripId, "applicantId" to row.text("applicantId"))) { message = "Approved. The traveler now has member access."; rows = rows.filterNot { it == row }; analytics.logEvent("join_request_reviewed", mapOf("decision" to "approved", "age_bucket" to "current", "capacity_bucket" to "available")) } }, enabled = !busy && flags.joinRequestsEnabled) { Text("Approve request") }
                            OutlinedButton(onClick = { call("decline", mapOf("tripId" to tripId, "applicantId" to row.text("applicantId"))) { message = "Request declined."; rows = rows.filterNot { it == row }; analytics.logEvent("join_request_reviewed", mapOf("decision" to "declined", "age_bucket" to "current", "capacity_bucket" to "available")) } }, enabled = !busy) { Text("Decline") }
                            TextButton(onClick = { safetySubject = row.text("applicantId"); safetyType = "user"; showSafety = true; analytics.logEvent("safety_control_opened", mapOf("source" to "request")) }) { Text("Report or block") }
                        } else OutlinedButton(onClick = { call("withdraw", mapOf("tripId" to row.text("tripId"))) { message = "Request withdrawn."; rows = rows.filterNot { it == row }; analytics.logEvent("join_request_withdrawn", mapOf("age_bucket" to "current")) } }, enabled = !busy) { Text("Withdraw request") }
                    }
                    if (row.text("status") == "approved") OutlinedButton(onClick = { onOpenTrip(row.text("tripId")) }) { Text("Open current trip") }
                } }
                if (data.text("cursor").isNotEmpty()) OutlinedButton(onClick = { call("requests", mapOf("cursor" to data.text("cursor")) + if (tripId.isEmpty()) emptyMap() else mapOf("tripId" to tripId), true) }, enabled = !busy) { Text("More requests") }
            }
            "activity" -> {
                Text("Your durable history of requests and meaningful trip changes. Push is optional.")
                OutlinedButton(onClick = { call("activity") }, enabled = !busy) { Text("Refresh activity") }
                TextButton(onClick = { call("read") { rows = rows.map { JsonObject(it + ("read" to JsonPrimitive(true))) }; analytics.logEvent("activity_action_completed", mapOf("type" to "mark_all_read")) } }, enabled = !busy) { Text("Mark all read") }
                if (!busy && rows.isEmpty()) Text("You’re all caught up. Request decisions and trip updates will appear here.")
                rows.forEach { row -> CommunityCard {
                    Text(activityLabel(row.text("type")), style = MaterialTheme.typography.titleMedium)
                    Text(if (row.text("read") == "true") "Read" else if (row.text("actionable") == "true") "Unread · Needs attention" else "Unread")
                    OutlinedButton(onClick = {
                        call("read", mapOf("activityId" to row.text("id")))
                        if (row.text("type").contains("request")) { requestTripId = if (row.text("type") == "join_request_received") row.text("tripId") else ""; showRequests = true } else onOpenTrip(row.text("tripId"))
                        analytics.logEvent("notification_opened", mapOf("type" to row.text("type"), "age_bucket" to "history"))
                    }, enabled = !busy) { Text("View current status") }
                } }
                if (data.text("cursor").isNotEmpty()) OutlinedButton(onClick = { call("activity", mapOf("cursor" to data.text("cursor")), true) }, enabled = !busy) { Text("Older activity") }
                TextButton(onClick = { call("preferences") { showPreferences = true; analytics.logEvent("notification_permission_prompted", mapOf("context" to "activity_preferences")) } }, enabled = !busy) { Text("Notification preferences") }
            }
            "safety" -> {
                Text("Meet in public, tell someone you trust about your plans, and keep payments out of TripTandem. Compatibility is not a safety guarantee.")
                Text("For imminent danger, contact local emergency services. TripTandem does not provide emergency response.")
                Text("Community rules", style = MaterialTheme.typography.titleLarge)
                Text("Adults only. Respect consent and privacy. No harassment, hate, exploitation, impersonation, scams, payment requests, or sharing exact private locations. Reports are reviewed by people; blocking is immediate and does not notify the other user.")
                Text("Blocking someone does not silently remove shared trip access. Leave a shared trip from Members, or ask the owner/support to remove the other member.")
                TextButton(onClick = { uriHandler.openUri("https://triptandem.app/terms") }) { Text("Terms") }
                TextButton(onClick = { uriHandler.openUri("https://triptandem.app/privacy") }) { Text("Privacy policy") }
                TextButton(onClick = { uriHandler.openUri("https://triptandem.app/support") }) { Text("Support & escalation") }
                Text("Blocked accounts", style = MaterialTheme.typography.titleLarge)
                if (rows.isEmpty() && !busy) Text("You have no blocked accounts.")
                rows.forEach { row -> CommunityCard {
                    Text("Blocked account · ${row.text("userId").takeLast(6)}")
                    var confirm by remember(row.text("userId")) { mutableStateOf(false) }
                    CommunityCheck("Unblock this account? Previous requests and invitations will not be restored.", confirm) { confirm = it }
                    OutlinedButton(onClick = { call("unblock", mapOf("userId" to row.text("userId"), "confirmed" to "true")) { rows = rows.filterNot { it == row }; message = "Account unblocked." } }, enabled = confirm && !busy) { Text("Unblock") }
                } }
            }
        }
        if (mode == "discover") TextButton(onClick = { showRequests = true }) { Text("My join requests") }
        if (mode != "safety") TextButton(onClick = { safetySubject = ""; showSafety = true; analytics.logEvent("safety_control_opened", mapOf("source" to mode)) }) { Text("Safety & community rules") }
        Spacer(Modifier.height(16.dp))
    }
    detail?.let { view ->
        CommunityDialog("Review this trip", { detail = null }) {
            ProjectionSummary(view.obj("projection"))
            Text(view.obj("fit").text("label"), style = MaterialTheme.typography.titleLarge)
            view.obj("fit").strings("reasons").forEach { Text("• $it") }
            Text("Compatibility is informational, never a guarantee of safety.")
            HorizontalDivider(); Text("What the host will receive", style = MaterialTheme.typography.titleLarge)
            ProfileSummary(view.obj("sharedProfile"))
            CommunityField("Optional introduction (20–300 characters)", introduction, 300) { introduction = it }
            Text("Meet in a public place, share plans with someone you trust, and do not send deposits or contact details.")
            CommunityCheck("I understand the guidance and consent to share this profile and introduction", acknowledged) { acknowledged = it }
            Button(onClick = { call("request", mapOf("tripId" to view.obj("projection").text("tripId"), "introduction" to introduction, "startDate" to start, "endDate" to end, "profileHash" to view.text("profileHash"), "acknowledged" to "true")) { detail = null; message = "Request sent. You can withdraw it while pending."; analytics.logEvent("join_request_submitted", mapOf("intro_length_bucket" to if (introduction.isEmpty()) "0" else "20_300")) } }, enabled = !busy && acknowledged && flags.joinRequestsEnabled && (introduction.isEmpty() || introduction.length >= 20)) { Text("Request to join") }
            OutlinedButton(onClick = { call("block", mapOf("tripId" to view.obj("projection").text("tripId"))) { detail = null; rows = emptyList(); message = "Host blocked. You will no longer see each other’s public trips." } }, enabled = !busy) { Text("Block host") }
            TextButton(onClick = { safetySubject = view.obj("projection").text("tripId"); safetyType = "trip"; showSafety = true; detail = null; analytics.logEvent("safety_control_opened", mapOf("source" to "trip_detail")) }) { Text("Report trip") }
            if (busy) CircularProgressIndicator()
            if (error) Text(message, color = MaterialTheme.colorScheme.error)
        }
    }
    if (showSafety && safetySubject.isNotEmpty()) CommunityDialog("Report or block", { showSafety = false }) {
        Text("For imminent danger, contact local emergency services. TripTandem is not an emergency service.")
        CommunityChoice("Report category", reportCategory, reportCategories) { reportCategory = it }
        CommunityField("Optional details; avoid unnecessary sensitive information", reportDetail, 2000) { reportDetail = it }
        Button(onClick = { call("report", mapOf("subjectType" to safetyType, "subjectId" to safetySubject, "category" to reportCategory, "detail" to reportDetail, "idempotencyKey" to reportKey)) { message = "Report received. Reference: ${it.text("reference")}. Our team will review it; we cannot promise an immediate response."; reportKey = "report-${kotlin.random.Random.nextLong().toString().replace('-', 'n')}"; showSafety = false; analytics.logEvent("report_submitted", mapOf("category" to reportCategory, "subject_type" to safetyType)) } }, enabled = !busy) { Text("Submit report") }
        if (safetyType == "user") OutlinedButton(onClick = { call("block", mapOf("userId" to safetySubject)) { message = it.text("guidance"); showSafety = false; rows = rows.filterNot { it.text("applicantId") == safetySubject }; analytics.logEvent("block_completed", mapOf("context" to "request")) } }, enabled = !busy) { Text("Block account") }
        if (busy) CircularProgressIndicator()
        if (error) Text(message, color = MaterialTheme.colorScheme.error)
    }
    if (showPreferences) CommunityPreferences(data, flags, busy, onDismiss = { showPreferences = false }) { values -> call("preferences", values + ("save" to "true")) { showPreferences = false; message = "Preferences saved. In-app activity stays available."; analytics.logEvent("notification_preference_changed", mapOf("category" to "all", "enabled" to values["push"].orEmpty())); analytics.logEvent("notification_permission_result", mapOf("result" to values["push"].orEmpty())) } }
}

@Composable private fun CommunityCard(content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp), colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
@Composable private fun CommunityField(label: String, value: String, max: Int, change: (String) -> Unit) {
    OutlinedTextField(value, { if (it.length <= max) change(it) }, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), minLines = 1, maxLines = 5)
}
@Composable private fun CommunityChoice(label: String, selected: String, options: Map<String, String>, change: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("$label: ${options[selected] ?: "Choose"}") }
        DropdownMenu(expanded, { expanded = false }) { options.forEach { (value, text) -> DropdownMenuItem(text = { Text(text) }, onClick = { change(value); expanded = false }) } }
    }
}
@Composable private fun CommunityCheck(label: String, value: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Checkbox(value, change); Text(label, Modifier.weight(1f).padding(top = 12.dp)) }
}
@Composable private fun ProjectionSummary(p: JsonObject) {
    Text(p.text("title"), style = MaterialTheme.typography.titleLarge)
    Text(p.text("destination")); Text("${p.text("startDate")} – ${p.text("endDate")}")
    Text("Open · ${p.text("remainingSpots")} spots · ${p.text("pace")} pace · ${p.text("budgetBand")} budget")
    Text("Host ${p.obj("host").text("initials")} · ${p.strings("languages").joinToString()}")
    if (p.strings("interests").isNotEmpty()) Text(p.strings("interests").joinToString())
    Text(p.text("expectationNote")); Text(p.text("summary"))
    Text("Only approved members can see private itinerary details.", style = MaterialTheme.typography.bodySmall)
}
@Composable private fun ProfileSummary(p: JsonObject) {
    Text("Initials: ${p.text("initials")}")
    Text("Pace: ${p.text("pace").ifEmpty { "Not provided" }} · Budget: ${p.text("budgetBand").ifEmpty { "Not provided" }}")
    Text("Languages: ${p.strings("languages").joinToString()}")
    Text("Interests: ${p.strings("interests").joinToString().ifEmpty { "Not provided" }}")
}
@Composable private fun CommunityDialog(title: String, dismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = dismiss) {
        Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)) {
            Column(Modifier.heightIn(max = 760.dp).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge); TextButton(onClick = dismiss) { Text("Close") }; content()
            }
        }
    }
}
@Composable private fun CommunityPreferences(initial: JsonObject, flags: TripTandemFeatureFlags, busy: Boolean, onDismiss: () -> Unit, save: (Map<String, String>) -> Unit) {
    var push by remember { mutableStateOf(initial.text("push") == "true") }
    var requests by remember { mutableStateOf(initial.text("requests") != "false") }
    var membership by remember { mutableStateOf(initial.text("membership") != "false") }
    var changes by remember { mutableStateOf(initial.text("trip_changes") != "false") }
    var reminders by remember { mutableStateOf(initial.text("reminders") == "true") }
    var timezone by remember { mutableStateOf(initial.text("timezone").ifEmpty { "UTC" }) }
    var start by remember { mutableStateOf(initial.text("quietStart").ifEmpty { "22" }) }
    var end by remember { mutableStateOf(initial.text("quietEnd").ifEmpty { "8" }) }
    CommunityDialog("Notification preferences", onDismiss) {
        Text("In-app history stays available even when push is off. Lock-screen messages contain no private travel details.")
        if (flags.pushEnabled) CommunityCheck("Enable push notifications", push) { push = it } else Text("Push delivery is not enabled yet. Activity remains available here.")
        CommunityCheck("Requests", requests) { requests = it }; CommunityCheck("Membership", membership) { membership = it }
        CommunityCheck("Trip changes", changes) { changes = it }; CommunityCheck("Reminders", reminders) { reminders = it }
        CommunityField("Quiet-hours timezone", timezone, 80) { timezone = it }
        CommunityField("Quiet hours start (0–23)", start, 2) { start = it }; CommunityField("Quiet hours end (0–23)", end, 2) { end = it }
        Button(onClick = { save(mapOf("push" to push.toString(), "requests" to requests.toString(), "membership" to membership.toString(), "trip_changes" to changes.toString(), "reminders" to reminders.toString(), "timezone" to timezone, "quietStart" to start, "quietEnd" to end)) }, enabled = !busy) { Text("Save preferences") }
    }
}
private fun activityLabel(type: String) = when(type) {
    "join_request_received" -> "A join request needs your attention"
    "request_approved" -> "Your request was approved"
    "request_declined" -> "Your request was declined"
    "request_withdrawn" -> "Request withdrawn"
    "request_expired" -> "Your request expired"
    "request_invalidated" -> "A request is no longer available"
    "membership_removed" -> "Your membership has ended"
    "role_changed" -> "Your trip role changed"
    "trip_cancelled" -> "A trip was cancelled"
    "trip_changed" -> "A trip has important changes"
    "trip_reached_capacity" -> "A trip reached capacity"
    "invitation_accepted" -> "An invitation was accepted"
    "itinerary_digest" -> "Your group updated the itinerary"
    else -> "A TripTandem update is available"
}

@Composable
fun MemberSafetyActions(repository: CommunityRepository, userId: String) {
    var open by remember { mutableStateOf(false) }; var category by remember { mutableStateOf("other") }
    var detail by remember { mutableStateOf("") }; var message by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }
    val key = remember { "report-${kotlin.random.Random.nextLong().toString().replace('-', 'n')}" }; val scope = rememberCoroutineScope()
    TextButton(onClick = { open = true }) { Text("Report or block account") }
    if (open) CommunityDialog("Safety controls", { open = false }) {
        Text("Blocking immediately hides your public content from each other without notifying them. For a shared trip, leave or ask the owner/support to remove the other member.")
        Text("In immediate danger, contact local emergency services. TripTandem does not provide emergency response.")
        CommunityChoice("Category", category, reportCategories) { category = it }
        CommunityField("Optional report details", detail, 2000) { detail = it }
        if (message.isNotEmpty()) Text(message, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        fun submit(op: String, input: Map<String, String>) {
            busy = true
            scope.launch {
                when (val result = repository.execute(op, input)) {
                    is DataResult.Success -> {
                        val parsed = runCatching { Json.parseToJsonElement(result.value).jsonObject }.getOrNull()
                        message = if (op == "report") "Report received. Reference: ${parsed?.text("reference")}. Our team will review it." else "Account blocked. Shared trip access remains until you leave or the owner removes a member."
                    }
                    is DataResult.Failure -> message = "Unable to complete this action. Reconnect and try again."
                }; busy = false
            }
        }
        Button(onClick = { submit("report", mapOf("subjectType" to "user", "subjectId" to userId, "category" to category, "detail" to detail, "idempotencyKey" to key)) }, enabled = !busy) { Text("Submit report") }
        OutlinedButton(onClick = { submit("block", mapOf("userId" to userId)) }, enabled = !busy) { Text("Block account") }
    }
}
