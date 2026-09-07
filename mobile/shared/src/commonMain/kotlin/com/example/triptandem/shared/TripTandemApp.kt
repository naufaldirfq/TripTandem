package com.triptandem.shared

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import org.jetbrains.compose.resources.painterResource
import triptandem.shared.generated.resources.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.jetbrains.compose.resources.Font
import triptandem.shared.generated.resources.Res
import triptandem.shared.generated.resources.cabinet_grotesk_bold
import triptandem.shared.generated.resources.cabinet_grotesk_extra_bold
import triptandem.shared.generated.resources.satoshi_bold
import triptandem.shared.generated.resources.satoshi_medium
import triptandem.shared.generated.resources.satoshi_regular

private val Cream = Color(0xFFFBF8F3)
private val CreamMuted = Color(0xFFF5F0E8)
private val CreamBorder = Color(0xFFEDE6DA)
private val Coral = Color(0xFFB84628)
private val CoralHero = Color(0xFFE8704A)
private val CoralTint = Color(0xFFFCE8E0)
private val Sage = Color(0xFF446B55)
private val SageAccent = Color(0xFF6B9E7F)
private val SageTint = Color(0xFFE4EDE6)
private val Ink = Color(0xFF1A1816)
private val InkMuted = Color(0xFF6E665E)
private val InkSoft = Color(0xFF3D3832)
private val ErrorRed = Color(0xFFB3261E)
private val Danger = Color(0xFFE5242A)
private val DangerTint = Color(0xFFFFF3F3)
private val DangerBorder = Color(0xFFFFC7C9)

// The Functions worker allows up to 90 seconds for the provider request and
// its final allowance transaction. Keep polling long enough to observe that
// terminal state instead of reporting a client-side timeout first.
private const val GENERATION_MAX_POLL_ATTEMPTS = 120

private val TripTandemColors = androidx.compose.material3.lightColorScheme(
    primary = Coral,
    onPrimary = Color.White,
    primaryContainer = CoralTint,
    onPrimaryContainer = Color(0xFF5C2416),
    secondary = Sage,
    onSecondary = Color.White,
    secondaryContainer = SageTint,
    onSecondaryContainer = Color(0xFF203B2B),
    background = Cream,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = CreamMuted,
    onSurfaceVariant = InkSoft,
    outline = Color(0xFFCFC5B7),
    error = ErrorRed,
    onError = Color.White,
)

@Composable
private fun tripTandemTypography(): androidx.compose.material3.Typography {
    val cabinetBold = FontFamily(Font(Res.font.cabinet_grotesk_bold, FontWeight.Bold))
    val cabinetExtraBold = FontFamily(Font(Res.font.cabinet_grotesk_extra_bold, FontWeight.ExtraBold))
    val satoshi = FontFamily(
        Font(Res.font.satoshi_regular, FontWeight.Normal),
        Font(Res.font.satoshi_medium, FontWeight.Medium),
        Font(Res.font.satoshi_bold, FontWeight.Bold),
    )
    return createTripTandemTypography(cabinetBold, cabinetExtraBold, satoshi)
}

fun createTripTandemTypography(
    cabinetBold: FontFamily,
    cabinetExtraBold: FontFamily,
    satoshi: FontFamily,
): androidx.compose.material3.Typography = androidx.compose.material3.Typography(
    displayLarge = TextStyle(fontFamily = cabinetExtraBold, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, lineHeight = 38.sp),
    headlineSmall = TextStyle(fontFamily = cabinetExtraBold, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontFamily = cabinetBold, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = cabinetBold, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontFamily = satoshi, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = satoshi, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = satoshi, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = satoshi, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontFamily = satoshi, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontFamily = satoshi, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)

/**
 * Shared Phase 1 application. Android and the iOS host inject Firebase
 * repositories; previews can use the deterministic local store. The open-trip
 * flags remain fail-closed in both cases.
 */
@Composable
fun TripTandemApp(
    typography: androidx.compose.material3.Typography? = null,
    analytics: TripTandemAnalytics = NoOpTripTandemAnalytics,
    featureFlags: TripTandemFeatureFlags = TripTandemFeatureFlags.SafeDefaults,
    repositories: TripTandemRepositories? = null,
    onShareInvite: ((String) -> Unit)? = null,
    pendingInvite: PendingInvite? = null,
    onSignOut: (() -> Unit)? = null,
    revenueCat: RevenueCatCoordinator? = null,
    onOpenExternalUrl: ((String) -> Unit)? = null,
    onManageSubscription: (() -> Unit)? = null,
    connectivity: ConnectivityMonitor = AlwaysOnlineConnectivityMonitor,
    onShareExport: ((String) -> Unit)? = null,
) {
    val resolvedTypography = typography ?: tripTandemTypography()
    val data = remember(repositories) { repositories ?: TripTandemRepositories.local() }
    val isOnline by connectivity.isOnline.collectAsState()

    DisposableEffect(connectivity) {
        onDispose { connectivity.close() }
    }

    MaterialTheme(colorScheme = TripTandemColors, typography = resolvedTypography) {
        var route by remember { mutableStateOf(AppRoute.Welcome) }
        var restoringSession by remember(data) { mutableStateOf(true) }
        var startupError by remember(data) { mutableStateOf<TripTandemError?>(null) }
        var startupAttempt by remember { mutableIntStateOf(0) }
        var startWithSignUp by remember { mutableStateOf(false) }
        var profile by remember { mutableStateOf<TravelerProfile?>(null) }
        var embeddedProfileEditing by remember { mutableStateOf(false) }
        var selectedTrip by remember { mutableStateOf<TripRecord?>(null) }
        var proStatus by remember { mutableStateOf<OrganizerProStatus?>(null) }
        var paywallTrigger by remember { mutableStateOf<String?>(null) }

        fun openPaywall(trigger: String) {
            paywallTrigger = trigger
        }

        LaunchedEffect(revenueCat) {
            // Auth transitions can replace or remove the RevenueCat
            // coordinator. Clear the previous account's entitlement before
            // refreshing the new identity so Pro-gated actions never inherit
            // stale state during the handoff.
            val previous = proStatus
            proStatus = null
            if (revenueCat != null) {
                when (val result = revenueCat.refreshEntitlement()) {
                    is DataResult.Success -> {
                        proStatus = result.value
                        if (previous?.isActive != result.value.isActive) {
                            analytics.logEvent(
                                TripTandemAnalytics.Events.ENTITLEMENT_CHANGED,
                                mapOf(
                                    "from" to (previous?.let { if (it.isActive) "pro" else "free" } ?: "unknown"),
                                    "to" to if (result.value.isActive) "pro" else "free",
                                    "source" to "refresh",
                                ),
                            )
                        }
                    }
                    is DataResult.Failure -> Unit
                }
            }
        }

        LaunchedEffect(data, startupAttempt) {
            startupError = null
            restoringSession = true
            // Native Firebase bootstraps the anonymous session just before
            // Compose starts. A short bounded retry keeps a cold start from
            // getting stuck on Welcome while Auth is still restoring.
            for (attempt in 0 until 8) {
                when (val result = data.profile.getCurrentProfile()) {
                    is DataResult.Success -> {
                        profile = result.value
                        // Session restoration must never take control back from a
                        // screen the user opened while the native auth state was
                        // settling. This also keeps a late cold-start callback
                        // from flashing Welcome or replacing the profile editor.
                        if (route == AppRoute.Welcome && result.value != null && pendingInvite == null) route = AppRoute.Home
                        else if (route == AppRoute.Welcome && (data.identity.currentSession() as? DataResult.Success)?.value?.isAnonymous == false) route = AppRoute.Auth
                        break
                    }
                    is DataResult.Failure -> {
                        if (result.error != TripTandemError.Unauthenticated || attempt == 7) { startupError = result.error; break }
                        delay(250)
                    }
                }
            }
            restoringSession = false
            analytics.logScreen(route.screenName)
        }

        LaunchedEffect(pendingInvite) {
            if (pendingInvite != null) {
                route = AppRoute.Invite
                analytics.logScreen(route.screenName)
            }
        }

        if (restoringSession || startupError != null) {
            Box(Modifier.fillMaxSize().background(Cream), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    TripTandemMark()
                    if (startupError != null) ErrorBanner(startupError!!) { startupAttempt++ }
                    else CircularProgressIndicator(color = Coral, modifier = Modifier.size(24.dp))
                }
            }
        } else when (route) {
            AppRoute.Welcome -> WelcomeScreen(
                onGetStarted = {
                    analytics.logEvent(TripTandemAnalytics.Events.SIGN_UP_STARTED, mapOf("method" to "anonymous"))
                    startWithSignUp = true
                    route = AppRoute.Auth
                    analytics.logScreen(route.screenName)
                },
                onContinue = {
                    startWithSignUp = false
                    route = AppRoute.Auth
                    analytics.logScreen(route.screenName)
                },
            )
            AppRoute.ProfileSetup, AppRoute.Settings -> androidx.compose.runtime.key(route) { ProfileSetupScreen(
                settingsMode = route == AppRoute.Settings,
                onEditProfile = { route = AppRoute.ProfileSetup },
                initialProfile = profile,
                repositories = data,
                identity = data.identity,
                analytics = analytics,
                proStatus = proStatus,
                onSaved = { saved ->
                    profile = saved
                    route = AppRoute.Settings
                    analytics.logScreen(route.screenName)
                },
                onUpgrade = if (profile != null) {
                    { openPaywall("settings") }
                } else {
                    null
                },
                onBack = { route = if (route == AppRoute.ProfileSetup) AppRoute.Settings else AppRoute.Home },
                onDeleted = {
                    profile = null
                    route = AppRoute.Welcome
                    analytics.logScreen(route.screenName)
                },
                onSignOut = {
                    profile = null
                    route = AppRoute.Welcome
                    onSignOut?.invoke()
                    analytics.logScreen(route.screenName)
                },
            )
            }
            AppRoute.Auth -> AuthScreen(
                initiallyCreateAccount = startWithSignUp,
                repositories = data,
                analytics = analytics,
                onBack = { route = AppRoute.Welcome },
                onAuthenticated = { savedProfile ->
                    profile = savedProfile
                    route = AppRoute.Home
                    analytics.logScreen(route.screenName)
                },
            )
            AppRoute.Home -> HomeScreen(
                profile = profile,
                canCreateTrip = profile?.accountStatus != AccountStatus.Suspended,
                proStatus = proStatus,
                repositories = data,
                featureFlags = featureFlags,
                analytics = analytics,
                onUpgrade = { openPaywall("second_active_trip") },
                onCreateTrip = {
                    analytics.logEvent(TripTandemAnalytics.Events.TRIP_CREATE_STARTED, mapOf("source" to "trips"))
                    route = AppRoute.CreateTrip
                    analytics.logScreen(route.screenName)
                },
                onTripSelected = { trip ->
                    selectedTrip = trip
                    route = AppRoute.Itinerary
                    analytics.logScreen(route.screenName)
                },
                onProfile = { route = AppRoute.Settings },
                profileContent = {
                    if (embeddedProfileEditing) {
                        DisposableEffect(Unit) {
                            onDispose { embeddedProfileEditing = false }
                        }
                        ProfileSetupScreen(
                            settingsMode = false,
                            embedded = true,
                            initialProfile = profile,
                            repositories = data,
                            identity = data.identity,
                            analytics = analytics,
                            proStatus = proStatus,
                            onUpgrade = { openPaywall("settings") },
                            onSaved = { profile = it; embeddedProfileEditing = false },
                            onBack = { embeddedProfileEditing = false },
                            onDeleted = { embeddedProfileEditing = false; profile = null; route = AppRoute.Welcome },
                            onSignOut = { embeddedProfileEditing = false; profile = null; route = AppRoute.Welcome; onSignOut?.invoke() },
                        )
                    } else {
                        ProfileSetupScreen(
                            settingsMode = true,
                            embedded = true,
                            onEditProfile = { embeddedProfileEditing = true },
                            initialProfile = profile,
                            repositories = data,
                            identity = data.identity,
                            analytics = analytics,
                            proStatus = proStatus,
                            onUpgrade = { openPaywall("settings") },
                            onSaved = { profile = it },
                            onBack = {},
                            onDeleted = { profile = null; route = AppRoute.Welcome },
                            onSignOut = { profile = null; route = AppRoute.Welcome; onSignOut?.invoke() },
                        )
                    }
                },
            )
            AppRoute.CreateTrip -> CreateTripScreen(
                repositories = data,
                drafts = data.tripDrafts,
                analytics = analytics,
                proStatus = proStatus,
                onUpgrade = { openPaywall("member_capacity") },
                onBack = { route = AppRoute.Home },
                onCreated = { trip ->
                    selectedTrip = trip
                    route = AppRoute.Itinerary
                    analytics.logScreen(route.screenName)
                },
            )
            AppRoute.Itinerary -> selectedTrip?.let { trip ->
                ItineraryScreen(
                    trip = trip,
                    repositories = data,
                    analytics = analytics,
                    featureFlags = featureFlags,
                    isOnline = isOnline,
                    revenueCat = revenueCat,
                    proStatus = proStatus,
                    onUpgrade = { openPaywall("ai_generation") },
                    onBack = { route = AppRoute.Home },
                    onInviteMembers = {
                        route = AppRoute.Members
                        analytics.logScreen(route.screenName)
                    },
                    onShareInvite = onShareInvite,
                    onShareExport = onShareExport,
                    onTripUpdated = { updated -> selectedTrip = updated },
                    onTripDeleted = {
                        selectedTrip = null
                        route = AppRoute.Home
                        analytics.logScreen(route.screenName)
                    },
                )
            } ?: run { route = AppRoute.Home }
            AppRoute.Members -> selectedTrip?.let { trip ->
                MembersScreen(
                    trip = trip,
                    repositories = data,
                    analytics = analytics,
                    onBack = { route = AppRoute.Itinerary },
                    onShareInvite = onShareInvite,
                )
            } ?: run { route = AppRoute.Home }
            AppRoute.Invite -> pendingInvite?.let { invite ->
                InviteLandingScreen(
                    pendingInvite = invite,
                    repositories = data,
                    analytics = analytics,
                    onAccepted = { trip ->
                        selectedTrip = trip
                        route = AppRoute.Itinerary
                        analytics.logScreen(route.screenName)
                    },
                    onDecline = {
                        route = if (profile == null) AppRoute.Welcome else AppRoute.Home
                        analytics.logScreen(route.screenName)
                    },
                )
            } ?: run { route = AppRoute.Home }
        }

        paywallTrigger?.let { trigger ->
            OrganizerProPaywallDialog(
                coordinator = revenueCat,
                trigger = trigger,
                entitlementState = if (proStatus?.isActive == true) "pro" else "free",
                analytics = analytics,
                onStatusChanged = { updated ->
                    val previous = proStatus
                    proStatus = updated
                    if (previous?.isActive != updated.isActive) {
                        analytics.logEvent(
                            TripTandemAnalytics.Events.ENTITLEMENT_CHANGED,
                            mapOf(
                                "from" to (previous?.let { if (it.isActive) "pro" else "free" } ?: "unknown"),
                                "to" to if (updated.isActive) "pro" else "free",
                                "source" to "purchase_or_restore",
                            ),
                        )
                    }
                },
                onDismiss = { paywallTrigger = null },
                onOpenExternalUrl = onOpenExternalUrl,
                onManageSubscription = onManageSubscription,
            )
        }
    }
}

private enum class AppRoute(val screenName: String) {
    Welcome("welcome"),
    Auth("auth"),
    ProfileSetup("profile_setup"),
    Settings("profile_setup"),
    Home("trips"),
    CreateTrip("create_trip"),
    Itinerary("itinerary"),
    Members("members"),
    Invite("invite"),
}

@Composable
private fun WelcomeScreen(onGetStarted: () -> Unit, onContinue: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Ink)) {
    val heroHeight = (maxHeight * .61f).coerceAtLeast(410.dp)
    val footerHeight = maxHeight - heroHeight
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.height(heroHeight).fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFF2A2420), Color(0xFF3D322A), Ink)))) {
            Canvas(Modifier.fillMaxSize()) {
                val route = Path().apply {
                    moveTo(size.width * .12f, size.height * .78f)
                    cubicTo(size.width * .28f, size.height * .55f, size.width * .42f, size.height * .50f, size.width * .62f, size.height * .34f)
                    cubicTo(size.width * .75f, size.height * .25f, size.width * .86f, size.height * .20f, size.width * .92f, size.height * .10f)
                }
                drawPath(route, Color.White.copy(alpha = .26f), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 7.dp.toPx()))))
                drawPath(route, CoralHero.copy(alpha = .15f), style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round))
                drawCircle(CoralHero, 5.dp.toPx(), Offset(size.width * .12f, size.height * .78f))
                drawCircle(Color(0xFFF5F0E8).copy(alpha = .7f), 4.dp.toPx(), Offset(size.width * .46f, size.height * .47f))
                drawCircle(Color(0xFFF5F0E8).copy(alpha = .7f), 4.dp.toPx(), Offset(size.width * .72f, size.height * .29f))
                drawCircle(SageAccent, 5.dp.toPx(), Offset(size.width * .92f, size.height * .10f))
            }
            // Keep the route marker below the copy; on compact heights the
            // previous bottom inset placed it over the supporting sentence.
            FloatingTraveler(initial = "A", modifier = Modifier.align(Alignment.BottomStart).padding(start = 32.dp, bottom = heroHeight * .22f), ring = CoralHero)
            // Keep the decorative traveler above the copy block. The previous
            // center/+40dp placement could cover the end of the hero eyebrow
            // on compact phone heights, making the welcome message look
            // truncated even though the text itself was complete.
            FloatingTraveler(initial = "J", modifier = Modifier.align(Alignment.Center).offset(x = (-6).dp, y = (-65).dp), ring = Cream)
            FloatingTraveler(initial = "M", modifier = Modifier.align(Alignment.TopEnd).padding(end = 58.dp, top = 112.dp), ring = SageAccent)
            Box(Modifier.align(Alignment.CenterEnd).padding(end = 92.dp, top = 30.dp).size(32.dp).background(CoralHero, CircleShape), contentAlignment = Alignment.Center) {
                TripIcon(TripIconKind.MapPin, contentDescription = "Destination", tint = Color.White, modifier = Modifier.size(18.dp))
            }
            // Render the copy after the decorative route markers so every
            // message remains legible when the layout is compressed.
            Column(
                Modifier.fillMaxSize().statusBarsPadding().padding(start = 24.dp, top = 22.dp, end = 24.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TripTandemMark()
                    Text("TripTandem", color = Color.White, style = MaterialTheme.typography.titleLarge)
                }
                Column(
                    modifier = Modifier.padding(bottom = 0.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "TRAVEL BETTER, TOGETHER",
                        color = CoralHero,
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 1.7.sp),
                    )
                    Text(
                        buildAnnotatedString { append("Plan the trip.\nFind your people.\n"); withStyle(SpanStyle(color = Color(0xFFF08A68))) { append("Go together.") } },
                        color = Cream,
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-0.35).sp),
                    )
                    Text(
                        "Turn a trip idea into a shared plan—and invite companions who actually fit.",
                        color = Cream.copy(alpha = .78f),
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp),
                    )
                }
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(min = footerHeight),
            color = Cream,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            Column(
                Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 34.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(bottom = 12.dp),
                ) {
                    AvatarStack()
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Ink)) { append("2–8 friends") }
                            append(" per trip—one calm plan for everyone.")
                        },
                        color = InkSoft,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
                    )
                }
                Button(
                    onClick = onGetStarted,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CoralHero),
                ) {
                    Text("Create a free account", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, color = Color.White))
                    Spacer(Modifier.width(8.dp))
                    TripIcon(TripIconKind.ArrowRight, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                OutlinedButton(
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, CreamBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink, containerColor = Color.White),
                ) { Text("Log in", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, color = Ink)) }
                Text(
                    "Safety tools stay free. No booking fees. Just better group travel.",
                    color = InkMuted.copy(alpha = .72f),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
    }
}

@Composable
private fun AuthScreen(
    initiallyCreateAccount: Boolean = false,
    repositories: TripTandemRepositories,
    analytics: TripTandemAnalytics,
    onBack: () -> Unit,
    onAuthenticated: (TravelerProfile?) -> Unit,
) {
    var createAccount by rememberSaveable { mutableStateOf(initiallyCreateAccount) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<TripTandemError?>(null) }
    var currentSession by remember { mutableStateOf<AuthSession?>(null) }
    var needsBasics by remember { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var confirmed by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    suspend fun finishAuthentication() {
        when (val result = repositories.profile.getCurrentProfile()) {
            is DataResult.Success -> if (result.value != null) onAuthenticated(result.value) else needsBasics = true
            is DataResult.Failure -> error = result.error
        }
    }


    LaunchedEffect(repositories.identity) {
        currentSession = when (val result = repositories.identity.currentSession()) {
            is DataResult.Success -> result.value
            is DataResult.Failure -> null
        }
        if (currentSession?.isAnonymous == false) {
            when (val result = repositories.profile.getCurrentProfile()) {
                is DataResult.Success -> if (result.value == null) needsBasics = true
                is DataResult.Failure -> error = result.error
            }
        }
    }

    Scaffold(containerColor = Cream) { innerPadding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { TextButton(onClick = onBack) { Text("Back", color = Coral) } }
            if (needsBasics) {
                item {
                    Text("One last thing", style = MaterialTheme.typography.headlineSmall)
                    Text("Choose the name your friends will see. You can add travel preferences later in your profile.", color = InkMuted)
                }
                item { TripTextField(name, { name = it.take(40) }, label = { Text("Display name") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(confirmed, { confirmed = it })
                        Text("I meet the minimum age requirement and accept the Terms and Privacy Policy.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                error?.let { failure -> item { ErrorBanner(failure) { error = null } } }
                item {
                    Button(onClick = {
                        loading = true
                        scope.launch {
                            when (val result = repositories.profile.saveCurrentProfile(SaveTravelerProfileInput(
                                displayName = name.trim(), homeRegion = "Prefer not to say", primaryLanguage = "English",
                                ageConfirmed = confirmed, termsVersion = PolicyVersions.TERMS, privacyVersion = PolicyVersions.PRIVACY,
                            ))) {
                                is DataResult.Success -> onAuthenticated(result.value)
                                is DataResult.Failure -> error = result.error
                            }
                            loading = false
                        }
                    }, enabled = !loading && confirmed && name.trim().length in 2..40, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) { Text(if (loading) "Saving…" else "Start planning") }
                }
            } else {
            item {
                TripTandemMark()
                Spacer(Modifier.height(8.dp))
                Text(if (createAccount) "Start planning with friends." else "Welcome back, traveler", style = MaterialTheme.typography.headlineSmall)
                Text(
                    if (createAccount) "Create a free account to build your first trip and invite your people."
                    else "Sign in to recover the trips and profile you already created.",
                    color = InkMuted,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            item {
                TripTextField(
                    value = email,
                    onValueChange = { if (it.length <= 160) email = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Email") },
                    singleLine = true,
                )
            }
            item {
                TripTextField(
                    value = password,
                    onValueChange = { if (it.length <= 128) password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Password") },
                    supportingText = { Text("At least 6 characters. Never share it in an invite.") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
            }
            error?.let { failure -> item { ErrorBanner(failure, onRetry = { error = null }) } }
            item {
                Button(
                    onClick = {
                        loading = true
                        error = null
                        analytics.logEvent(TripTandemAnalytics.Events.SIGN_UP_STARTED, mapOf("method" to "email"))
                        scope.launch {
                            val result = if (createAccount) {
                                repositories.identity.createOrLinkEmail(email.trim(), password)
                            } else {
                                repositories.identity.signInWithEmail(email.trim(), password)
                            }
                            when (result) {
                                is DataResult.Success -> {
                                    currentSession = result.value
                                    analytics.logEvent(TripTandemAnalytics.Events.SIGN_UP_COMPLETED, mapOf("method" to "email"))
                                    finishAuthentication()
                                }
                                is DataResult.Failure -> error = result.error
                            }
                            loading = false
                        }
                    },
                    enabled = !loading && email.contains("@") && password.length >= 6,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Coral),
                ) {
                    if (loading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text(if (createAccount) "Create account" else "Sign in")
                }
            }
            item {
                OutlinedButton(
                    onClick = {
                        loading = true
                        error = null
                        analytics.logEvent(TripTandemAnalytics.Events.SIGN_UP_STARTED, mapOf("method" to "google"))
                        scope.launch {
                            when (val result = repositories.identity.signInWithGoogle()) {
                                is DataResult.Success -> {
                                    currentSession = result.value
                                    analytics.logEvent(TripTandemAnalytics.Events.SIGN_UP_COMPLETED, mapOf("method" to "google"))
                                    finishAuthentication()
                                }
                                is DataResult.Failure -> error = result.error
                            }
                            loading = false
                        }
                    },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, CreamBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink),
                ) {
                    Image(painterResource(Res.drawable.provider_google), contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Continue with Google")
                }
            }
            if (supportsAppleSignIn()) item {
                Button(
                    onClick = {
                        loading = true
                        error = null
                        analytics.logEvent(TripTandemAnalytics.Events.SIGN_UP_STARTED, mapOf("method" to "apple"))
                        scope.launch {
                            when (val result = repositories.identity.signInWithApple()) {
                                is DataResult.Success -> {
                                    currentSession = result.value
                                    analytics.logEvent(TripTandemAnalytics.Events.SIGN_UP_COMPLETED, mapOf("method" to "apple"))
                                    finishAuthentication()
                                }
                                is DataResult.Failure -> error = result.error
                            }
                            loading = false
                        }
                    },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Ink),
                ) {
                    Image(painterResource(Res.drawable.provider_apple), contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Continue with Apple", color = Color.White)
                }
            }
            item {
                OutlinedButton(
                    onClick = { createAccount = !createAccount; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, CreamBorder),
                ) { Text(if (createAccount) "I already have an account" else "Create a new account") }
            }
            item { Text("Your trips are private by default. Safety tools stay free—always.", color = InkMuted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center) }
            }
        }
    }
}

@Composable
private fun ProfileSetupScreen(
    settingsMode: Boolean = false,
    embedded: Boolean = false,
    onEditProfile: () -> Unit = {},
    initialProfile: TravelerProfile?,
    repositories: TripTandemRepositories,
    identity: IdentityRepository,
    analytics: TripTandemAnalytics,
    proStatus: OrganizerProStatus?,
    onUpgrade: (() -> Unit)?,
    onSaved: (TravelerProfile) -> Unit,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    onSignOut: () -> Unit,
) {
    var displayName by remember(initialProfile) { mutableStateOf(initialProfile?.displayName.orEmpty()) }
    var homeRegion by remember(initialProfile) { mutableStateOf(initialProfile?.homeRegion.orEmpty()) }
    var language by remember(initialProfile) { mutableStateOf(initialProfile?.primaryLanguage ?: "English") }
    var ageConfirmed by remember(initialProfile) { mutableStateOf(initialProfile?.ageConfirmed == true) }
    var bio by remember(initialProfile) { mutableStateOf(initialProfile?.bio.orEmpty()) }
    var pace by remember(initialProfile) { mutableStateOf(initialProfile?.pace) }
    var budgetBand by remember(initialProfile) { mutableStateOf(initialProfile?.budgetBand) }
    var visibility by remember(initialProfile) { mutableStateOf(initialProfile?.visibility ?: ProfileVisibility.Private) }
    var additionalLanguages by remember(initialProfile) { mutableStateOf(initialProfile?.additionalLanguages.orEmpty()) }
    var interests by remember(initialProfile) { mutableStateOf(initialProfile?.interests.orEmpty()) }
    var policyAccepted by remember(initialProfile) { mutableStateOf(initialProfile != null && initialProfile.consentedAtEpochMillis != null) }
    var showSignOut by remember { mutableStateOf(false) }
    var showPreview by remember { mutableStateOf(false) }
    var showDeleteAccount by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var deletingAccount by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<TripTandemError?>(null) }
    var requiresReauthentication by remember { mutableStateOf(false) }
    var reauthEmail by rememberSaveable { mutableStateOf("") }
    var reauthPassword by rememberSaveable { mutableStateOf("") }
    var session by remember { mutableStateOf<AuthSession?>(null) }
    var accountEmail by rememberSaveable { mutableStateOf("") }
    var accountPassword by rememberSaveable { mutableStateOf("") }
    var linkingAccount by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<TripTandemError?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun confirmDeleteAccount() {
        deletingAccount = true
        deleteError = null
        if (requiresReauthentication) {
            when (val signedIn = identity.signInWithEmail(reauthEmail.trim(), reauthPassword)) {
                is DataResult.Success -> {
                    if (session?.uid != null && signedIn.value.uid != session?.uid) {
                        deleteError = TripTandemError.Validation("credentials")
                        deletingAccount = false
                        return
                    }
                    session = signedIn.value
                    reauthPassword = ""
                }
                is DataResult.Failure -> {
                    deleteError = signedIn.error
                    deletingAccount = false
                    return
                }
            }
        }
        when (val result = repositories.profile.deleteCurrentAccount()) {
            is DataResult.Success -> {
                analytics.logEvent(TripTandemAnalytics.Events.ACCOUNT_DELETION_COMPLETED)
                showDeleteAccount = false
                onDeleted()
            }
            is DataResult.Failure -> {
                deleteError = result.error
                if (result.error == TripTandemError.ReauthenticationRequired && session?.email != null) {
                    requiresReauthentication = true
                    if (reauthEmail.isBlank()) reauthEmail = session?.email.orEmpty()
                }
            }
        }
        deletingAccount = false
    }

    LaunchedEffect(identity) {
        session = when (val result = identity.currentSession()) {
            is DataResult.Success -> result.value
            is DataResult.Failure -> null
        }
    }

    Scaffold(containerColor = Cream, bottomBar = {
        if (settingsMode && !embedded) NavigationBar(containerColor = Color.White) {
            listOf("Trips", "Discover", "Activity", "Profile").forEachIndexed { index, label ->
                NavigationBarItem(selected = index == 3, onClick = { if (index != 3) onBack() }, icon = { TripIcon(tabIcon(index), label) }, label = { Text(label) }, colors = NavigationBarItemDefaults.colors(indicatorColor = CoralTint, selectedIconColor = Coral, selectedTextColor = Coral))
            }
        }
    }) { innerPadding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    if (!settingsMode) TextButton(onClick = onBack) { Text("Back", color = Coral) }
                    Spacer(Modifier.width(4.dp))
                    Text(if (settingsMode) "Account settings" else "Your traveler profile", style = MaterialTheme.typography.titleLarge)
                }
            }
            if (settingsMode) {
                item {
                    DesignCard {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Box(Modifier.size(60.dp).background(CoralTint, CircleShape), contentAlignment = Alignment.Center) {
                                Text(initials(displayName), color = Coral, style = MaterialTheme.typography.titleLarge)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(displayName.ifBlank { "Traveler" }, style = MaterialTheme.typography.titleLarge)
                                Text(bio.ifBlank { "Your next adventure starts here." }, color = InkMuted, style = MaterialTheme.typography.bodySmall)
                                if (homeRegion.isNotBlank()) Text(homeRegion, color = InkMuted, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        OutlinedButton(onClick = onEditProfile, modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, CreamBorder)) { Text("Edit profile") }
                    }
                }
                item {
                    Text("Account", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    DesignCard {
                        Text("Email", style = MaterialTheme.typography.labelLarge)
                        Text(session?.email ?: "Temporary account", color = InkMuted)
                    }
                }
            }
            if (!settingsMode) {
            item {
                Spacer(Modifier.height(4.dp))
                Text("Tell us how you travel", style = MaterialTheme.typography.headlineSmall)
                Text("This helps your companions plan with you. You can change it anytime.", color = InkMuted)
            }
            item {
                TripTextField(
                    value = displayName,
                    onValueChange = { if (it.length <= 40) displayName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Display name") },
                    supportingText = { Text("2–40 characters. This is the name members see.") },
                    singleLine = true,
                )
            }
            item {
                RegionSelector(homeRegion) { homeRegion = it }
            }
            if (!ageConfirmed || !policyAccepted) item {
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(checked = ageConfirmed, onCheckedChange = { ageConfirmed = it })
                    Text("I confirm that I meet the minimum age requirement.", color = InkSoft, modifier = Modifier.padding(top = 12.dp))
                }
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(checked = policyAccepted, onCheckedChange = { policyAccepted = it })
                    Text("I accept the Terms (${PolicyVersions.TERMS}) and Privacy Policy (${PolicyVersions.PRIVACY}).", color = InkSoft, modifier = Modifier.padding(top = 12.dp))
                }
            }
            item {
                Text("Your travel style", style = MaterialTheme.typography.titleMedium)
                Text("These details help companions understand your travel style. You control who can see them.", color = InkMuted, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                TripTextField(
                    value = bio,
                    onValueChange = { if (it.length <= 240) bio = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Short bio") },
                    placeholder = { Text("A sentence about how you like to travel") },
                    supportingText = { Text("Up to 240 characters") },
                    minLines = 2,
                    maxLines = 4,
                )
                Spacer(Modifier.height(8.dp))
                Text("Travel pace", style = MaterialTheme.typography.labelLarge)
                PaceSelector(pace) { pace = it }
                Spacer(Modifier.height(8.dp))
                Text("Budget band", style = MaterialTheme.typography.labelLarge)
                BudgetSelector(budgetBand) { budgetBand = it }
                Spacer(Modifier.height(8.dp))
                Text("Interests", style = MaterialTheme.typography.labelLarge)
                Text("Choose a few signals for future companion matching.", color = InkMuted, style = MaterialTheme.typography.bodySmall)
                TripInterestVocabulary.values.chunked(3).forEach { row ->
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                        row.forEach { option ->
                            ChoiceChip(
                                label = option,
                                selected = option in interests,
                                onClick = {
                                    interests = if (option in interests) interests - option
                                    else (interests + option).distinct().take(12)
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Profile visibility", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    ChoiceChip("Private", visibility == ProfileVisibility.Private, { visibility = ProfileVisibility.Private })
                    ChoiceChip("Connections", visibility == ProfileVisibility.Connections, { visibility = ProfileVisibility.Connections })
                }
                Text("Private is the safest default. Connections can see your approved profile summary.", color = InkMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                OutlinedButton(
                    onClick = { showPreview = true; analytics.logEvent(TripTandemAnalytics.Events.PROFILE_PREVIEW_OPENED) },
                    modifier = Modifier.padding(top = 8.dp),
                    border = BorderStroke(1.dp, CreamBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Coral),
                ) { Text("Preview what others see") }
            }
            item {
                Text("Primary language", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("English", "Bahasa Indonesia", "日本語").forEach { option ->
                        ChoiceChip(label = option, selected = language == option, onClick = { language = option })
                    }
                }
            }
            item {
                Text("Additional languages", style = MaterialTheme.typography.titleMedium)
                Text("Optional. Select up to five languages you can plan in.", color = InkMuted, style = MaterialTheme.typography.bodySmall)
                listOf("Bahasa Indonesia", "日本語", "한국어", "Español").chunked(2).forEach { row ->
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                        row.forEach { option ->
                            ChoiceChip(
                                label = option,
                                selected = option in additionalLanguages,
                                onClick = {
                                    additionalLanguages = if (option in additionalLanguages) additionalLanguages - option
                                    else (additionalLanguages + option).distinct().take(5)
                                },
                            )
                        }
                    }
                }
            }
            }
            if (settingsMode && session?.isAnonymous == true) {
                item {
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = SageTint)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Secure your account", color = Sage, style = MaterialTheme.typography.titleMedium)
                            Text("Add an email and password so you can recover this profile and its trips on another device.", color = Color(0xFF203B2B), style = MaterialTheme.typography.bodySmall)
                            TripTextField(accountEmail, { if (it.length <= 160) accountEmail = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Email") }, singleLine = true)
                            TripTextField(accountPassword, { if (it.length <= 128) accountPassword = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                            Button(
                                onClick = {
                                    linkingAccount = true
                                    scope.launch {
                                        when (val result = identity.createOrLinkEmail(accountEmail.trim(), accountPassword)) {
                                            is DataResult.Success -> { session = result.value; accountPassword = "" }
                                            is DataResult.Failure -> error = result.error
                                        }
                                        linkingAccount = false
                                    }
                                },
                                enabled = !linkingAccount && accountEmail.contains("@") && accountPassword.length >= 6,
                                colors = ButtonDefaults.buttonColors(containerColor = Sage),
                            ) { if (linkingAccount) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Save recovery login") }
                        }
                    }
                }
            } else if (session?.email != null) {
                item { Text("Account secured with ${session?.email}", color = Sage, style = MaterialTheme.typography.bodySmall) }
            }
            if (settingsMode && initialProfile != null && onUpgrade != null) {
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = if (proStatus?.isActive == true) SageTint else CoralTint),
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                if (proStatus?.isActive == true) "Organizer Pro is active" else "Plan more with Organizer Pro",
                                color = if (proStatus?.isActive == true) Sage else Coral,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                if (proStatus?.isActive == true) {
                                    "Your organizer limits and monthly AI allowance are active. Manage or restore your plan anytime."
                                } else {
                                    "Unlock more active trips, up to 12 travelers per trip, and up to 30 AI drafts per month. Joining and safety tools stay free."
                                },
                                color = InkSoft,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            OutlinedButton(
                                onClick = onUpgrade,
                                border = BorderStroke(1.dp, if (proStatus?.isActive == true) Sage else Coral),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = if (proStatus?.isActive == true) Sage else Coral),
                            ) {
                                Text(if (proStatus?.isActive == true) "Manage Organizer Pro" else "Upgrade to Pro")
                            }
                        }
                    }
                }
            }
            error?.let { failure ->
                item { ErrorBanner(failure, onRetry = { error = null }) }
            }
            if (!settingsMode) {
            item {
                Button(
                    onClick = {
                        saving = true
                        error = null
                        scope.launch {
                            when (val result = repositories.profile.saveCurrentProfile(
                                SaveTravelerProfileInput(
                                    displayName = displayName,
                                    homeRegion = homeRegion.ifBlank { null },
                                    primaryLanguage = language,
                                    ageConfirmed = ageConfirmed,
                                    additionalLanguages = additionalLanguages,
                                    interests = interests,
                                    termsVersion = PolicyVersions.TERMS,
                                    privacyVersion = PolicyVersions.PRIVACY,
                                    bio = bio.ifBlank { null },
                                    pace = pace,
                                    budgetBand = budgetBand,
                                    visibility = visibility,
                                ),
                            )) {
                                is DataResult.Success -> {
                                    analytics.logEvent(TripTandemAnalytics.Events.SIGN_UP_COMPLETED, mapOf("method" to "anonymous"))
                                    analytics.logEvent(TripTandemAnalytics.Events.PROFILE_ESSENTIALS_COMPLETED)
                                    if (bio.isNotBlank() || pace != null || budgetBand != null) {
                                        analytics.logEvent(
                                            TripTandemAnalytics.Events.PROFILE_OPTIONAL_COMPLETED,
                                            mapOf("field_count_bucket" to "1_3"),
                                        )
                                    }
                                    onSaved(result.value)
                                }
                                is DataResult.Failure -> error = result.error
                            }
                            saving = false
                        }
                    },
                    enabled = !saving && displayName.trim().length in 2..40 && homeRegion.isNotBlank() && ageConfirmed && policyAccepted && initialProfile?.accountStatus != AccountStatus.Suspended,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Coral),
                ) {
                    if (saving) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text("Save profile")
                }
            }
            }
            if (initialProfile?.accountStatus == AccountStatus.Suspended) {
                item {
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CoralTint)) {
                        Text("This account is suspended. You can contact support or delete your account, but protected trip changes are disabled.", color = Color(0xFF5C2416), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(14.dp))
                    }
                }
            }
            if (settingsMode) {
                item {
                    OfflineStorageCard(
                        repositories = repositories,
                        analytics = analytics,
                        userId = initialProfile?.uid ?: session?.uid,
                    )
                }
                item {
                    SettingsSafetyNote()
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "DANGER ZONE",
                            color = Danger.copy(alpha = .78f),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 2.dp),
                        )
                        Surface(
                            Modifier.fillMaxWidth(),
                            color = Color.White,
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, DangerBorder),
                            shadowElevation = 1.dp,
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    Box(
                                        Modifier.size(48.dp).background(DangerTint, RoundedCornerShape(16.dp)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        TripIcon(TripIconKind.Trash, "Delete account", tint = Danger, modifier = Modifier.size(22.dp))
                                    }
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Text("Delete my account", style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "Permanently delete all profile data. Active shared trips must be transferred or deleted first. This action cannot be undone.",
                                            color = InkMuted,
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                }
                                Button(
                                    onClick = {
                                        showDeleteAccount = true
                                        deleteError = null
                                        requiresReauthentication = false
                                        analytics.logEvent(TripTandemAnalytics.Events.ACCOUNT_DELETION_STARTED)
                                    },
                                    modifier = Modifier.fillMaxWidth().height(52.dp).semantics { contentDescription = "Delete my account" },
                                    shape = RoundedCornerShape(18.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Danger),
                                ) {
                                    TripIcon(TripIconKind.Trash, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(9.dp))
                                    Text("Delete my account", style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                    }
                }
                item {
                    OutlinedButton(
                        onClick = { showSignOut = true },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, CreamBorder),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = Ink),
                    ) {
                        TripIcon(TripIconKind.LogOut, contentDescription = null, tint = Ink, modifier = Modifier.size(21.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Log out", style = MaterialTheme.typography.titleMedium)
                    }
                }
                item {
                    Text(
                        "TripTandem · Phase 1 MVP · v1.0.1",
                        Modifier.fillMaxWidth().padding(bottom = 2.dp),
                        color = InkMuted.copy(alpha = .55f),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }

    if (showSignOut) {
        AlertDialog(
            onDismissRequest = { showSignOut = false },
            containerColor = Cream,
            shape = RoundedCornerShape(28.dp),
            icon = {
                Box(Modifier.size(56.dp).background(CreamMuted, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                    TripIcon(TripIconKind.LogOut, contentDescription = null, tint = Ink, modifier = Modifier.size(25.dp))
                }
            },
            title = { Text("Log out of TripTandem?") },
            text = { Text("Your trips, profile and shared plans will be here when you sign back in.", color = InkMuted) },
            confirmButton = {
                Button(
                    onClick = { showSignOut = false; onSignOut() },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Coral),
                ) { Text("Log out") }
            },
            dismissButton = { TextButton(onClick = { showSignOut = false }) { Text("Stay signed in", color = InkSoft) } },
        )
    }
    if (showPreview) {
        AlertDialog(
            onDismissRequest = { showPreview = false },
            containerColor = Cream,
            title = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StatusChip("PROFILE PREVIEW", SageTint, Sage)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(64.dp).background(CoralTint, CircleShape), contentAlignment = Alignment.Center) { Text(initials(displayName), color = Coral, style = MaterialTheme.typography.headlineSmall) }
                    Text("Meet ${displayName.ifBlank { "a traveler" }}", style = MaterialTheme.typography.headlineSmall)
                }
            } },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("How I travel", style = MaterialTheme.typography.titleMedium)
                    homeRegion.takeIf { it.isNotBlank() }?.let { Text(it, color = InkMuted, style = MaterialTheme.typography.bodySmall) }
                    bio.takeIf { it.isNotBlank() }?.let { Text(it, color = InkSoft, style = MaterialTheme.typography.bodyMedium) }
                    Text("${pace?.label ?: "Pace not set"} · ${budgetBand?.label ?: "Budget not set"}", color = Sage, style = MaterialTheme.typography.bodySmall)
                    Text("Languages: ${(listOf(language) + additionalLanguages).distinct().joinToString()}", color = InkMuted, style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        interests.forEach { StatusChip(it, CreamMuted, InkSoft) }
                    }
                    HorizontalDivider(color = CreamBorder)
                    Text("Only these travel details are shown. Your email and private account details stay hidden.", color = Sage, style = MaterialTheme.typography.bodySmall)
                    Text("Visibility: ${if (visibility == ProfileVisibility.Private) "Private" else "Connections only"}", color = InkMuted, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { showPreview = false }) { Text("Close", color = Coral) } },
        )
    }

    if (showDeleteAccount) {
        AlertDialog(
            onDismissRequest = { if (!deletingAccount) { showDeleteAccount = false; deleteError = null } },
            containerColor = Cream,
            shape = RoundedCornerShape(28.dp),
            icon = {
                Box(Modifier.size(56.dp).background(DangerTint, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                    TripIcon(TripIconKind.Trash, contentDescription = null, tint = Danger, modifier = Modifier.size(25.dp))
                }
            },
            title = { Text("Delete my account?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Your profile will disappear and owned trips will be removed. This cannot be undone.", color = InkSoft)
                    Text("If this account has an active shared trip, transfer ownership or cancel/delete that trip first.", color = InkMuted, style = MaterialTheme.typography.bodySmall)
                    Text("Deleting your TripTandem account does not cancel an App Store or Google Play subscription. Cancel store billing separately in your subscription settings before deleting if you no longer want it.", color = InkMuted, style = MaterialTheme.typography.bodySmall)
                    if (requiresReauthentication && session?.email != null) {
                        Text("For your security, sign in again to confirm deletion.", color = InkSoft, style = MaterialTheme.typography.bodySmall)
                        TripTextField(
                            value = reauthEmail,
                            onValueChange = { if (it.length <= 160) reauthEmail = it },
                            label = { Text("Email") },
                            singleLine = true,
                        )
                        TripTextField(
                            value = reauthPassword,
                            onValueChange = { if (it.length <= 128) reauthPassword = it },
                            label = { Text("Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                        )
                    } else if (requiresReauthentication) {
                        Text("This temporary session cannot be reauthenticated here. Save a recovery login, sign out, and sign back in before deleting.", color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                    }
                    deleteError?.let { Text(errorMessage(it), color = ErrorRed, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                Button(
                    enabled = !deletingAccount && (!requiresReauthentication || (session?.email != null && reauthEmail.contains("@") && reauthPassword.length >= 6)),
                    onClick = {
                        scope.launch { confirmDeleteAccount() }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Danger),
                ) { Text(if (deletingAccount) "Deleting…" else if (requiresReauthentication) "Sign in & delete" else "Delete my account") }
            },
            dismissButton = { TextButton(enabled = !deletingAccount, onClick = { showDeleteAccount = false; deleteError = null }) { Text("Keep account", color = InkSoft) } },
        )
    }
}

@Composable
private fun HomeScreen(
    profile: TravelerProfile?,
    canCreateTrip: Boolean,
    proStatus: OrganizerProStatus?,
    repositories: TripTandemRepositories,
    featureFlags: TripTandemFeatureFlags,
    analytics: TripTandemAnalytics,
    onCreateTrip: () -> Unit,
    onUpgrade: () -> Unit,
    onTripSelected: (TripRecord) -> Unit,
    onProfile: () -> Unit,
    profileContent: @Composable () -> Unit,
) {
    var trips by remember { mutableStateOf<List<TripRecord>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<TripTandemError?>(null) }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val activityNavigation by CommunityNavigation.requests.collectAsState()
    LaunchedEffect(activityNavigation) { if (activityNavigation > 0) selectedTab = 2 }
    val scope = rememberCoroutineScope()

    fun loadTrips() {
        loading = true
        error = null
        scope.launch {
            when (val result = repositories.trips.listMyTrips()) {
                is DataResult.Success -> trips = result.value
                is DataResult.Failure -> error = result.error
            }
            loading = false
        }
    }
    LaunchedEffect(repositories) { loadTrips() }
    // Pro gates organizer-owned active trips only. A joined trip is included
    // in the list for collaboration but must not consume the user's Free
    // organizer allowance.
    val activeTripCount = profile?.uid?.let { activeOwnedTripCount(trips, it) } ?: 0
    val canStartAnotherTrip = proStatus?.isActive == true || activeTripCount < 1
    val createTripAction = if (canStartAnotherTrip) onCreateTrip else onUpgrade
    var actionableActivityCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(repositories.community, selectedTab) {
        when (val result = repositories.community.execute("activity", emptyMap())) {
            is DataResult.Success -> {
                actionableActivityCount = runCatching {
                    Json.parseToJsonElement(result.value).jsonObject["items"]?.jsonArray?.count { row ->
                        row.jsonObject["read"]?.let { (it as? JsonPrimitive)?.contentOrNull } != "true" &&
                            row.jsonObject["actionable"]?.let { (it as? JsonPrimitive)?.contentOrNull } == "true"
                    } ?: 0
                }.getOrDefault(0)
            }
            is DataResult.Failure -> Unit
        }
    }

    Scaffold(
        containerColor = Cream,
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                listOf("Trips", "Discover", "Activity", "Profile").forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = {
                            selectedTab = index

                        },
                        icon = {
                            Box {
                                TripIcon(tabIcon(index), contentDescription = label)
                                if (index == 2 && actionableActivityCount > 0) {
                                    Box(Modifier.size(16.dp).background(Danger, CircleShape), contentAlignment = Alignment.Center) {
                                        Text(if (actionableActivityCount > 9) "9+" else actionableActivityCount.toString(), color = Color.White, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        },
                        label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Coral,
                            selectedTextColor = Coral,
                            indicatorColor = CoralTint,
                            unselectedIconColor = InkMuted,
                            unselectedTextColor = InkMuted,
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        if (selectedTab == 3) {
            Box(Modifier.padding(bottom = innerPadding.calculateBottomPadding())) { profileContent() }
         } else if (selectedTab == 1 || selectedTab == 2) {
            Box(Modifier.padding(innerPadding)) {
                CommunityScreen(repositories.community, if (selectedTab == 1) "discover" else "activity", featureFlags, analytics,
                    onCreateTrip = createTripAction,
                    onOpenTrip = { id -> scope.launch {
                        when (val result = repositories.trips.getTrip(id)) {
                            is DataResult.Success -> onTripSelected(result.value)
                            is DataResult.Failure -> error = result.error
                        }
                    } })
            }
        } else LazyColumn(
            Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TripTandemMark()
                        Text("TripTandem", color = Ink, style = MaterialTheme.typography.titleLarge)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { selectedTab = 2 }) {
                            TripIcon(
                                TripIconKind.Bell,
                                contentDescription = "Notifications",
                                tint = Ink,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        Box(Modifier.size(44.dp).background(CoralTint, CircleShape), contentAlignment = Alignment.Center) {
                            Text(initials(profile?.displayName ?: "A"), color = Coral, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("YOUR TRIPS", color = CoralHero, style = MaterialTheme.typography.labelLarge)
                    Text("Welcome back, ${profile?.displayName ?: "traveler"}", style = MaterialTheme.typography.headlineSmall)
                    Text("One calm plan for every adventure—pick up where you left off.", color = InkMuted, style = MaterialTheme.typography.bodyLarge)
                }
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().then(if (canCreateTrip) Modifier.clickable(onClick = createTripAction) else Modifier),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = if (canCreateTrip) CoralHero else CreamMuted),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(Modifier.size(52.dp).background(if (canCreateTrip) Color.White.copy(alpha = .20f) else Color.White, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                            TripIcon(TripIconKind.Plus, contentDescription = null, tint = if (canCreateTrip) Color.White else InkMuted, modifier = Modifier.size(28.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(if (!canCreateTrip) "Trip creation is paused" else if (canStartAnotherTrip) "Create a new trip" else "Unlock more trips", color = if (canCreateTrip) Color.White else Ink, style = MaterialTheme.typography.titleMedium)
                            Text(if (!canCreateTrip) "Your account is suspended. Contact support or delete your account." else if (canStartAnotherTrip) "Invite friends and build the plan together" else "Organizer Pro includes more active trips (fair use).", color = if (canCreateTrip) Color.White.copy(alpha = .82f) else InkMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        if (canCreateTrip) TripIcon(TripIconKind.ArrowRight, contentDescription = "Create trip", tint = Color.White)
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Active trips", style = MaterialTheme.typography.titleLarge)
                    if (!loading) Text("${trips.size} plans", color = InkMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (loading) {
                item { LoadingCard(label = "Loading your trips…") }
            } else if (error != null) {
                item { ErrorBanner(error!!, onRetry = ::loadTrips) }
            } else if (trips.isEmpty()) {
                item {
                    EmptyState(
                        title = "No trips yet",
                        body = "Start with a destination, then invite the people who make the journey better.",
                        action = "Create your first trip",
                        onAction = if (canCreateTrip) createTripAction else null,
                    )
                }
            } else {
                items(trips, key = { it.id }) { trip -> TripCard(trip, onClick = { onTripSelected(trip) }) }
            }
            if (featureFlags.communityDiscoveryEnabled) {
                item {
                    Text("Open trips for you", style = MaterialTheme.typography.titleLarge)
                    Text("Discovery is still a gated pilot. Only approved public projections appear here.", color = InkMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
            item { PlanningTip() }
        }
    }
}

@Composable
private fun CreateTripScreen(
    repositories: TripTandemRepositories,
    drafts: TripDraftRepository,
    analytics: TripTandemAnalytics,
    proStatus: OrganizerProStatus?,
    onUpgrade: () -> Unit,
    onBack: () -> Unit,
    onCreated: (TripRecord) -> Unit,
) {
    val savedDraft = remember(drafts) { drafts.load() }
    var title by rememberSaveable { mutableStateOf(savedDraft?.title.orEmpty()) }
    var destination by rememberSaveable { mutableStateOf(savedDraft?.destination.orEmpty()) }
    var startDate by rememberSaveable { mutableStateOf(savedDraft?.startDate.orEmpty()) }
    var endDate by rememberSaveable { mutableStateOf(savedDraft?.endDate.orEmpty()) }
    var timezone by rememberSaveable { mutableStateOf(savedDraft?.destinationTimezone ?: "UTC") }
    var currency by rememberSaveable { mutableStateOf(savedDraft?.currency ?: "USD") }
    var expectationNote by rememberSaveable { mutableStateOf(savedDraft?.expectationNote.orEmpty()) }
    var datesFlexible by rememberSaveable { mutableStateOf(savedDraft?.datesFlexible ?: false) }
    var capacity by remember { mutableIntStateOf(savedDraft?.capacity ?: 4) }
    var visibility by remember { mutableStateOf(savedDraft?.visibility ?: TripVisibility.Private) }
    var pace by remember { mutableStateOf(savedDraft?.pace ?: TripPace.Balanced) }
    var budgetBand by remember { mutableStateOf(savedDraft?.budgetBand ?: BudgetBand.Moderate) }
    var interests by remember { mutableStateOf(savedDraft?.interests ?: emptyList()) }
    var coverColor by rememberSaveable { mutableStateOf(savedDraft?.coverColor ?: "#E8704A") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<TripTandemError?>(null) }
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableIntStateOf(1) }
    var attempted by remember { mutableStateOf(false) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val fieldErrors = validateTripDetails(title, destination, startDate, endDate, timezone, currency)
    val maxCapacity = if (proStatus?.isActive == true) 12 else 6

    LaunchedEffect(maxCapacity) {
        if (capacity > maxCapacity) capacity = maxCapacity
    }

    LaunchedEffect(title, destination, startDate, endDate, timezone, currency, expectationNote, datesFlexible, capacity, visibility, pace, budgetBand, interests, coverColor) {
        drafts.save(
            TripDraftSnapshot(
                title = title,
                destination = destination,
                startDate = startDate,
                endDate = endDate,
                destinationTimezone = timezone,
                datesFlexible = datesFlexible,
                visibility = visibility,
                capacity = capacity,
                pace = pace,
                budgetBand = budgetBand,
                currency = currency,
                expectationNote = expectationNote,
                interests = interests,
                coverColor = coverColor,
            ),
        )
    }

    Scaffold(containerColor = Cream) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp)) {
                TextButton(onClick = { if (step > 1) { step--; attempted = false } else onBack() }) { Text("Back", color = Coral) }
                Spacer(Modifier.weight(1f))
                Text("Create a trip", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onBack) { Text("Close", color = InkMuted) }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item {
                    Text("STEP $step OF 3", color = Coral, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(12.dp))
                    Text(when(step) { 1 -> "Where are you going?"; 2 -> "How do you like to travel?"; else -> "Who’s coming along?" }, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(when(step) { 1 -> "Start with the shape of the trip. You can invite your people once the plan feels right."; 2 -> "Set a pace and budget that feel right for your group."; else -> "Choose who can join. You can invite people after creating your trip." }, color = InkMuted)
                }
                if (step == 1) {
                    item {
                        DestinationPicker(destination, { destination = it }, if (attempted) fieldErrors["destination"] else null)
                    }
                    item {
                        DesignCard {
                            Text("DATES", color = InkMuted, style = MaterialTheme.typography.labelLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                TripDateField("Start", startDate, { startDate = it }, Modifier.weight(1f), attempted && fieldErrors.containsKey("dates"))
                                TripDateField("End", endDate, { endDate = it }, Modifier.weight(1f), attempted && fieldErrors.containsKey("dates"))
                            }
                            if (attempted) fieldErrors["dates"]?.let { Text(it, color = ErrorRed, style = MaterialTheme.typography.bodySmall) }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(datesFlexible, { datesFlexible = it })
                                Text("Dates are flexible", color = Sage, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                    item { TimezonePicker(timezone, { timezone = it }, if (attempted) fieldErrors["destinationTimezone"] else null) }
                    item {
                        DesignCard(containerColor = SageTint) {
                            Text("Private by default", style = MaterialTheme.typography.titleMedium)
                            Text("Only people you invite can view this plan. You choose later if it should be shareable by link.", color = InkMuted)
                        }
                    }
                    item {
                        Text("UP NEXT", color = InkMuted, style = MaterialTheme.typography.labelLarge)
                        Text("2   Travel style", style = MaterialTheme.typography.titleMedium)
                        Text("Pace, budget & interests", color = InkMuted)
                        Spacer(Modifier.height(12.dp))
                        Text("3   Privacy & group", style = MaterialTheme.typography.titleMedium)
                        Text("Who can join", color = InkMuted)
                    }
                }
                if (step == 2) {
                item {
                    TripTextField(title, { title = it.take(80) }, modifier = Modifier.fillMaxWidth(), label = { Text("Trip title") }, placeholder = { Text("e.g. Kyoto in spring") }, singleLine = true,
                        isError = attempted && fieldErrors.containsKey("title"), supportingText = { if (attempted) fieldErrors["title"]?.let { Text(it) } })
                }
                item {
                    TripTextField(currency, { currency = it.take(3).uppercase() }, modifier = Modifier.fillMaxWidth(), label = { Text("Currency") }, singleLine = true,
                        isError = attempted && fieldErrors.containsKey("currency"), supportingText = { if (attempted) fieldErrors["currency"]?.let { Text(it) } })
                }
                item { TripTextField(expectationNote, { expectationNote = it.take(500) }, modifier = Modifier.fillMaxWidth(), label = { Text("Group expectations (optional)") }, placeholder = { Text("Flexible mornings, shared meals…") }, minLines = 2, maxLines = 4) }
                item {
                    Text("Trip interests", style = MaterialTheme.typography.titleMedium)
                    Text("Optional signals to keep the shared plan aligned.", color = InkMuted, style = MaterialTheme.typography.bodySmall)
                    TripInterestVocabulary.values.chunked(3).forEach { row ->
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                            row.forEach { option ->
                                ChoiceChip(
                                    label = option,
                                    selected = option in interests,
                                    onClick = { interests = if (option in interests) interests - option else (interests + option).distinct().take(12) },
                                )
                            }
                        }
                    }
                }
                item {
                    Text("Cover accent", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
                        listOf("#E8704A" to CoralHero, "#6B9E7F" to SageAccent, "#E9D7C6" to Color(0xFFE9D7C6)).forEach { (value, color) ->
                            Box(
                                Modifier.size(42.dp).clip(CircleShape).background(color).border(if (coverColor == value) 3.dp else 1.dp, if (coverColor == value) Ink else CreamBorder, CircleShape).clickable { coverColor = value },
                            )
                        }
                    }
                }
                item {
                    Text("Travel pace", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    PaceSelector(pace) { pace = it }
                }
                item {
                    Text("Budget band", style = MaterialTheme.typography.titleMedium)
                    Text("A relative planning signal, not a financial rating.", color = InkMuted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                    BudgetSelector(budgetBand) { budgetBand = it }
                }
                }
                if (step == 3) {
                item {
                    Text("Who can join?", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoiceChip("Private", visibility == TripVisibility.Private, { visibility = TripVisibility.Private })
                        ChoiceChip("Unlisted link", visibility == TripVisibility.Unlisted, { visibility = TripVisibility.Unlisted })
                    }
                    Text(if (visibility == TripVisibility.Private) "Only members can read this trip." else "Anyone with the secure link can request access; it will not appear in discovery.", color = InkMuted, style = MaterialTheme.typography.bodySmall)
                }
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text("Group capacity", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${capacity} travelers including you" + if (proStatus?.isActive == true) "" else " · Free plan limit 6",
                                color = InkMuted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                enabled = capacity > 2,
                                onClick = { capacity-- },
                                modifier = Modifier.semantics { contentDescription = "Decrease group capacity" },
                            ) { Text("−", style = MaterialTheme.typography.titleLarge) }
                            Text(capacity.toString(), style = MaterialTheme.typography.titleMedium)
                            IconButton(
                                enabled = capacity < maxCapacity,
                                onClick = { capacity++ },
                                modifier = Modifier.semantics { contentDescription = "Increase group capacity" },
                            ) { Text("+", style = MaterialTheme.typography.titleLarge) }
                        }
                    }
                    if (proStatus?.isActive != true && capacity >= 6) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Need up to 12 travelers?", color = InkMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            TextButton(onClick = onUpgrade) { Text("View Pro", color = Coral) }
                        }
                    }
                }
                }
            }
            error?.let { failure -> ErrorBanner(failure, onRetry = { error = null }) }
            Button(
                onClick = {
                    attempted = true
                    val relevant = if (step == 1) setOf("destination", "dates", "destinationTimezone") else if (step == 2) setOf("title", "currency") else fieldErrors.keys
                    if (fieldErrors.keys.any { it in relevant }) {
                        if (step == 3) step = if (fieldErrors.keys.any { it in setOf("destination", "dates", "destinationTimezone") }) 1 else 2
                        scope.launch { listState.animateScrollToItem(1) }
                        return@Button
                    }
                    if (step < 3) {
                        step++
                        attempted = false
                        scope.launch { listState.scrollToItem(0) }
                        return@Button
                    }
                    saving = true
                    error = null
                    scope.launch {
                        when (val result = repositories.trips.createTrip(
                            CreateTripInput(
                                title = title.trim(),
                                destination = destination.trim(),
                                startDate = startDate,
                                endDate = endDate,
                                destinationTimezone = timezone.trim(),
                                datesFlexible = datesFlexible,
                                visibility = visibility,
                                capacity = capacity,
                                pace = pace,
                                budgetBand = budgetBand,
                                currency = currency.trim().uppercase().takeIf { it.length == 3 },
                                expectationNote = expectationNote.ifBlank { null },
                                interests = interests,
                                coverColor = coverColor,
                            ),
                        )) {
                            is DataResult.Success -> {
                                drafts.clear()
                                analytics.logEvent(TripTandemAnalytics.Events.TRIP_CREATED, mapOf("visibility" to visibility.wireValue, "capacity_bucket" to capacityBucket(capacity)))
                                onCreated(result.value)
                            }
                            is DataResult.Failure -> error = result.error
                        }
                        saving = false
                    }
                },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Coral),
            ) { if (saving) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp) else { Text(if (step < 3) "Continue" else if (visibility == TripVisibility.Private) "Create private trip" else "Create trip"); Spacer(Modifier.width(10.dp)); TripIcon(TripIconKind.ArrowRight, null) } }
        }
    }
}

@Composable
private fun ItineraryScreen(
    trip: TripRecord,
    repositories: TripTandemRepositories,
    analytics: TripTandemAnalytics,
    featureFlags: TripTandemFeatureFlags,
    isOnline: Boolean,
    revenueCat: RevenueCatCoordinator?,
    proStatus: OrganizerProStatus?,
    onUpgrade: () -> Unit,
    onBack: () -> Unit,
    onInviteMembers: () -> Unit,
    onShareInvite: ((String) -> Unit)? = null,
    onShareExport: ((String) -> Unit)? = null,
    onTripUpdated: (TripRecord) -> Unit,
    onTripDeleted: () -> Unit,
) {
    var currentTrip by remember(trip) { mutableStateOf(trip) }
    var items by remember(trip.id) { mutableStateOf<List<ItineraryItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<TripTandemError?>(null) }
    var selectedDay by rememberSaveable(trip.id) { mutableIntStateOf(0) }
    var tripTab by rememberSaveable(trip.id) { mutableIntStateOf(0) }
    var showCommunity by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var lastSyncTime by remember(currentTrip.id) { mutableStateOf<Long?>(null) }
    var editing by remember { mutableStateOf<ItineraryItem?>(null) }
    var showEditTrip by remember { mutableStateOf(false) }
    var showDeleteTrip by remember { mutableStateOf(false) }
    var pendingTripUpdate by remember { mutableStateOf<UpdateTripInput?>(null) }
    var deleting by remember { mutableStateOf<ItineraryItem?>(null) }
    var showGenerate by remember { mutableStateOf(false) }
    var generationJob by remember { mutableStateOf<ItineraryGenerationJob?>(null) }
    var generationInput by remember(currentTrip.id) { mutableStateOf(repositories.generationDrafts.load(currentTrip.id)) }
    var activeGenerationJobId by remember(currentTrip.id) { mutableStateOf(repositories.generationDrafts.loadActiveJobId(currentTrip.id)) }
    var showGenerationPreview by remember(currentTrip.id) { mutableStateOf(false) }
    var generationEditedItems by remember(currentTrip.id) { mutableStateOf<Map<String, GeneratedItineraryItem>>(emptyMap()) }
    var generationError by remember { mutableStateOf<TripTandemError?>(null) }
    var generationStarting by remember { mutableStateOf(false) }
    var generationCancelling by remember { mutableStateOf(false) }
    var generationPolling by remember(currentTrip.id) { mutableStateOf(false) }
    var selectedSuggestionIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var team by remember(trip.id) { mutableStateOf<List<TripMember>>(emptyList()) }
    var currentMember by remember(trip.id) { mutableStateOf<TripMember?>(null) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val dayCount = remember(currentTrip.startDate, currentTrip.endDate) { daysInclusive(currentTrip.startDate, currentTrip.endDate).coerceIn(1, 60) }
    val selectedDate = dayDateAt(currentTrip, selectedDay)
    val visibleItems = items.filter { (it.dayDate ?: currentTrip.startDate) == selectedDate }.sortedBy { it.position }
    // Completed, cancelled, and archived trips are read-only. Keeping this
    // gate in the shared UI mirrors the server-side policy and prevents an
    // editor from seeing mutation controls after a trip is closed.
    val canEdit = isOnline && (currentMember?.let {
        it.status == MembershipStatus.Active &&
            (it.role == TripMemberRole.Owner || it.role == TripMemberRole.Editor) &&
            currentTrip.status in setOf(TripStatus.Draft, TripStatus.Planning, TripStatus.Confirmed)
    } == true)
    val timeConflicts = visibleItems.filter { item ->
        visibleItems.any { other -> other.id != item.id && overlaps(item, other) }
    }.map { it.id }.toSet()

    fun clearPersistedGenerationJob() {
        activeGenerationJobId = null
        repositories.generationDrafts.clearActiveJobId(currentTrip.id)
        showGenerationPreview = false
        generationEditedItems = emptyMap()
    }

    fun pollGeneration(jobId: String, input: ItineraryGenerationInput?) {
        if (generationPolling) return
        generationPolling = true
        scope.launch {
            try {
                for (attempt in 0 until GENERATION_MAX_POLL_ATTEMPTS) {
                    if (generationJob?.jobId == jobId && generationJob?.state in setOf(
                            GenerationJobState.Succeeded,
                            GenerationJobState.PartiallySucceeded,
                            GenerationJobState.Applied,
                            GenerationJobState.Failed,
                            GenerationJobState.Cancelled,
                            GenerationJobState.Expired,
                        )) break
                    delay(1_000L)
                    when (val polled = repositories.generation.getJob(jobId)) {
                        is DataResult.Success -> {
                            if (polled.value.jobId != jobId) continue
                            generationJob = polled.value
                            when (polled.value.state) {
                                GenerationJobState.Succeeded, GenerationJobState.PartiallySucceeded -> {
                                    polled.value.preview?.let { preview ->
                                        selectedSuggestionIds = preview.items.map { it.id }.toSet()
                                        generationEditedItems = preview.items.associateBy { it.id }
                                    }
                                    generationError = null
                                    showGenerationPreview = true
                                    analytics.logEvent(
                                        TripTandemAnalytics.Events.GENERATION_COMPLETED,
                                        mapOf(
                                            "scope" to (input?.scope?.wireValue ?: "unknown"),
                                            "latency_bucket" to when {
                                                attempt < 5 -> "under_5s"
                                                attempt < 15 -> "5_15s"
                                                else -> "over_15s"
                                            },
                                            "result_status" to polled.value.state.wireValue,
                                            "item_count_bucket" to when {
                                                polled.value.preview?.items?.isEmpty() != false -> "0"
                                                polled.value.preview.items.size <= 5 -> "1_5"
                                                polled.value.preview.items.size <= 15 -> "6_15"
                                                else -> "16_plus"
                                            },
                                        ),
                                    )
                                }
                                GenerationJobState.Failed -> {
                                    generationError = TripTandemError.GenerationFailed(polled.value.failureClass)
                                    clearPersistedGenerationJob()
                                    analytics.logEvent(TripTandemAnalytics.Events.GENERATION_FAILED, mapOf("failure_class" to generationFailureClassForAnalytics(polled.value.failureClass)))
                                }
                                GenerationJobState.Cancelled, GenerationJobState.Expired, GenerationJobState.Applied -> {
                                    clearPersistedGenerationJob()
                                }
                                else -> Unit
                            }
                        }
                        is DataResult.Failure -> {
                            generationError = polled.error
                        }
                    }
                }
                val finalJob = generationJob?.takeIf { it.jobId == jobId }
                when {
                    finalJob?.state == GenerationJobState.Expired -> {
                        generationError = TripTandemError.GenerationFailed("expired")
                        clearPersistedGenerationJob()
                        analytics.logEvent(TripTandemAnalytics.Events.GENERATION_FAILED, mapOf("failure_class" to generationFailureClassForAnalytics("expired")))
                    }
                    finalJob?.state == GenerationJobState.Failed -> {
                        clearPersistedGenerationJob()
                    }
                    finalJob?.state == GenerationJobState.Cancelled || finalJob?.state == GenerationJobState.Applied -> {
                        clearPersistedGenerationJob()
                    }
                    finalJob?.state !in setOf(
                        GenerationJobState.Succeeded,
                        GenerationJobState.PartiallySucceeded,
                        GenerationJobState.Applied,
                        GenerationJobState.Cancelled,
                    ) -> {
                        generationError = generationError ?: TripTandemError.GenerationFailed("timeout")
                        analytics.logEvent(TripTandemAnalytics.Events.GENERATION_FAILED, mapOf("failure_class" to generationFailureClassForAnalytics("timeout")))
                    }
                }
            } finally {
                generationPolling = false
            }
        }
    }

    fun startGeneration(input: ItineraryGenerationInput) {
        input.validationError()?.let {
            generationError = it
            return
        }
        generationInput = input
        repositories.generationDrafts.save(currentTrip.id, input)
        generationError = null
        if (!isOnline) {
            // Keep the reviewed parameters locally so reconnecting can resume
            // with the same draft; no server request is attempted offline.
            generationError = TripTandemError.Offline
            return
        }
        generationStarting = true
        generationCancelling = false
        analytics.logEvent(
            TripTandemAnalytics.Events.GENERATION_STARTED,
            mapOf(
                "scope" to input.scope.wireValue,
                "entitlement_state" to if (proStatus?.isActive == true) "pro" else "free",
                "existing_item_bucket" to when {
                    items.isEmpty() -> "0"
                    items.size <= 5 -> "1_5"
                    items.size <= 15 -> "6_15"
                    else -> "16_plus"
                },
            ),
        )
        scope.launch {
            when (val created = repositories.generation.createJob(currentTrip.id, input)) {
                is DataResult.Failure -> {
                    generationStarting = false
                    if (created.error == TripTandemError.EntitlementRequired || created.error == TripTandemError.QuotaExceeded) {
                        analytics.logEvent(TripTandemAnalytics.Events.GENERATION_PAYWALL_VIEWED, mapOf("trigger" to "ai_allowance"))
                        onUpgrade()
                    } else {
                        generationError = created.error
                        analytics.logEvent(TripTandemAnalytics.Events.GENERATION_FAILED, mapOf("failure_class" to generationFailureClassForAnalytics("create_job")))
                    }
                }
                is DataResult.Success -> {
                    generationJob = created.value
                    activeGenerationJobId = created.value.jobId
                    repositories.generationDrafts.saveActiveJobId(currentTrip.id, created.value.jobId)
                    generationStarting = false
                    pollGeneration(created.value.jobId, input)
                }
            }
        }
    }

    fun cancelGeneration() {
        val job = generationJob ?: return
        if (job.state !in setOf(GenerationJobState.Queued, GenerationJobState.Running)) return
        generationCancelling = true
        scope.launch {
            when (val result = repositories.generation.cancelJob(job.jobId)) {
                is DataResult.Success -> {
                    generationJob = result.value.copy(state = GenerationJobState.Cancelled)
                    generationCancelling = false
                    generationError = null
                    clearPersistedGenerationJob()
                }
                is DataResult.Failure -> {
                    generationCancelling = false
                    generationError = result.error
                }
            }
        }
    }

    fun loadItems() {
        loading = true
        error = null
        scope.launch {
            when (val result = repositories.itinerary.listItems(trip.id)) {
                is DataResult.Success -> {
                    items = result.value.sortedBy { it.position }
                    lastSyncTime = currentEpochMillis()
                }
                is DataResult.Failure -> error = result.error
            }
            loading = false
        }
    }

    fun syncNow() {
        if (refreshing) return
        refreshing = true
        scope.launch {
            var refreshSuccess = false
            var isOfflineError = false
            when (val tripRes = repositories.trips.getTrip(currentTrip.id)) {
                is DataResult.Success -> {
                    currentTrip = tripRes.value
                    onTripUpdated(tripRes.value)
                    refreshSuccess = true
                }
                is DataResult.Failure -> {
                    if (tripRes.error is TripTandemError.Offline) isOfflineError = true
                }
            }
            when (val itemsRes = repositories.itinerary.listItems(currentTrip.id)) {
                is DataResult.Success -> {
                    items = itemsRes.value.sortedBy { it.position }
                    refreshSuccess = true
                }
                is DataResult.Failure -> {
                    if (itemsRes.error is TripTandemError.Offline) isOfflineError = true
                }
            }
            when (val membersRes = repositories.members.listMembers(currentTrip.id)) {
                is DataResult.Success -> {
                    team = membersRes.value
                    refreshSuccess = true
                }
                is DataResult.Failure -> {
                    if (membersRes.error is TripTandemError.Offline) isOfflineError = true
                }
            }
            if (refreshSuccess) {
                lastSyncTime = currentEpochMillis()
            }
            val resultParam = when {
                refreshSuccess && isOnline -> "success"
                isOfflineError || !isOnline -> "offline"
                else -> "error"
            }
            analytics.logEvent(
                TripTandemAnalytics.Events.OFFLINE_REFRESH_COMPLETED,
                mapOf("result" to resultParam),
            )
            refreshing = false
        }
    }

    fun persistTripUpdate(input: UpdateTripInput, dateChangeChoice: ItineraryDateChangeChoice? = null) {
        scope.launch {
            val previous = currentTrip
            when (val result = repositories.trips.updateTrip(currentTrip.id, input)) {
                is DataResult.Success -> {
                    showEditTrip = false
                    pendingTripUpdate = null
                    currentTrip = result.value
                    onTripUpdated(result.value)
                    if (dateChangeChoice != null) {
                        val affected = items.filter { item ->
                            item.dayDate != null && !isDateWithinRange(item.dayDate, input.startDate, input.endDate)
                        }
                        affected.forEachIndexed { index, item ->
                            when (dateChangeChoice) {
                                ItineraryDateChangeChoice.MoveToFirstDay -> {
                                    val movedEpoch = if (item.flexibleTime) {
                                        null
                                    } else {
                                        localDateTimeToEpochMillis(input.startDate, item.startTimeLabel, result.value.destinationTimezone)
                                    }
                                    repositories.itinerary.updateItem(
                                        result.value.id,
                                        item.id,
                                        item.toUpdateInput(index).copy(dayDate = input.startDate, startTimeEpochMillis = movedEpoch),
                                    )
                                }
                                ItineraryDateChangeChoice.KeepUnscheduled -> repositories.itinerary.updateItem(currentTrip.id, item.id, item.toUpdateInput(index).copy(dayDate = null))
                                ItineraryDateChangeChoice.DeleteAffected -> repositories.itinerary.deleteItem(result.value.id, item.id)
                            }
                        }
                        loadItems()
                    }
                    if (previous.status != result.value.status) {
                        when (result.value.status) {
                            TripStatus.Cancelled -> analytics.logEvent(TripTandemAnalytics.Events.TRIP_CANCELLED, mapOf("member_count_bucket" to "1_4"))
                            TripStatus.Archived -> analytics.logEvent(TripTandemAnalytics.Events.TRIP_ARCHIVED)
                            else -> Unit
                        }
                    }
                    if (previous.visibility != result.value.visibility) {
                        analytics.logEvent(TripTandemAnalytics.Events.TRIP_VISIBILITY_CHANGED, mapOf("from" to previous.visibility.wireValue, "to" to result.value.visibility.wireValue))
                    }
                }
                is DataResult.Failure -> {
                    if (result.error == TripTandemError.Conflict) analytics.logEvent(TripTandemAnalytics.Events.TRIP_CONFLICT_DETECTED)
                    error = result.error
                }
            }
        }
    }

    LaunchedEffect(currentTrip.id) {
        loadItems()
        team = (repositories.members.listMembers(trip.id) as? DataResult.Success)?.value.orEmpty()
        when (val result = repositories.members.getCurrentMember(currentTrip.id)) {
            is DataResult.Success -> {
                currentMember = result.value
                val uid = result.value?.userId.orEmpty()
                if (uid.isNotBlank() && lastSyncTime == null) {
                    val bundle = repositories.offlineCache.getCachedTripBundle(uid, currentTrip.id)
                    if (bundle != null) {
                        lastSyncTime = bundle.lastSyncEpochMillis
                    }
                }
            }
            is DataResult.Failure -> Unit
        }
        val persistedJobId = activeGenerationJobId
        if (!persistedJobId.isNullOrBlank() && generationJob == null) {
            when (val result = repositories.generation.getJob(persistedJobId)) {
                is DataResult.Success -> {
                    generationJob = result.value
                    when (result.value.state) {
                        GenerationJobState.Succeeded, GenerationJobState.PartiallySucceeded -> {
                            result.value.preview?.let { preview ->
                                selectedSuggestionIds = preview.items.map { it.id }.toSet()
                                generationEditedItems = preview.items.associateBy { it.id }
                            }
                            generationError = null
                            showGenerationPreview = true
                        }
                        GenerationJobState.Queued, GenerationJobState.Running -> {
                            generationError = null
                            pollGeneration(persistedJobId, generationInput)
                        }
                        GenerationJobState.Failed -> {
                            generationError = TripTandemError.GenerationFailed(result.value.failureClass)
                            clearPersistedGenerationJob()
                        }
                        GenerationJobState.Expired -> {
                            generationError = TripTandemError.GenerationFailed("expired")
                            clearPersistedGenerationJob()
                            analytics.logEvent(TripTandemAnalytics.Events.GENERATION_FAILED, mapOf("failure_class" to generationFailureClassForAnalytics("expired")))
                        }
                        GenerationJobState.Cancelled, GenerationJobState.Applied -> {
                            clearPersistedGenerationJob()
                        }
                    }
                }
                is DataResult.Failure -> {
                    // Keep the ID for a transient/offline failure so the next
                    // foreground can resume the same server-side job.
                    generationError = result.error
                }
            }
        }
    }

    if (showCommunity) {
        CommunityScreen(repositories.community, "publish", featureFlags, analytics, currentTrip.id,
            onBack = { showCommunity = false })
        return
    }

    Scaffold(
        containerColor = Cream,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    OutlinedButton(onClick = onBack, shape = CircleShape, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(44.dp), border = BorderStroke(1.dp, CreamBorder), colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White)) { Text("←", color = Ink, fontSize = 24.sp) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        team.take(3).forEach { member ->
                            Box(Modifier.size(32.dp).background(if (member.role == TripMemberRole.Owner) CoralTint else SageTint, CircleShape), contentAlignment = Alignment.Center) { Text(initials(member.displayName), color = InkSoft, style = MaterialTheme.typography.labelSmall) }
                        }
                        IconButton(onClick = { tripTab = 2 }) { TripIcon(TripIconKind.Users, "View members", tint = Coral) }
                        TextButton(onClick = { showExportDialog = true }) { Text("Export") }
                        if (currentMember?.role == TripMemberRole.Owner) TextButton(onClick = { showCommunity = true }) { Text("Community") }
                        if (canEdit) TextButton(onClick = { showEditTrip = true }) { Text("Edit") }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(currentTrip.visibility.label().uppercase(), CoralTint, Coral)
                    Text("${shortDate(currentTrip.startDate)}–${shortDate(currentTrip.endDate)} · ${currentTrip.destination}", color = InkMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                }
                Text(currentTrip.title, style = MaterialTheme.typography.headlineSmall)
                TripSectionTabs(tripTab) { tripTab = it }
            }
            if (!isOnline) {
                OfflineModeBanner(
                    lastSyncTime = lastSyncTime,
                    onSyncNow = ::syncNow,
                    refreshing = refreshing,
                )
            }
            if (tripTab == 0) {
                TripOverview(
                    trip = currentTrip,
                    items = items,
                    team = team,
                    loading = loading,
                    error = error,
                    retry = ::loadItems,
                    onMembers = { tripTab = 2 },
                    onItinerary = { tripTab = 1 },
                    lastSyncTime = lastSyncTime,
                    onSyncNow = ::syncNow,
                    refreshing = refreshing,
                    onExport = { showExportDialog = true },
                    isOnline = isOnline,
                )
            } else if (tripTab == 2) {
                Box(Modifier.weight(1f)) { MembersScreen(currentTrip, repositories, analytics, onBack = { tripTab = 1 }, onShareInvite = onShareInvite, embedded = true) }
            } else {
            LazyRow(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(dayCount) { day ->
                    val date = dayDateAt(currentTrip, day)
                    Surface(Modifier.width(72.dp).height(76.dp).clickable { selectedDay = day }, shape = RoundedCornerShape(18.dp), color = if (selectedDay == day) Coral else Color.White, border = BorderStroke(1.dp, if (selectedDay == day) Coral else CreamBorder)) {
                        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text(weekday(date), color = if (selectedDay == day) Color.White else InkMuted, style = MaterialTheme.typography.labelSmall)
                            Text(date.takeLast(2).toIntOrNull()?.toString().orEmpty(), color = if (selectedDay == day) Color.White else Ink, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column { Text("DAY ${selectedDay + 1}", color = CoralHero, style = MaterialTheme.typography.labelLarge); Text("${weekday(selectedDate)} · ${shortDate(selectedDate)}", style = MaterialTheme.typography.titleLarge) }
                        Text("${visibleItems.size} plans", color = InkMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (loading) item { LoadingCard("Syncing your itinerary…") }
                else if (error != null) item { ErrorBanner(error!!, ::loadItems) }
                else if (visibleItems.isEmpty()) item {
                    if (canEdit) {
                        ItineraryEmptyState(selectedDay, selectedDate, { showAdd = true }, if (featureFlags.aiGenerationEnabled && isOnline) ({ showGenerate = true }) else null)
                    } else {
                        EmptyState("Nothing planned yet", "The trip owner or an editor can add the first stop. You can read the plan here once it is shared.")
                    }
                }
                else {
                    if (timeConflicts.isNotEmpty()) item {
                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CoralTint)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TripIcon(TripIconKind.Alert, "Schedule warning", tint = Coral, modifier = Modifier.size(20.dp))
                                Text("This time overlaps another stop. Review before confirming.", color = Color(0xFF5C2416), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    items(visibleItems, key = { it.id }) { itineraryItem ->
                        ItineraryItemCard(
                            item = itineraryItem,
                            canEdit = canEdit,
                            lastEditorLabel = when {
                                itineraryItem.lastEditedBy == null -> null
                                itineraryItem.lastEditedBy == currentMember?.userId -> "you"
                                else -> "another member"
                            },
                            canMoveUp = canEdit && visibleItems.indexOf(itineraryItem) > 0,
                            canMoveDown = canEdit && visibleItems.indexOf(itineraryItem) < visibleItems.lastIndex,
                            onMove = { delta ->
                                val target = visibleItems.getOrNull(visibleItems.indexOf(itineraryItem) + delta) ?: return@ItineraryItemCard
                                if (!canEdit) return@ItineraryItemCard
                                scope.launch {
                                    val first = repositories.itinerary.updateItem(currentTrip.id, itineraryItem.id, itineraryItem.toUpdateInput(target.position))
                                    val second = if (first is DataResult.Success) repositories.itinerary.updateItem(currentTrip.id, target.id, target.toUpdateInput(itineraryItem.position)) else first
                                    if (second is DataResult.Success) {
                                        analytics.logEvent(TripTandemAnalytics.Events.ITINERARY_ITEM_REORDERED, mapOf("method" to "accessible_button"))
                                        loadItems()
                                    } else if (second is DataResult.Failure) {
                                        if (second.error == TripTandemError.Conflict) analytics.logEvent(TripTandemAnalytics.Events.ITINERARY_CONFLICT_DETECTED, mapOf("conflict_type" to "stale_revision"))
                                        error = second.error
                                    }
                                }
                            },
                            onEdit = { editing = itineraryItem },
                            onDelete = { deleting = itineraryItem },
                        )
                    }
                }
                item {
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = SageTint)) {
                        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                            TripIcon(TripIconKind.Check, contentDescription = null, tint = Sage, modifier = Modifier.size(22.dp))
                            Column { Text("Planning note", color = Sage, style = MaterialTheme.typography.labelLarge); Text("Ideas stay flexible until the group agrees. Add a note instead of overwriting a confirmed plan.", color = Color(0xFF203B2B), style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
            if (canEdit && currentTrip.status != TripStatus.Cancelled && currentTrip.status != TripStatus.Archived) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (featureFlags.aiGenerationEnabled) {
                        OutlinedButton(
                            enabled = isOnline,
                            onClick = { showGenerate = true; generationError = null },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Coral),
                        ) {
                            Text("Generate a safe draft", color = Coral)
                        }
                        if (!isOnline) {
                            Text(
                                "You’re offline. Reconnect to generate; your reviewed draft stays on this device.",
                                color = InkMuted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    generationJob?.takeIf { it.state == GenerationJobState.Queued || it.state == GenerationJobState.Running }?.let {
                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CoralTint)) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(color = Coral, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text("Preparing your draft…", Modifier.weight(1f).padding(start = 10.dp), style = MaterialTheme.typography.bodySmall)
                                TextButton(enabled = !generationCancelling, onClick = ::cancelGeneration) { Text(if (generationCancelling) "Cancelling…" else "Cancel", color = Coral) }
                            }
                        }
                    }
                    generationError?.let { failure ->
                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CoralTint)) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(errorMessage(failure), color = Color(0xFF5C2416), style = MaterialTheme.typography.bodySmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    generationInput?.let { savedInput ->
                                        TextButton(enabled = isOnline, onClick = { startGeneration(savedInput) }) { Text("Retry", color = Coral) }
                                    }
                                    if (failure == TripTandemError.QuotaExceeded || failure == TripTandemError.EntitlementRequired) {
                                        TextButton(onClick = onUpgrade) { Text("View Pro", color = Coral) }
                                    }
                                }
                            }
                        }
                    }
                    generationJob?.takeIf {
                        it.state == GenerationJobState.Succeeded || it.state == GenerationJobState.PartiallySucceeded
                    }?.let {
                        if (!showGenerationPreview) {
                            Card(
                                Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = SageTint),
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Draft ready to review", color = Sage, style = MaterialTheme.typography.labelLarge)
                                        Text("Nothing has been added to the shared itinerary yet.", color = Color(0xFF203B2B), style = MaterialTheme.typography.bodySmall)
                                    }
                                    TextButton(onClick = { showGenerationPreview = true }) { Text("Review", color = Coral) }
                                }
                            }
                        }
                    }
                    Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Coral)) {
                        TripIcon(TripIconKind.Plus, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Add to day ${selectedDay + 1}", style = MaterialTheme.typography.titleMedium)
                    }
                }
            } else if (!isOnline && currentTrip.status != TripStatus.Cancelled && currentTrip.status != TripStatus.Archived) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = CreamBorder.copy(alpha = 0.5f),
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TripIcon(TripIconKind.Alert, contentDescription = null, tint = InkMuted, modifier = Modifier.size(18.dp))
                            Text("Connection required to add or edit itinerary stops.", color = InkMuted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            }
        }
    }

    if (showGenerate) {
        GenerateItineraryDialog(
            trip = currentTrip,
            lockedItemIds = items.filter { it.status == ItineraryItemStatus.Booked }.map { it.id },
            initial = generationInput,
            isOnline = isOnline,
            busy = generationStarting,
            error = generationError,
            onDismiss = { if (!generationStarting) showGenerate = false },
            onDraftChanged = { input ->
                generationInput = input
                repositories.generationDrafts.save(currentTrip.id, input)
            },
            onGenerate = {
                showGenerate = false
                startGeneration(it)
            },
        )
    }
    generationJob?.takeIf {
        showGenerationPreview && (it.state == GenerationJobState.Succeeded || it.state == GenerationJobState.PartiallySucceeded)
    }?.let { job ->
        GenerationPreviewDialog(
            job = job,
            selectedIds = selectedSuggestionIds,
            editedItems = generationEditedItems,
            onToggle = { id ->
                selectedSuggestionIds = if (id in selectedSuggestionIds) selectedSuggestionIds - id else selectedSuggestionIds + id
                analytics.logEvent(TripTandemAnalytics.Events.GENERATION_PREVIEW_EDITED)
            },
            onEditItem = { updated ->
                generationEditedItems = generationEditedItems + (updated.id to updated)
                analytics.logEvent(TripTandemAnalytics.Events.GENERATION_PREVIEW_EDITED)
            },
            onDismiss = { showGenerationPreview = false },
            onApply = { selected, editedItems, finishApply ->
                scope.launch {
                    val editFingerprint = editedItems
                        .sortedBy { it.id }
                        .joinToString("|") { item ->
                            listOf(item.id, item.title, item.startTimeLabel.orEmpty(), item.flexibleTime, item.durationMinutes, item.place.orEmpty(), item.note.orEmpty()).joinToString("~")
                        }
                        .hashCode()
                    val idempotencyKey = "${job.jobId}-${selected.sorted().joinToString().hashCode()}-$editFingerprint"
                    when (val applied = repositories.generation.applyJob(job.jobId, selected, idempotencyKey, editedItems)) {
                        is DataResult.Success -> {
                            analytics.logEvent(
                                TripTandemAnalytics.Events.GENERATION_APPLIED,
                                mapOf(
                                    "selected_count_bucket" to when {
                                        selected.isEmpty() -> "0"
                                        selected.size <= 5 -> "1_5"
                                        selected.size <= 15 -> "6_15"
                                        else -> "16_plus"
                                    },
                                    "duplicate_warning" to job.preview?.items.orEmpty().any { it.id in selected && it.warning != null }.toString(),
                                ),
                            )
                            repositories.generationDrafts.clear(currentTrip.id)
                            showGenerationPreview = false
                            generationEditedItems = emptyMap()
                            generationJob = null
                            loadItems()
                            finishApply()
                        }
                        is DataResult.Failure -> {
                            generationError = applied.error
                            if (applied.error == TripTandemError.Conflict) analytics.logEvent(TripTandemAnalytics.Events.ITINERARY_CONFLICT_DETECTED, mapOf("conflict_type" to "generation_apply"))
                            finishApply()
                        }
                    }
                }
            },
        )
    }

    if (showAdd) {
        AddItineraryDialog(
            dayDate = selectedDate,
            destinationTimezone = currentTrip.destinationTimezone,
            dayNumber = selectedDay + 1, destination = currentTrip.destination, existingItems = items.map { it.copy(dayDate = it.dayDate ?: currentTrip.startDate) },
            onDismiss = { showAdd = false },
            onSave = { input ->
                scope.launch {
                    when (val result = repositories.itinerary.createItem(currentTrip.id, input.copy(position = visibleItems.size, dayDate = selectedDate))) {
                        is DataResult.Success -> { showAdd = false; analytics.logEvent(TripTandemAnalytics.Events.ITINERARY_ITEM_CREATED, mapOf("type" to input.type.wireValue, "entry_method" to "manual")); loadItems() }
                        is DataResult.Failure -> error = result.error
                    }
                }
            },
        )
    }
    editing?.let { item ->
        AddItineraryDialog(
            initial = item,
            dayDate = item.dayDate ?: selectedDate,
            destinationTimezone = currentTrip.destinationTimezone,
            dayNumber = selectedDay + 1, destination = currentTrip.destination, existingItems = items.map { it.copy(dayDate = it.dayDate ?: currentTrip.startDate) },
            onDismiss = { editing = null },
            onSave = { input ->
                scope.launch {
                    when (val result = repositories.itinerary.updateItem(currentTrip.id, item.id, input.toUpdateInput(item))) {
                        is DataResult.Success -> { editing = null; analytics.logEvent(TripTandemAnalytics.Events.ITINERARY_ITEM_UPDATED, mapOf("field_group" to "item_details")); loadItems() }
                        is DataResult.Failure -> {
                            if (result.error == TripTandemError.Conflict) analytics.logEvent(TripTandemAnalytics.Events.ITINERARY_CONFLICT_DETECTED, mapOf("conflict_type" to "stale_revision"))
                            error = result.error
                        }
                    }
                }
            },
        )
    }
    deleting?.let { item ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Remove this item?") },
            text = { Text("“${item.title}” will be removed from the shared plan. You’ll have 30 seconds to undo.") },
            confirmButton = { TextButton(onClick = {
                deleting = null
                scope.launch {
                    when (val result = repositories.itinerary.deleteItem(currentTrip.id, item.id)) {
                        is DataResult.Success -> {
                            analytics.logEvent(TripTandemAnalytics.Events.ITINERARY_ITEM_DELETED)
                            loadItems()
                            val snackbarJob = scope.launch {
                                if (snackbarHostState.showSnackbar("Item removed", "Undo", duration = SnackbarDuration.Indefinite) == SnackbarResult.ActionPerformed) {
                                    when (val restored = repositories.itinerary.restoreItem(currentTrip.id, item.id)) {
                                        is DataResult.Success -> { analytics.logEvent(TripTandemAnalytics.Events.ITINERARY_UNDO_RESTORED); loadItems() }
                                        is DataResult.Failure -> error = restored.error
                                    }
                                }
                            }
                            // Firestore/local repositories enforce the same
                            // 30-second window. Dismiss the action at the
                            // server boundary even though Material has no
                            // built-in 30-second snackbar duration.
                            scope.launch {
                                delay(30_000L)
                                snackbarHostState.currentSnackbarData?.dismiss()
                                snackbarJob.cancel()
                            }
                        }
                        is DataResult.Failure -> error = result.error
                    }
                }
            }) { Text("Remove", color = ErrorRed) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep") } },
        )
    }
    if (showEditTrip) {
        EditTripDialog(
            trip = currentTrip,
            onDismiss = { showEditTrip = false },
            onSave = { input ->
                val dateChanged = input.startDate != currentTrip.startDate || input.endDate != currentTrip.endDate
                val affectedItems = items.count { item ->
                    item.dayDate != null && !isDateWithinRange(item.dayDate, input.startDate, input.endDate)
                }
                if (dateChanged && affectedItems > 0) {
                    showEditTrip = false
                    pendingTripUpdate = input
                } else {
                    persistTripUpdate(input)
                }
            },
            onDelete = { showEditTrip = false; showDeleteTrip = true },
        )
    }
    if (showDeleteTrip) {
        AlertDialog(
            onDismissRequest = { showDeleteTrip = false },
            title = { Text("Delete this trip?") },
            text = { Text("This permanently removes the trip and its itinerary for every member. Use Cancelled if you only want to make it read-only.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteTrip = false
                    scope.launch {
                        when (val result = repositories.trips.deleteTrip(currentTrip.id)) {
                            is DataResult.Success -> { analytics.logEvent(TripTandemAnalytics.Events.TRIP_DELETED); onTripDeleted() }
                            is DataResult.Failure -> error = result.error
                        }
                    }
                }) { Text("Delete permanently", color = ErrorRed) }
            },
            dismissButton = { TextButton(onClick = { showDeleteTrip = false }) { Text("Keep trip") } },
        )
    }
    pendingTripUpdate?.let { input ->
        AlertDialog(
            onDismissRequest = { pendingTripUpdate = null },
            title = { Text("Some itinerary days changed") },
            text = { Text("${items.count { item -> item.dayDate != null && !isDateWithinRange(item.dayDate, input.startDate, input.endDate) }} itinerary item(s) fall outside the new dates. Choose what to do with them.") },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { persistTripUpdate(input, ItineraryDateChangeChoice.MoveToFirstDay) }) { Text("Move to ${input.startDate}", color = Coral) }
                    TextButton(onClick = { persistTripUpdate(input, ItineraryDateChangeChoice.KeepUnscheduled) }) { Text("Keep unscheduled", color = Coral) }
                    TextButton(onClick = { persistTripUpdate(input, ItineraryDateChangeChoice.DeleteAffected) }) { Text("Delete affected items", color = ErrorRed) }
                }
            },
            dismissButton = { TextButton(onClick = { pendingTripUpdate = null }) { Text("Keep current dates") } },
        )
    }
    if (showExportDialog) {
        ExportItineraryDialog(
            trip = currentTrip,
            items = items,
            members = team,
            onDismiss = { showExportDialog = false },
            onShare = onShareExport,
            analytics = analytics,
        )
    }
}

@Composable
private fun GenerateItineraryDialog(
    trip: TripRecord,
    lockedItemIds: List<String>,
    initial: ItineraryGenerationInput?,
    isOnline: Boolean,
    busy: Boolean,
    error: TripTandemError?,
    onDismiss: () -> Unit,
    onDraftChanged: (ItineraryGenerationInput) -> Unit,
    onGenerate: (ItineraryGenerationInput) -> Unit,
) {
    var selectedScope by remember(initial) { mutableStateOf(initial?.scope ?: GenerationScope.WholeTrip) }
    var selectedDay by remember(initial) { mutableStateOf(initial?.dayDate ?: trip.startDate) }
    var pace by remember(initial) { mutableStateOf(initial?.pace ?: trip.pace ?: TripPace.Balanced) }
    var budget by remember(initial) { mutableStateOf(initial?.budgetBand ?: trip.budgetBand ?: BudgetBand.Moderate) }
    var interests by remember(initial) { mutableStateOf(initial?.interests ?: trip.interests) }
    var startLabel by remember(initial) { mutableStateOf(initial?.dailyStartLabel.orEmpty()) }
    var endLabel by remember(initial) { mutableStateOf(initial?.dailyEndLabel.orEmpty()) }
    var accessibility by remember(initial) { mutableStateOf(initial?.accessibilityNotes.orEmpty()) }
    var diet by remember(initial) { mutableStateOf(initial?.dietNotes.orEmpty()) }
    var reviewed by remember(initial) { mutableStateOf(false) }
    val input = ItineraryGenerationInput(
        scope = selectedScope,
        dayDate = if (selectedScope == GenerationScope.SingleDay) selectedDay else null,
        pace = pace,
        budgetBand = budget,
        interests = interests,
        dailyStartLabel = startLabel.ifBlank { null },
        dailyEndLabel = endLabel.ifBlank { null },
        accessibilityNotes = accessibility.ifBlank { null },
        dietNotes = diet.ifBlank { null },
        lockedItemIds = lockedItemIds,
    )
    val validation = input.validationError()
    LaunchedEffect(input) {
        onDraftChanged(input)
        // Any change to the reviewed payload requires a fresh acknowledgement
        // so the consent checkbox always describes the current request.
        reviewed = false
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Generate a safe draft") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                item {
                    Text("Review the small set of trip details sent for planning. Members, contact details, exact lodging, and private notes are never sent to the AI provider.", color = InkMuted, style = MaterialTheme.typography.bodySmall)
                }
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = CreamMuted),
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Trip context", color = InkSoft, style = MaterialTheme.typography.labelLarge)
                            Text(trip.destination, style = MaterialTheme.typography.titleMedium)
                            Text("${trip.startDate} – ${trip.endDate} · ${trip.destinationTimezone}", color = InkMuted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item {
                    Text("What should we draft?", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                        GenerationScope.entries.forEach { option -> ChoiceChip(option.label, selectedScope == option, { selectedScope = option }) }
                    }
                }
                if (selectedScope == GenerationScope.SingleDay) {
                    item {
                        TripDateField("Day", selectedDay, { selectedDay = it }, modifier = Modifier.fillMaxWidth())
                    }
                }
                item {
                    Text("Travel style", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                        TripPace.entries.forEach { option -> ChoiceChip(option.label, pace == option, { pace = option }) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                        BudgetBand.entries.forEach { option -> ChoiceChip(option.label, budget == option, { budget = option }) }
                    }
                }
                item {
                    Text("Interests", style = MaterialTheme.typography.titleMedium)
                    TripInterestVocabulary.values.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 5.dp)) {
                            row.forEach { option -> ChoiceChip(option, option in interests, { interests = if (option in interests) interests - option else (interests + option).distinct().take(12) }) }
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        TripTextField(startLabel, { if (it.length <= 32) startLabel = it }, modifier = Modifier.weight(1f), label = { Text("Start") }, placeholder = { Text("morning") }, singleLine = true)
                        TripTextField(endLabel, { if (it.length <= 32) endLabel = it }, modifier = Modifier.weight(1f), label = { Text("End") }, placeholder = { Text("evening") }, singleLine = true)
                    }
                    Text("Optional labels keep the plan flexible.", color = InkMuted, style = MaterialTheme.typography.labelSmall)
                }
                item {
                    TripTextField(accessibility, { if (it.length <= 500) accessibility = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Accessibility notes (optional)") }, maxLines = 2)
                    TripTextField(diet, { if (it.length <= 500) diet = it }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Diet notes (optional)") }, maxLines = 2)
                }
                item {
                    Text("${lockedItemIds.size} booked item(s) will stay locked and unchanged.", color = Sage, style = MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = reviewed, onCheckedChange = { reviewed = it })
                        Text("I reviewed the data and understand this is an unverified draft.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (validation != null) item { Text("Check the highlighted generation details before continuing.", color = ErrorRed, style = MaterialTheme.typography.bodySmall) }
                if (!isOnline) item {
                    Text(
                        "You’re offline. Reconnect to generate this preview; your reviewed parameters are saved on this device.",
                        color = InkMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                error?.let { failure -> item { Text(errorMessage(failure), color = ErrorRed, style = MaterialTheme.typography.bodySmall) } }
            }
        },
        confirmButton = {
            TextButton(enabled = isOnline && !busy && reviewed && validation == null, onClick = { onGenerate(input) }) {
                Text(if (busy) "Starting…" else "Generate preview", color = Coral)
            }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun GenerationPreviewDialog(
    job: ItineraryGenerationJob,
    selectedIds: Set<String>,
    editedItems: Map<String, GeneratedItineraryItem>,
    onToggle: (String) -> Unit,
    onEditItem: (GeneratedItineraryItem) -> Unit,
    onDismiss: () -> Unit,
    onApply: (List<String>, List<GeneratedItineraryItem>, () -> Unit) -> Unit,
) {
    val preview = job.preview ?: return
    var applying by remember(job.jobId) { mutableStateOf(false) }
    var editingItemId by remember(job.jobId) { mutableStateOf<String?>(null) }
    var detailsItem by remember(job.jobId) { mutableStateOf<GeneratedItineraryItem?>(null) }
    val displayItems = preview.items.map { editedItems[it.id] ?: it }
    AlertDialog(
        onDismissRequest = { if (!applying) onDismiss() },
        title = { Text("Review your draft") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                item {
                    Text(preview.unverifiedInformationNotice, color = Coral, style = MaterialTheme.typography.bodySmall)
                    preview.assumptions.forEach { Text("• $it", color = InkMuted, style = MaterialTheme.typography.labelSmall) }
                    preview.warnings.forEach { Text("⚠ $it", color = Coral, style = MaterialTheme.typography.labelSmall) }
                }
                items(displayItems, key = { it.id }) { item ->
                    Card(colors = CardDefaults.cardColors(containerColor = if (item.id in selectedIds) SageTint else Color.White), border = BorderStroke(1.dp, CreamBorder)) {
                        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = item.id in selectedIds, onCheckedChange = { onToggle(item.id) })
                            Column(Modifier.weight(1f)) {
                                Text(item.title, style = MaterialTheme.typography.titleMedium)
                                Text("${item.dayDate} · ${item.startTimeLabel ?: "Flexible time"} · ${item.durationMinutes} min", color = InkMuted, style = MaterialTheme.typography.bodySmall)
                                item.place?.let { Text(it, color = InkSoft, style = MaterialTheme.typography.bodySmall) }
                                item.warning?.let { Text(it, color = Coral, style = MaterialTheme.typography.labelSmall) }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(enabled = !applying, onClick = { editingItemId = item.id }) {
                                        Text("Edit", color = Coral)
                                    }
                                    TextButton(enabled = !applying, onClick = { detailsItem = item }) {
                                        Text("Check details", color = Coral)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedIds.isNotEmpty() && !applying,
                onClick = {
                    applying = true
                    onApply(selectedIds.toList(), selectedIds.mapNotNull { editedItems[it] }) { applying = false }
                },
            ) {
                Text(if (applying) "Applying…" else "Apply ${selectedIds.size} selected", color = Coral)
            }
        },
        dismissButton = { TextButton(enabled = !applying, onClick = onDismiss) { Text("Keep editing") } },
    )
    editingItemId?.let { itemId ->
        // Open the editor for the visible suggestion even on its first edit.
        // Previously this looked only in editedItems, so an untouched preview
        // row rendered an Edit button that did nothing.
        displayItems.firstOrNull { it.id == itemId }?.let { item ->
            EditGeneratedItineraryItemDialog(
                item = item,
                onDismiss = { editingItemId = null },
                onSave = { updated ->
                    onEditItem(updated)
                    editingItemId = null
                },
            )
        }
    }
    detailsItem?.let { item ->
        AlertDialog(
            onDismissRequest = { detailsItem = null },
            title = { Text("Check details before you go") },
            text = {
                Text(
                    "${item.title} is an AI planning idea, not verified travel information. Confirm opening hours, pricing, availability, accessibility, and local safety with the venue or a trusted provider before confirming it with the group.",
                    color = InkSoft,
                )
            },
            confirmButton = {
                TextButton(onClick = { detailsItem = null }) { Text("Got it", color = Coral) }
            },
        )
    }
}

@Composable
private fun EditGeneratedItineraryItemDialog(
    item: GeneratedItineraryItem,
    onDismiss: () -> Unit,
    onSave: (GeneratedItineraryItem) -> Unit,
) {
    var title by remember(item.id) { mutableStateOf(item.title) }
    var timeLabel by remember(item.id) { mutableStateOf(item.startTimeLabel.orEmpty()) }
    var flexibleTime by remember(item.id) { mutableStateOf(item.flexibleTime) }
    var durationText by remember(item.id) { mutableStateOf(item.durationMinutes.toString()) }
    var place by remember(item.id) { mutableStateOf(item.place.orEmpty()) }
    var note by remember(item.id) { mutableStateOf(item.note.orEmpty()) }
    val duration = durationText.toIntOrNull()
    val clockValid = timeLabel.matches(Regex("(?:[01]\\d|2[0-3]):[0-5]\\d"))
    val flexibleValid = timeLabel.isBlank() || timeLabel.lowercase() in setOf("morning", "late morning", "afternoon", "evening", "night", "anytime")
    val valid = title.trim().length in 1..160 && duration != null && duration in 0..1440 &&
        (if (flexibleTime) flexibleValid else clockValid)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit suggestion") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${item.dayDate} · ${item.type.label}", color = InkMuted, style = MaterialTheme.typography.bodySmall)
                TripTextField(title, { if (it.length <= 160) title = it }, label = { Text("Title") }, singleLine = true)
                TripTextField(timeLabel, { if (it.length <= 32) timeLabel = it }, label = { Text("Time or label") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = flexibleTime,
                        onCheckedChange = { checked ->
                            flexibleTime = checked
                            if (checked && clockValid) timeLabel = "morning"
                            if (!checked && !clockValid) timeLabel = "09:00"
                        },
                    )
                    Text("Time is flexible", style = MaterialTheme.typography.bodySmall)
                }
                TripTextField(durationText, { if (it.length <= 4 && it.all(Char::isDigit)) durationText = it }, label = { Text("Duration (minutes)") }, singleLine = true)
                TripTextField(place, { if (it.length <= 200) place = it }, label = { Text("Place (optional)") }, singleLine = true)
                TripTextField(note, { if (it.length <= 1000) note = it }, label = { Text("Note (optional)") }, minLines = 2, maxLines = 3)
                if (!valid) Text("Use a title, a valid time (HH:mm or a flexible label), and 0–1,440 minutes.", color = ErrorRed, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onSave(item.copy(
                    title = title.trim(),
                    startTimeLabel = timeLabel.trim().ifBlank { null },
                    flexibleTime = flexibleTime,
                    durationMinutes = duration ?: item.durationMinutes,
                    place = place.trim().ifBlank { null },
                    note = note.trim().ifBlank { null },
                ))
            }) { Text("Save", color = Coral) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun OrganizerProPaywallDialog(
    coordinator: RevenueCatCoordinator?,
    trigger: String,
    entitlementState: String,
    analytics: TripTandemAnalytics,
    onStatusChanged: (OrganizerProStatus) -> Unit,
    onDismiss: () -> Unit,
    onOpenExternalUrl: ((String) -> Unit)?,
    onManageSubscription: (() -> Unit)?,
) {
    var offering by remember(coordinator) { mutableStateOf<OrganizerProOffering?>(null) }
    var selected by remember { mutableStateOf(RevenueCatPackageSelection.Annual) }
    var purchaseState by remember(coordinator) { mutableStateOf(OrganizerProPurchaseState.Idle) }
    var message by remember { mutableStateOf<String?>(null) }
    var paywallViewedLogged by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val busy = purchaseState == OrganizerProPurchaseState.LoadingProducts || purchaseState == OrganizerProPurchaseState.Purchasing

    LaunchedEffect(coordinator) {
        if (coordinator == null) {
            purchaseState = OrganizerProPurchaseState.Unavailable
            message = "Organizer Pro is not configured for this build yet. The free plan is still available."
        } else {
            purchaseState = OrganizerProPurchaseState.LoadingProducts
            when (val result = coordinator.currentOffering()) {
                is DataResult.Success -> {
                    offering = result.value
                    purchaseState = OrganizerProPurchaseState.Ready
                }
                is DataResult.Failure -> {
                    purchaseState = result.error.toOrganizerProPurchaseState()
                    message = errorMessage(result.error)
                }
            }
        }
    }
    LaunchedEffect(purchaseState, offering?.identifier, trigger, entitlementState) {
        // Wait until the initial catalog request has completed so the event
        // carries the real offering identifier whenever RevenueCat provides
        // one. Logging on the initial Idle frame would permanently record an
        // "unavailable" catalog even when the request succeeds a moment later.
        val catalogSettled = purchaseState != OrganizerProPurchaseState.Idle
            && purchaseState != OrganizerProPurchaseState.LoadingProducts
        if (catalogSettled && !paywallViewedLogged) {
            val offeringId = offering?.identifier
                ?.takeIf { it.isNotBlank() && it.length <= 40 && it.none(Char::isWhitespace) }
                ?: "unavailable"
            analytics.logEvent(
                TripTandemAnalytics.Events.PAYWALL_VIEWED,
                mapOf(
                    "trigger" to trigger,
                    "offering_id" to offeringId,
                    "entitlement_state" to entitlementState,
                ),
            )
            paywallViewedLogged = true
        }
    }
    val availableSelections = RevenueCatPackageSelection.entries.filter { option ->
        offering?.packages?.any { it.matches(option) } == true
    }
    LaunchedEffect(offering) {
        if (availableSelections.isNotEmpty() && selected !in availableSelections) {
            selected = availableSelections.first()
        }
    }
    val selectedPackage = offering?.packages?.firstOrNull { it.matches(selected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Cream,
        title = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            StatusChip("ORGANIZER PRO", CoralTint, Coral)
            Text("More adventures.\nAll your people.", style = MaterialTheme.typography.headlineSmall)
        } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    when (trigger) {
                        "second_active_trip" -> "Plan more journeys with more active trips (fair use)."
                        "member_capacity" -> "Bring up to 12 travelers into one shared plan."
                        else -> "Keep shaping richer plans with fair-use AI generation."
                    },
                    color = InkSoft,
                )
                listOf("More trips on the horizon" to "Plan multiple active trips (fair use).", "Room for your whole crew" to "Bring up to 12 travelers per trip.", "A head start on every itinerary" to "Up to 30 AI drafts each month.").forEach { (title, detail) ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TripIcon(TripIconKind.Check, null, tint = Sage)
                        Column { Text(title, style = MaterialTheme.typography.titleMedium); Text(detail, color = InkMuted, style = MaterialTheme.typography.bodySmall) }
                    }
                }
                Text("Joining, invites and safety tools always stay free.", color = Sage, style = MaterialTheme.typography.bodySmall)
                if (busy) CircularProgressIndicator(color = Coral, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                else if (offering != null) {
                    if (availableSelections.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            availableSelections.forEach { option ->
                                val label = when (option) {
                                    RevenueCatPackageSelection.Monthly -> "Monthly"
                                    RevenueCatPackageSelection.Annual -> "Annual"
                                }
                                ChoiceChip(
                                    label,
                                    selected == option,
                                    {
                                        selected = option
                                        analytics.logEvent(TripTandemAnalytics.Events.PACKAGE_SELECTED, mapOf("package_type" to option.name.lowercase()))
                                    },
                                )
                            }
                        }
                    }
                    selectedPackage?.let { packageInfo ->
                        Text("${packageInfo.formattedPrice} · ${packageInfo.billingPeriodLabel} · ${packageInfo.productTitle}", style = MaterialTheme.typography.titleMedium)
                        packageInfo.introductoryOffer?.let { intro ->
                            Text(
                                if (intro.isFreeTrial) "Intro offer: ${intro.periodLabel} free"
                                else "Intro offer: ${intro.formattedPrice} for ${intro.periodLabel}",
                                color = Sage,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    if (selectedPackage == null) {
                        Text("Plans are temporarily unavailable. You can keep using the free experience.", color = InkMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Renews automatically until you cancel. You’ll see the localized store price before purchase.", color = InkMuted, style = MaterialTheme.typography.labelSmall)
                }
                message?.let { Text(it, color = if (offering == null) ErrorRed else InkMuted, style = MaterialTheme.typography.bodySmall) }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    TextButton(onClick = { onOpenExternalUrl?.invoke("https://triptandem.app/terms") }) { Text("Terms", color = Coral) }
                    TextButton(onClick = { onOpenExternalUrl?.invoke("https://triptandem.app/privacy") }) { Text("Privacy", color = Coral) }
                    TextButton(onClick = { onManageSubscription?.invoke() }) { Text("Manage", color = Coral) }
                }
            }
        },
        confirmButton = {
            Button(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                enabled = coordinator != null && selectedPackage != null && purchaseState == OrganizerProPurchaseState.Ready,
                onClick = {
                    val purchases = coordinator ?: return@Button
                    purchaseState = OrganizerProPurchaseState.Purchasing
                    val packageType = selected.name.lowercase()
                    analytics.logEvent(TripTandemAnalytics.Events.PURCHASE_STARTED, mapOf("package_type" to packageType))
                    scope.launch {
                        when (val result = purchases.purchase(selected)) {
                            is DataResult.Success -> {
                                purchaseState = if (result.value.isActive) OrganizerProPurchaseState.Active else OrganizerProPurchaseState.Ready
                                onStatusChanged(result.value)
                                analytics.logEvent(
                                    TripTandemAnalytics.Events.PURCHASE_COMPLETED,
                                    mapOf(
                                        "package_type" to packageType,
                                        "trial" to when {
                                            selectedPackage?.introductoryOffer == null -> "not_available"
                                            selectedPackage.introductoryOffer.isFreeTrial -> "eligible_free_trial"
                                            else -> "eligible_intro_offer"
                                        },
                                        "localized_price_bucket" to priceBucket(selectedPackage?.amountMicros),
                                        "active" to result.value.isActive.toString(),
                                    ),
                                )
                                if (result.value.isActive) onDismiss()
                            }
                            is DataResult.Failure -> {
                                purchaseState = result.error.toOrganizerProPurchaseState()
                                message = errorMessage(result.error)
                                when (result.error) {
                                    TripTandemError.PurchaseCancelled -> analytics.logEvent(TripTandemAnalytics.Events.PURCHASE_CANCELLED, mapOf("package_type" to packageType))
                                    else -> analytics.logEvent(TripTandemAnalytics.Events.PURCHASE_FAILED, mapOf("package_type" to packageType, "error_class" to purchaseFailureClass(result.error)))
                                }
                            }
                        }
                    }
                },
            ) { Text(if (busy) "Working…" else "Get Organizer Pro", color = Color.White) }
        },
        dismissButton = {
            Row {
                TextButton(enabled = coordinator != null && !busy, onClick = {
                    val purchases = coordinator ?: return@TextButton
                    purchaseState = OrganizerProPurchaseState.LoadingProducts
                    analytics.logEvent(TripTandemAnalytics.Events.RESTORE_STARTED)
                    scope.launch {
                        when (val result = purchases.restorePurchases()) {
                            is DataResult.Success -> {
                                purchaseState = if (result.value.isActive) OrganizerProPurchaseState.Active else OrganizerProPurchaseState.Ready
                                onStatusChanged(result.value)
                                analytics.logEvent(TripTandemAnalytics.Events.RESTORE_COMPLETED, mapOf("result_class" to if (result.value.isActive) "active" else "not_active", "active" to result.value.isActive.toString()))
                                if (result.value.isActive) onDismiss() else message = "No active Organizer Pro entitlement was found."
                            }
                            is DataResult.Failure -> {
                                purchaseState = result.error.toOrganizerProPurchaseState()
                                message = errorMessage(result.error)
                                analytics.logEvent(
                                    TripTandemAnalytics.Events.RESTORE_COMPLETED,
                                    // A failed restore cannot grant an active
                                    // entitlement. Keep the documented
                                    // result/active shape identical across
                                    // success and failure events without
                                    // exposing store error details.
                                    mapOf(
                                        "result_class" to restoreResultClass(result.error),
                                        "active" to "false",
                                    ),
                                )
                            }
                        }
                    }
                }) { Text("Restore") }
                TextButton(enabled = !busy, onClick = onDismiss) { Text("Not now") }
            }
        },
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AddItineraryDialog(
    initial: ItineraryItem? = null,
    dayDate: String,
    destinationTimezone: String,
    dayNumber: Int = 1,
    destination: String = "",
    existingItems: List<ItineraryItem> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (CreateItineraryItemInput) -> Unit,
) {
    var title by remember(initial) { mutableStateOf(initial?.title.orEmpty()) }
    var time by remember(initial) { mutableStateOf(initial?.startTimeLabel ?: "09:00") }
    var flexibleTime by remember(initial) { mutableStateOf(initial?.flexibleTime ?: false) }
    var place by remember(initial) { mutableStateOf(initial?.place.orEmpty()) }
    var note by remember(initial) { mutableStateOf(initial?.note.orEmpty()) }
    var type by remember(initial) { mutableStateOf(initial?.type ?: ItineraryItemType.Activity) }
    var category by remember(initial) { mutableStateOf(when (initial?.type) { ItineraryItemType.Meal -> "Meal"; ItineraryItemType.Transport -> "Transit"; ItineraryItemType.Lodging -> "Stay"; ItineraryItemType.Note -> "Note"; else -> "Sightsee" }) }
    var duration by remember(initial) { mutableIntStateOf(initial?.durationMinutes ?: 60) }
    var status by remember(initial) { mutableStateOf(initial?.status ?: ItineraryItemStatus.Planned) }
    var visibility by remember(initial) { mutableStateOf(initial?.visibility ?: ItineraryVisibility.Members) }
    var showTimePicker by remember { mutableStateOf(false) }
    var durationMenu by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(initial != null) }
    val fixedEpoch = if (flexibleTime) null else localDateTimeToEpochMillis(dayDate, time, destinationTimezone)
    val start = parseClockMinutes(time)
    val conflicts = if (flexibleTime || status == ItineraryItemStatus.Cancelled) emptyList() else scheduleConflicts(dayDate, start, duration, existingItems, initial?.id)
    val endsNextDay = !flexibleTime && start != null && start + duration > 1440
    val available = (0 until 1440 step 30).filter { it + duration <= 1440 && scheduleConflicts(dayDate, it, duration, existingItems, initial?.id).isEmpty() }
    val suggestions = (available.filter { it >= 8 * 60 } + available.filter { it < 8 * 60 }).take(6)
    val safeSummaryAllowed = type != ItineraryItemType.Lodging && type != ItineraryItemType.Note
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(containerColor = Cream, topBar = {
            Column(Modifier.statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Text("←", fontSize = 26.sp) }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (initial == null) "Add to Day $dayNumber" else "Edit activity", style = MaterialTheme.typography.titleLarge)
                        Text("${weekday(dayDate)}, ${shortDate(dayDate)} · $destination", color = InkMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = onDismiss) { Text("×", fontSize = 26.sp, color = InkMuted) }
                }
                HorizontalDivider(color = CreamBorder)
            }
        }, bottomBar = {
            Column(Modifier.background(Color.White).navigationBarsPadding()) {
                HorizontalDivider(color = CreamBorder)
                Button(enabled = title.isNotBlank() && (flexibleTime || fixedEpoch != null) && conflicts.isEmpty() && !endsNextDay,
                    onClick = { onSave(CreateItineraryItemInput(type = type, title = title.trim(), startTimeEpochMillis = fixedEpoch,
                        startTimeLabel = time.ifBlank { null }, durationMinutes = duration, place = place.ifBlank { null }, note = note.ifBlank { null },
                        status = status, visibility = visibility, position = initial?.position ?: 0, dayDate = dayDate, flexibleTime = flexibleTime)) },
                    modifier = Modifier.fillMaxWidth().padding(20.dp).height(56.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = CoralHero)) {
                    Text(if (initial == null) "Add to itinerary  →" else "Save changes", style = MaterialTheme.typography.titleMedium)
                }
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                Text("Activity type", Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.titleMedium)
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("Coffee" to ItineraryItemType.Meal, "Meal" to ItineraryItemType.Meal, "Sightsee" to ItineraryItemType.Activity, "Transit" to ItineraryItemType.Transport, "Stay" to ItineraryItemType.Lodging, "Note" to ItineraryItemType.Note)) { (label, option) ->
                        Surface(Modifier.size(78.dp).clickable { category = label; type = option; if (!safeSummaryType(option)) visibility = ItineraryVisibility.Members }, shape = RoundedCornerShape(18.dp), color = if (category == label) CoralHero else Color.White, border = BorderStroke(1.dp, CreamBorder)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                TripIcon(if (option == ItineraryItemType.Transport) TripIconKind.Map else if (option == ItineraryItemType.Lodging) TripIconKind.Trips else TripIconKind.Discover, null, tint = if (category == label) Color.White else Ink)
                                Spacer(Modifier.height(6.dp)); Text(label, color = if (category == label) Color.White else Ink, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    TripTextField(title, { if (it.length <= 160) title = it }, label = { Text("What are you doing?") }, singleLine = true)
                    TripTextField(place, { if (it.length <= 200) place = it }, label = { Text("Where? (optional)") }, singleLine = true)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("When?", style = MaterialTheme.typography.titleMedium)
                        if (!flexibleTime && start != null) Text("$time – ${clockLabel(start + duration)}", color = Sage, style = MaterialTheme.typography.labelLarge)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("START", color = InkMuted, style = MaterialTheme.typography.labelSmall)
                            Surface(Modifier.fillMaxWidth().clickable { showTimePicker = true }, color = Color.White, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, CreamBorder)) { Text(if (flexibleTime) "Flexible" else time, Modifier.padding(18.dp), style = MaterialTheme.typography.titleLarge) }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("DURATION", color = InkMuted, style = MaterialTheme.typography.labelSmall)
                            Box {
                                Surface(Modifier.fillMaxWidth().clickable { durationMenu = true }, color = Color.White, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, CreamBorder)) { Text("${durationLabel(duration)}  ⌄", Modifier.padding(18.dp), style = MaterialTheme.typography.titleMedium) }
                                DropdownMenu(expanded = durationMenu, onDismissRequest = { durationMenu = false }) {
                                    (listOf(0, 15, 30, 45, 60, 90, 120, 180, 240, 360, 480, 720, 1440) + duration).distinct().sorted().forEach { value ->
                                        DropdownMenuItem(text = { Text(durationLabel(value)) }, onClick = { duration = value; durationMenu = false })
                                    }
                                }
                            }
                        }
                    }
                    if (conflicts.isNotEmpty()) Text("This overlaps ${conflicts.joinToString { it.title }}. Choose an available time.", color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                    if (endsNextDay) Text("This activity ends after this day. Choose an earlier start or a shorter duration.", color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                    if (!flexibleTime) {
                        Text("Available starts · $destinationTimezone", color = Sage, style = MaterialTheme.typography.labelSmall)
                        if (suggestions.isEmpty()) Text("No open slot fits this duration. Try a shorter activity.", color = InkMuted)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { suggestions.forEach { minute -> ChoiceChip(clockLabel(minute), start == minute, { time = clockLabel(minute) }) } }
                    }
                    TripTextField(note, { if (it.length <= 1000) note = it }, label = { Text("Notes (optional)") }, minLines = 3, maxLines = 5)
                    TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Hide planning options" else "Planning options · status & visibility") }
                    if (advanced) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(flexibleTime, { flexibleTime = it; time = if (it) "Morning" else "09:00" }); Text("Time is flexible") }
                        if (flexibleTime) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Morning", "Afternoon", "Evening", "Any time").forEach { label -> ChoiceChip(label, time == label, { time = label }) } }
                        Text("Plan status", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { ItineraryItemStatus.entries.forEach { option -> ChoiceChip(option.label, status == option, { status = option }) } }
                        Text("Who can see this item?", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ChoiceChip("Members", visibility == ItineraryVisibility.Members, { visibility = ItineraryVisibility.Members })
                            if (safeSummaryAllowed) ChoiceChip("Trip summary", visibility == ItineraryVisibility.TripSummary, { visibility = ItineraryVisibility.TripSummary })
                        }
                        Text("Keep lodging and private meeting details for members.", color = InkMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (showTimePicker) {
            val picker = androidx.compose.material3.rememberTimePickerState(initialHour = (start ?: 540) / 60, initialMinute = (start ?: 540) % 60, is24Hour = true)
            AlertDialog(onDismissRequest = { showTimePicker = false }, containerColor = Cream, title = { Text("Start time") },
                text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    androidx.compose.material3.TimePicker(state = picker)
                    Text("Destination time · $destinationTimezone", color = InkMuted, style = MaterialTheme.typography.labelSmall)
                    if (scheduleConflicts(dayDate, picker.hour * 60 + picker.minute, duration, existingItems, initial?.id).isNotEmpty()) Text("This time is occupied. Choose another start.", color = ErrorRed)
                } },
                confirmButton = { TextButton(onClick = { time = clockLabel(picker.hour * 60 + picker.minute); flexibleTime = false; showTimePicker = false }) { Text("Use time") } },
                dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } })
        }
    }
}
private fun safeSummaryType(type: ItineraryItemType) = type != ItineraryItemType.Lodging && type != ItineraryItemType.Note
internal fun clockLabel(minutes: Int): String = "${(minutes / 60).toString().padStart(2, '0')}:${(minutes % 60).toString().padStart(2, '0')}"
private fun durationLabel(minutes: Int): String = when { minutes == 0 -> "No duration"; minutes % 60 == 0 -> "${minutes / 60} ${if (minutes == 60) "hour" else "hours"}"; else -> "$minutes min" }
fun scheduleConflicts(date: String, start: Int?, duration: Int, items: List<ItineraryItem>, excludingId: String? = null): List<ItineraryItem> {
    if (start == null) return emptyList()
    return items.filter { item ->
        val other = parseClockMinutes(item.startTimeLabel)
        item.deletedAtEpochMillis == null && item.id != excludingId && item.dayDate == date && !item.flexibleTime && item.status != ItineraryItemStatus.Cancelled && other != null &&
            start < other + item.durationMinutes.coerceAtLeast(1) && other < start + duration.coerceAtLeast(1)
    }
}

@Composable
private fun EditTripDialog(
    trip: TripRecord,
    onDismiss: () -> Unit,
    onSave: (UpdateTripInput) -> Unit,
    onDelete: () -> Unit,
) {
    var title by remember(trip) { mutableStateOf(trip.title) }
    var destination by remember(trip) { mutableStateOf(trip.destination) }
    var startDate by remember(trip) { mutableStateOf(trip.startDate) }
    var endDate by remember(trip) { mutableStateOf(trip.endDate) }
    var timezone by remember(trip) { mutableStateOf(trip.destinationTimezone) }
    var capacity by remember(trip) { mutableIntStateOf(trip.capacity.coerceIn(2, 12)) }
    var visibility by remember(trip) { mutableStateOf(trip.visibility) }
    var status by remember(trip) { mutableStateOf(trip.status) }
    var datesFlexible by remember(trip) { mutableStateOf(trip.datesFlexible) }
    var currency by remember(trip) { mutableStateOf(trip.currency.orEmpty()) }
    var pace by remember(trip) { mutableStateOf(trip.pace) }
    var budgetBand by remember(trip) { mutableStateOf(trip.budgetBand) }
    var interests by remember(trip) { mutableStateOf(trip.interests) }
    var coverColor by remember(trip) { mutableStateOf(trip.coverColor ?: "#E8704A") }
    var expectationNote by remember(trip) { mutableStateOf(trip.expectationNote.orEmpty()) }

    TripFormDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit trip") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                item { TripTextField(title, { if (it.length <= 120) title = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Trip title") }, singleLine = true) }
                item { TripTextField(destination, { if (it.length <= 160) destination = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Destination") }, singleLine = true) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        TripDateField("Start", startDate, { startDate = it }, modifier = Modifier.weight(1f))
                        TripDateField("End", endDate, { endDate = it }, modifier = Modifier.weight(1f))
                    }
                }
                item {
                    TripTextField(timezone, { if (it.length <= 80) timezone = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Destination timezone") }, singleLine = true)
                    if (timezone != trip.destinationTimezone) {
                        Text("Itinerary clock labels stay attached to the destination. Review timed items after changing this timezone.", color = Coral, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Checkbox(checked = datesFlexible, onCheckedChange = { datesFlexible = it })
                        Text("Dates are flexible", color = InkSoft)
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        TripTextField(currency, { if (it.length <= 3) currency = it.uppercase() }, modifier = Modifier.weight(1f), label = { Text("Currency") }, placeholder = { Text("USD") }, singleLine = true)
                        TripTextField(expectationNote, { if (it.length <= 500) expectationNote = it }, modifier = Modifier.weight(2f), label = { Text("Group expectations") }, minLines = 1, maxLines = 2)
                    }
                }
                item {
                    Text("Travel style", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                        TripPace.entries.forEach { option -> ChoiceChip(option.label, pace == option, { pace = option }) }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                        BudgetBand.entries.forEach { option -> ChoiceChip(option.label, budgetBand == option, { budgetBand = option }) }
                    }
                }
                item {
                    Text("Trip interests", style = MaterialTheme.typography.labelLarge)
                    TripInterestVocabulary.values.chunked(3).forEach { row ->
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                            row.forEach { option ->
                                ChoiceChip(option, option in interests, { interests = if (option in interests) interests - option else (interests + option).distinct().take(12) })
                            }
                        }
                    }
                }
                item {
                    Text("Cover accent", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
                        listOf("#E8704A" to CoralHero, "#6B9E7F" to SageAccent, "#E9D7C6" to Color(0xFFE9D7C6)).forEach { (value, color) ->
                            Box(Modifier.size(34.dp).clip(CircleShape).background(color).border(if (coverColor == value) 3.dp else 1.dp, if (coverColor == value) Ink else CreamBorder, CircleShape).clickable { coverColor = value })
                        }
                    }
                }
                item {
                    Text("Privacy", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                        ChoiceChip("Private", visibility == TripVisibility.Private, { visibility = TripVisibility.Private })
                        ChoiceChip("Unlisted link", visibility == TripVisibility.Unlisted, { visibility = TripVisibility.Unlisted })
                    }
                }
                item {
                    Text("Status", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                        listOf(TripStatus.Planning, TripStatus.Confirmed, TripStatus.Completed, TripStatus.Cancelled, TripStatus.Archived).forEach { option ->
                            ChoiceChip(option.label(), status == option, { status = option })
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column { Text("Capacity", style = MaterialTheme.typography.labelLarge); Text("${capacity} travelers including you", color = InkMuted, style = MaterialTheme.typography.bodySmall) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                enabled = capacity > 2,
                                onClick = { capacity-- },
                                modifier = Modifier.semantics { contentDescription = "Decrease group capacity" },
                            ) { Text("−", style = MaterialTheme.typography.titleLarge) }
                            Text(capacity.toString(), style = MaterialTheme.typography.titleMedium)
                            IconButton(
                                enabled = capacity < 12,
                                onClick = { capacity++ },
                                modifier = Modifier.semantics { contentDescription = "Increase group capacity" },
                            ) { Text("+", style = MaterialTheme.typography.titleLarge) }
                        }
                    }
                    if (capacity < trip.activeMemberCount) {
                        Text("This trip is full at its current membership. Lowering capacity will not remove anyone, but new joins stay blocked until capacity increases.", color = Coral, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    UpdateTripInput(
                        title = title,
                        destination = destination,
                        startDate = startDate,
                        endDate = endDate,
                        destinationTimezone = timezone,
                        datesFlexible = datesFlexible,
                        visibility = visibility,
                        capacity = capacity,
                        status = status,
                        currency = currency.trim().uppercase().takeIf { it.length == 3 },
                        budgetBand = budgetBand,
                        pace = pace,
                        expectationNote = expectationNote.ifBlank { null },
                        expectedRevision = trip.revision,
                        interests = interests,
                        coverColor = coverColor,
                    ),
                )
            }) { Text("Save", color = Coral) }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onDelete) { Text("Delete", color = ErrorRed) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun InviteLandingScreen(
    pendingInvite: PendingInvite,
    repositories: TripTandemRepositories,
    analytics: TripTandemAnalytics,
    onAccepted: (TripRecord) -> Unit,
    onDecline: () -> Unit,
) {
    var invite by remember(pendingInvite) { mutableStateOf<InviteLink?>(null) }
    var alreadyMember by remember(pendingInvite) { mutableStateOf(false) }
    var loading by remember(pendingInvite) { mutableStateOf(true) }
    var accepting by remember { mutableStateOf(false) }
    var openingExisting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<TripTandemError?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(pendingInvite) {
        loading = true
        error = null
        // An existing member should be able to follow an old or revoked
        // invite back to the trip without being asked to join a second time.
        when (val membership = repositories.members.getCurrentMember(pendingInvite.tripId)) {
            is DataResult.Success -> if (membership.value?.status == MembershipStatus.Active) {
                alreadyMember = true
                loading = false
                return@LaunchedEffect
            }
            is DataResult.Failure -> Unit
        }
        for (attempt in 0 until 8) {
            when (val result = repositories.invites.resolveInvite(pendingInvite.tripId, pendingInvite.shareToken)) {
                is DataResult.Success -> {
                    invite = result.value
                    break
                }
                is DataResult.Failure -> {
                    if (result.error == TripTandemError.Unauthenticated && attempt < 7) {
                        delay(250)
                    } else {
                        error = result.error
                        analytics.logEvent(TripTandemAnalytics.Events.INVITE_FAILED, mapOf("reason_class" to "resolve_failed"))
                        break
                    }
                }
            }
        }
        loading = false
    }

    Scaffold(containerColor = Cream) { innerPadding ->
        Column(
            Modifier.fillMaxSize().padding(innerPadding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TextButton(onClick = onDecline) { Text("Not now", color = Coral) }
            TripTandemMark()
            Text("You’re invited to a private trip", style = MaterialTheme.typography.headlineSmall)
            Text("Review the access role before joining. Private itinerary details stay hidden until your membership is accepted.", color = InkMuted)
            when {
                loading -> LoadingCard("Checking this invite…")
                error != null -> ErrorBanner(error!!, onRetry = { loading = true; scope.launch { when (val result = repositories.invites.resolveInvite(pendingInvite.tripId, pendingInvite.shareToken)) { is DataResult.Success -> { invite = result.value; error = null }; is DataResult.Failure -> error = result.error }; loading = false } })
                alreadyMember -> Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = SageTint), border = BorderStroke(1.dp, CreamBorder)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatusChip("Already a member", SageTint, Sage)
                        Text("You’re already part of this trip.", style = MaterialTheme.typography.titleLarge)
                        Text("This invite will not change your role. Open the latest shared itinerary instead.", color = Color(0xFF203B2B), style = MaterialTheme.typography.bodySmall)
                        Button(
                            onClick = {
                                openingExisting = true
                                scope.launch {
                                    when (val trip = repositories.trips.getTrip(pendingInvite.tripId)) {
                                        is DataResult.Success -> onAccepted(trip.value)
                                        is DataResult.Failure -> error = trip.error
                                    }
                                    openingExisting = false
                                }
                            },
                            enabled = !openingExisting,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Sage),
                            shape = RoundedCornerShape(14.dp),
                        ) { if (openingExisting) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Open trip") }
                    }
                }
                invite != null -> Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, CreamBorder)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatusChip("Secure invite", CoralTint, Coral)
                        Text(invite!!.tripTitle ?: "Trip access", style = MaterialTheme.typography.titleLarge)
                        invite!!.destination?.takeIf { it.isNotBlank() }?.let { Text(it, color = InkMuted, style = MaterialTheme.typography.bodyMedium) }
                        if (!invite!!.startDate.isNullOrBlank() && !invite!!.endDate.isNullOrBlank()) {
                            Text("${invite!!.startDate} – ${invite!!.endDate}", color = InkMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        Text("Invited by ${invite!!.inviterDisplayName ?: "the trip host"}", color = InkSoft, style = MaterialTheme.typography.bodySmall)
                        Text("Role offered: ${invite!!.role.label()}", color = Sage, style = MaterialTheme.typography.titleMedium)
                        Text("Expires ${inviteExpiryLabel(invite!!)} · ${invite!!.uses}/${invite!!.maxUses} uses", color = InkMuted, style = MaterialTheme.typography.bodySmall)
                        Text("The invite token is never shown in analytics. Exact itinerary details stay hidden until you join.", color = InkMuted, style = MaterialTheme.typography.bodySmall)
                        Button(
                            onClick = {
                                accepting = true
                                scope.launch {
                                    when (val accepted = repositories.invites.acceptInvite(pendingInvite.tripId, invite!!.id, invite!!.role)) {
                                        is DataResult.Success -> when (val trip = repositories.trips.getTrip(pendingInvite.tripId)) {
                                            is DataResult.Success -> { analytics.logEvent(TripTandemAnalytics.Events.INVITE_ACCEPTED, mapOf("role" to invite!!.role.wireValue)); onAccepted(trip.value) }
                                            is DataResult.Failure -> error = trip.error
                                        }
                                        is DataResult.Failure -> { error = accepted.error; analytics.logEvent(TripTandemAnalytics.Events.INVITE_FAILED, mapOf("reason_class" to "accept_failed")) }
                                    }
                                    accepting = false
                                }
                            },
                            enabled = !accepting,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Coral),
                            shape = RoundedCornerShape(14.dp),
                        ) { if (accepting) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Join this trip") }
                        OutlinedButton(onClick = onDecline, modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, CreamBorder)) { Text("Decline") }
                    }
                }
            }
        }
    }
}

@Composable
private fun MembersScreen(
    trip: TripRecord,
    repositories: TripTandemRepositories,
    analytics: TripTandemAnalytics,
    onBack: () -> Unit,
    onShareInvite: ((String) -> Unit)?,
    embedded: Boolean = false,
) {
    var showInvites by remember { mutableStateOf(false) }
    var showToken by remember { mutableStateOf(false) }
    var members by remember(trip.id) { mutableStateOf<List<TripMember>>(emptyList()) }
    var currentMember by remember(trip.id) { mutableStateOf<TripMember?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<TripTandemError?>(null) }
    var inviteRole by remember { mutableStateOf(TripMemberRole.Viewer) }
    var inviteExpiryHours by remember { mutableIntStateOf(24 * 7) }
    var inviteCustomExpiryText by remember { mutableStateOf("") }
    var inviteMaxUsesText by remember { mutableStateOf("1") }
    var invite by remember { mutableStateOf<InviteLink?>(null) }
    var creatingInvite by remember { mutableStateOf(false) }
    var tokenToAccept by remember { mutableStateOf("") }
    var accepting by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    var transferTarget by remember { mutableStateOf<TripMember?>(null) }
    val scope = rememberCoroutineScope()
    val canManage = currentMember?.role == TripMemberRole.Owner && currentMember?.status == MembershipStatus.Active
    val customExpiryHours = inviteCustomExpiryText.toIntOrNull()
    val customExpiryInvalid = inviteCustomExpiryText.isNotBlank() && customExpiryHours !in 1..(24 * 30)
    val effectiveInviteExpiryHours = customExpiryHours?.takeIf { it in 1..(24 * 30) } ?: inviteExpiryHours

    fun loadMembers() {
        loading = true
        error = null
        scope.launch {
            when (val result = repositories.members.listMembers(trip.id)) {
                is DataResult.Success -> members = result.value
                is DataResult.Failure -> error = result.error
            }
            when (val result = repositories.members.getCurrentMember(trip.id)) {
                is DataResult.Success -> currentMember = result.value
                is DataResult.Failure -> if (error == null) error = result.error
            }
            loading = false
        }
    }
    LaunchedEffect(trip.id) {
        loadMembers()
    }

    Scaffold(containerColor = Cream) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp)) {
                if (!embedded) TextButton(onClick = onBack) { Text("Back", color = Coral) }
                Text("Members", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.width(12.dp))
                StatusChip("${members.size} people", CoralTint, Coral)
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item {
                    Text("Owner and editors can update the plan. Viewers can read it.", color = InkMuted, style = MaterialTheme.typography.bodySmall)
                }
                if (loading) item { LoadingCard("Loading members…") }
                else if (error != null) item { ErrorBanner(error!!, ::loadMembers) }
                else if (members.isEmpty()) item { EmptyState("No members yet", "You are the owner. Create an invite when you are ready.") }
                else items(members.sortedBy { it.role.ordinal }, key = { it.userId }) { member ->
                    if (members.firstOrNull { it.role == member.role }?.userId == member.userId) {
                        Text("${member.role.label().uppercase()} · ${members.count { it.role == member.role }}", color = InkMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp, bottom = 10.dp))
                    }
                    MemberRow(
                        member = member,
                        canManage = canManage,
                        onRoleChange = { role ->
                            scope.launch {
                                when (val result = repositories.members.updateRole(trip.id, member.userId, role)) {
                                    is DataResult.Success -> {
                                        loadMembers()
                                        analytics.logEvent(TripTandemAnalytics.Events.MEMBER_ROLE_CHANGED, mapOf("from" to member.role.wireValue, "to" to role.wireValue))
                                    }
                                    is DataResult.Failure -> error = result.error
                                }
                            }
                        },
                        onRemove = {
                            scope.launch {
                                when (val result = repositories.members.removeMember(trip.id, member.userId)) {
                                    is DataResult.Success -> { loadMembers(); analytics.logEvent(TripTandemAnalytics.Events.MEMBER_REMOVED) }
                                    is DataResult.Failure -> error = result.error
                                }
                            }
                        },
                        onTransfer = { transferTarget = member },
                    )
                    if (member.userId != currentMember?.userId) MemberSafetyActions(repositories.community, member.userId)
                }
                item { Button(onClick = { showInvites = !showInvites }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = CoralHero)) { Text(if (showInvites) "Hide invitations" else "Invite more people", style = MaterialTheme.typography.titleMedium) } }
                if (showInvites) {
                item {
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, CreamBorder)) {
                        if (canManage) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Create an invite link", style = MaterialTheme.typography.titleMedium)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ChoiceChip("Viewer", inviteRole == TripMemberRole.Viewer, { inviteRole = TripMemberRole.Viewer })
                                    ChoiceChip("Editor", inviteRole == TripMemberRole.Editor, { inviteRole = TripMemberRole.Editor })
                                }
                                Text("Link expiry", style = MaterialTheme.typography.labelLarge)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(24 to "24 hours", (24 * 7) to "7 days", (24 * 30) to "30 days").forEach { (hours, label) ->
                                        ChoiceChip(label, inviteExpiryHours == hours, { inviteExpiryHours = hours })
                                    }
                                }
                                TripTextField(
                                    value = inviteCustomExpiryText,
                                    onValueChange = { value ->
                                        if (value.length <= 3 && value.all(Char::isDigit)) inviteCustomExpiryText = value
                                    },
                                    label = { Text("Custom expiry (hours, optional)") },
                                    supportingText = { Text("1–720 hours. This overrides the preset when valid.") },
                                    singleLine = true,
                                )
                                TripTextField(
                                    value = inviteMaxUsesText,
                                    onValueChange = { if (it.length <= 2 && it.all(Char::isDigit)) inviteMaxUsesText = it },
                                    label = { Text("Maximum uses") },
                                    supportingText = { Text("1–20 people can use this link.") },
                                    singleLine = true,
                                )
                                Button(
                                    onClick = {
                                        creatingInvite = true
                                        scope.launch {
                                            val maxUses = inviteMaxUsesText.toIntOrNull() ?: 0
                                            when {
                                                maxUses !in 1..20 -> error = TripTandemError.Validation("maxUses")
                                                else -> when (val result = repositories.invites.createInvite(trip.id, CreateInviteInput(inviteRole, effectiveInviteExpiryHours, maxUses))) {
                                                is DataResult.Success -> { invite = result.value; analytics.logEvent(TripTandemAnalytics.Events.INVITE_CREATED, mapOf("role" to inviteRole.wireValue, "expiry_bucket" to inviteExpiryBucket(effectiveInviteExpiryHours))) }
                                                is DataResult.Failure -> { error = result.error; analytics.logEvent(TripTandemAnalytics.Events.INVITE_FAILED, mapOf("reason_class" to "create_failed")) }
                                                }
                                            }
                                            creatingInvite = false
                                        }
                                    },
                                    enabled = !creatingInvite && !customExpiryInvalid && effectiveInviteExpiryHours in 1..(24 * 30) && (inviteMaxUsesText.toIntOrNull() ?: 0) in 1..20,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Coral),
                                ) { if (creatingInvite) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Generate secure link") }
                                invite?.let { generated ->
                                    val shareUrl = "triptandem://join/${generated.tripId}?token=${generated.shareToken}"
                                    HorizontalDivider(color = CreamBorder)
                                    Text("Share this link once. It is never stored in analytics.", color = InkMuted, style = MaterialTheme.typography.bodySmall)
                                    Text(shareUrl, color = InkSoft, style = MaterialTheme.typography.labelSmall)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = { onShareInvite?.invoke(shareUrl); analytics.logEvent(TripTandemAnalytics.Events.INVITE_SHARE_OPENED, mapOf("channel_category" to "system_share")) }, colors = ButtonDefaults.buttonColors(containerColor = Sage)) { Text("Share link") }
                                        OutlinedButton(onClick = {
                                            scope.launch {
                                                when (val revoked = repositories.invites.revokeInvite(trip.id, generated.id)) {
                                                    is DataResult.Success -> invite = null
                                                    is DataResult.Failure -> error = revoked.error
                                                }
                                            }
                                        }) { Text("Revoke") }
                                    }
                                }
                            }
                        } else {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Invite links are owner-only", style = MaterialTheme.typography.titleMedium)
                                Text("You can collaborate on the plan with your current ${currentMember?.role?.label()?.lowercase() ?: "member"} access. Ask the owner to invite someone else.", color = InkMuted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                }
                if (currentMember?.status == MembershipStatus.Active && currentMember?.role != TripMemberRole.Owner) {
                    item {
                        OutlinedButton(
                            onClick = { leaving = true },
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, CreamBorder),
                        ) { Text("Leave this trip", color = Coral) }
                    }
                }
                item { TextButton(onClick = { showToken = !showToken }) { Text("Already have an invite token?") } }
                if (showToken) item {
                    HorizontalDivider(color = CreamBorder)
                    TripTextField(tokenToAccept, { tokenToAccept = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Paste token") }, singleLine = true)
                    Button(enabled = tokenToAccept.isNotBlank() && !accepting, onClick = {
                        accepting = true
                        scope.launch {
                            when (val resolved = repositories.invites.resolveInvite(trip.id, tokenToAccept.trim())) {
                                is DataResult.Success -> when (val accepted = repositories.invites.acceptInvite(trip.id, resolved.value.id, resolved.value.role)) {
                                    is DataResult.Success -> { analytics.logEvent(TripTandemAnalytics.Events.INVITE_ACCEPTED, mapOf("role" to resolved.value.role.wireValue)); loadMembers() }
                                    is DataResult.Failure -> { error = accepted.error; analytics.logEvent(TripTandemAnalytics.Events.INVITE_FAILED, mapOf("reason_class" to "accept_failed")) }
                                }
                                is DataResult.Failure -> { error = resolved.error; analytics.logEvent(TripTandemAnalytics.Events.INVITE_FAILED, mapOf("reason_class" to "resolve_failed")) }
                            }
                            accepting = false
                        }
                    }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Sage)) { Text("Accept invite") }
                }
            }
        }
    }

    if (leaving) {
        AlertDialog(
            onDismissRequest = { leaving = false },
            title = { Text("Leave this trip?") },
            text = { Text("You will lose access to the private itinerary and member updates. You can rejoin only with a new invite.") },
            confirmButton = {
                TextButton(onClick = {
                    leaving = false
                    scope.launch {
                        when (val result = repositories.members.leaveTrip(trip.id)) {
                            is DataResult.Success -> { analytics.logEvent(TripTandemAnalytics.Events.MEMBER_LEFT); onBack() }
                            is DataResult.Failure -> error = result.error
                        }
                    }
                }) { Text("Leave trip", color = ErrorRed) }
            },
            dismissButton = { TextButton(onClick = { leaving = false }) { Text("Stay") } },
        )
    }
    transferTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { transferTarget = null },
            title = { Text("Transfer ownership?") },
            text = {
                Text(
                    "${target.displayName} will become the owner. Organizer limits move with the new owner: on Free, new Pro-only actions such as additional active trips, trips over 6 members, and extra AI generation may be gated. This trip and its current members stay available, and you remain an editor."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    transferTarget = null
                    scope.launch {
                        when (val result = repositories.members.transferOwnership(trip.id, target.userId)) {
                            is DataResult.Success -> { analytics.logEvent(TripTandemAnalytics.Events.OWNERSHIP_TRANSFERRED); loadMembers() }
                            is DataResult.Failure -> error = result.error
                        }
                    }
                }) { Text("Transfer", color = Coral) }
            },
            dismissButton = { TextButton(onClick = { transferTarget = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun MemberRow(
    member: TripMember,
    canManage: Boolean,
    onRoleChange: (TripMemberRole) -> Unit,
    onRemove: () -> Unit,
    onTransfer: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxWidth(), color = Color.White, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, CreamBorder)) {
    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(52.dp).background(if (member.role == TripMemberRole.Owner) CoralTint else SageTint, CircleShape), contentAlignment = Alignment.Center) { Text(initials(member.displayName), color = if (member.role == TripMemberRole.Owner) Coral else Sage, style = MaterialTheme.typography.titleMedium) }
        Column(Modifier.weight(1f)) {
            Text(member.displayName, style = MaterialTheme.typography.titleMedium)
            Text(if (member.status == MembershipStatus.Active) member.role.label() else "Invite pending", color = InkMuted, style = MaterialTheme.typography.bodySmall)
        }
        if (member.role != TripMemberRole.Owner && canManage && member.status == MembershipStatus.Active) {
            Box {
                TextButton(onClick = { menu = true }) { Text(member.role.label(), color = Sage) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Transfer ownership") }, onClick = { menu = false; onTransfer() })
                    TripMemberRole.entries.filter { it != TripMemberRole.Owner }.forEach { role -> DropdownMenuItem(text = { Text("Make ${role.label().lowercase()}") }, onClick = { menu = false; onRoleChange(role) }) }
                    DropdownMenuItem(text = { Text("Remove", color = ErrorRed) }, onClick = { menu = false; onRemove() })
                }
            }
        } else if (member.role == TripMemberRole.Owner) Text("Owner", color = Coral, style = MaterialTheme.typography.labelMedium)
        else Text(member.role.label(), color = Sage, style = MaterialTheme.typography.labelMedium)
    }
}
}

@Composable
private fun TripCard(trip: TripRecord, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, CreamBorder)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        StatusChip(trip.visibility.label(), CoralTint, Coral)
                        Text(trip.status.label(), color = InkMuted, style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(trip.title, style = MaterialTheme.typography.titleLarge, maxLines = 2)
                    Text(trip.destination, color = InkMuted, style = MaterialTheme.typography.bodyMedium)
                }
                DestinationThumbnail(trip.destination.take(5).uppercase())
            }
            HorizontalDivider(color = CreamBorder)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TripIcon(TripIconKind.Calendar, null, tint = Sage, modifier = Modifier.size(18.dp))
                Text("${trip.startDate} – ${trip.endDate}", color = InkSoft, style = MaterialTheme.typography.bodyMedium)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) { AvatarStack(); Spacer(Modifier.width(4.dp)); Text("${trip.capacity} travelers", color = InkMuted, style = MaterialTheme.typography.bodySmall) }
                Text(
                    when {
                        trip.activeMemberCount >= trip.capacity -> "Full"
                        trip.status == TripStatus.Planning -> "Planning"
                        else -> trip.status.label()
                    },
                    color = if (trip.activeMemberCount >= trip.capacity) Coral else Sage,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            LinearProgressIndicator(progress = { .28f }, modifier = Modifier.fillMaxWidth(), color = Sage, trackColor = SageTint)
            Text("Open itinerary to add the next item", color = InkMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ItineraryItemCard(
    item: ItineraryItem,
    canEdit: Boolean,
    lastEditorLabel: String?,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Int) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(20.dp))
            Box(Modifier.size(28.dp).background(if (item.status == ItineraryItemStatus.Idea) SageTint else CoralTint, CircleShape), contentAlignment = Alignment.Center) { TripIcon(TripIconKind.Discover, null, tint = if (item.status == ItineraryItemStatus.Idea) Sage else Coral, modifier = Modifier.size(16.dp)) }
            Box(Modifier.width(1.dp).height(80.dp).background(CreamBorder))
        }
        Card(Modifier.weight(1f), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, CreamBorder)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${item.startTimeLabel ?: "Any time"} · ${durationLabel(item.durationMinutes)}", Modifier.weight(1f), color = Coral, style = MaterialTheme.typography.labelLarge)
                    StatusChip(item.status.label, if (item.status == ItineraryItemStatus.Idea) CreamMuted else SageTint, if (item.status == ItineraryItemStatus.Idea) InkSoft else Sage)
                }
                Text(item.title, style = MaterialTheme.typography.titleLarge)
                item.place?.let { Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { TripIcon(TripIconKind.MapPin, null, tint = CoralHero, modifier = Modifier.size(16.dp)); Text(it, color = InkMuted, style = MaterialTheme.typography.bodySmall) } }
                item.note?.let { Text(it, color = InkSoft, style = MaterialTheme.typography.bodySmall) }
                if (item.generatedBy == "triptandem_ai") StatusChip("AI draft", CoralTint, Coral)
                if (canEdit) Box(Modifier.align(Alignment.End)) {
                    TextButton(onClick = { menu = true }, contentPadding = PaddingValues(0.dp)) { Text("Options", style = MaterialTheme.typography.labelSmall) }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("Edit activity") }, onClick = { menu = false; onEdit() })
                        DropdownMenuItem(text = { Text("Move earlier") }, enabled = canMoveUp, onClick = { menu = false; onMove(-1) })
                        DropdownMenuItem(text = { Text("Move later") }, enabled = canMoveDown, onClick = { menu = false; onMove(1) })
                        DropdownMenuItem(text = { Text("Remove activity", color = ErrorRed) }, onClick = { menu = false; onDelete() })
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanningTip() {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = SageTint)) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            TripIcon(TripIconKind.Users, null, tint = Sage, modifier = Modifier.size(22.dp))
            Column { Text("Planning together?", color = Sage, style = MaterialTheme.typography.labelLarge); Text("Share an invite link so friends can join as editors or viewers.", color = Color(0xFF203B2B), style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun EmptyState(title: String, body: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, CreamBorder)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(54.dp).background(CoralTint, CircleShape), contentAlignment = Alignment.Center) { TripIcon(TripIconKind.Map, null, tint = Coral, modifier = Modifier.size(26.dp)) }
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(body, color = InkMuted, style = MaterialTheme.typography.bodySmall)
            action?.let { label ->
                OutlinedButton(onClick = { onAction?.invoke() }, border = BorderStroke(1.dp, Coral), colors = ButtonDefaults.outlinedButtonColors(contentColor = Coral)) { Text(label) }
            }
        }
    }
}

@Composable
private fun LoadingCard(label: String) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, CreamBorder)) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { CircularProgressIndicator(color = Coral, modifier = Modifier.size(22.dp), strokeWidth = 2.dp); Text(label, color = InkMuted) }
    }
}

@Composable
private fun ErrorBanner(error: TripTandemError, onRetry: () -> Unit) {
    val message = errorMessage(error)
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CoralTint)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TripIcon(TripIconKind.Alert, "Error", tint = Coral, modifier = Modifier.size(22.dp))
            Text(message, Modifier.weight(1f), color = Color(0xFF5C2416), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onRetry) { Text("Retry", color = Coral) }
        }
    }
}

private fun errorMessage(error: TripTandemError): String = when (error) {
        TripTandemError.Unauthenticated -> "Your session is still starting. Try again in a moment."
        TripTandemError.PermissionDenied -> "You no longer have access to this private journey."
        TripTandemError.NotFound -> "That journey is no longer available."
        TripTandemError.Offline -> "You are offline. We’ll keep the latest readable plan on this device."
        TripTandemError.Conflict -> "Someone updated this plan. Refresh to keep the latest version."
        TripTandemError.ReauthenticationRequired -> "For your security, sign in again before deleting the account."
        TripTandemError.Suspended -> "This account is suspended. Contact support for help."
        TripTandemError.CapacityReached -> "This trip is full. Ask the organizer for another invite."
        TripTandemError.InviteExpired -> "This invite has expired. Ask the organizer for a new link."
        TripTandemError.InviteRevoked -> "This invite was revoked. Ask the organizer for a new link."
        TripTandemError.AuthCancelled -> "Sign-in was cancelled. You can try again whenever you’re ready."
        TripTandemError.AuthConflict -> "That sign-in is already linked to another account. Sign out of this temporary session and sign in to the existing account."
        TripTandemError.PurchaseCancelled -> "Purchase cancelled. Your free plan is still available."
        TripTandemError.PurchasePending -> "Purchase is pending store approval. Pro unlocks when the entitlement becomes active."
        TripTandemError.PurchaseUnavailable -> "Plans are temporarily unavailable. You can keep using the free experience."
        TripTandemError.EntitlementRequired -> "Organizer Pro is needed for this action. Review the plan or continue manually."
        TripTandemError.QuotaExceeded -> "Your fair-use allowance is used. Review Organizer Pro or continue manually."
        is TripTandemError.GenerationFailed -> when (error.failureClass) {
            "safety_blocked" -> "This request isn't available for itinerary planning. You can build the plan manually."
            "feature_disabled" -> "AI itinerary generation is not enabled right now. Your inputs are preserved; you can plan manually."
            "provider_unconfigured" -> "AI itinerary generation is not configured yet. Your inputs are preserved; you can plan manually."
            "no_valid_suggestions" -> "No safe suggestions were returned. Your inputs are preserved; you can plan manually."
            "provider_error" -> "The AI service could not finish this draft. Your inputs are preserved; try again or plan manually."
            "invalid_provider_output" -> "The AI service returned an unusable draft. Your inputs are preserved; try again or plan manually."
            "access_revoked" -> "Your account or editing access changed before this draft finished. Your existing itinerary was not changed."
            "provider_timeout" -> "The AI service took too long to respond. Your inputs are preserved; try again or plan manually."
            "provider_rate_limited" -> "The AI service is busy right now. Your inputs are preserved; try again or plan manually."
            "expired" -> "This draft expired. Start a new generation when you're ready."
            "cancelled" -> "Generation cancelled. Your existing itinerary was not changed."
            "quota_exceeded" -> "Your fair-use allowance is used. Review Organizer Pro or continue manually."
            else -> "The draft could not be generated. Your inputs are preserved; try again or plan manually."
        }
        is TripTandemError.Validation -> when (error.field) {
            "ownership" -> "Transfer ownership or resolve shared trips before deleting this account."
            "title" -> "Enter a trip title with at least 2 characters."
            "destination" -> "Choose a destination."
            "dates", "startDate", "endDate" -> "Choose valid start and end dates, in order, up to 60 days apart."
            "destinationTimezone" -> "Choose a valid destination timezone, such as Asia/Jakarta."
            "currency" -> "Choose a three-letter currency, such as IDR or USD."
            else -> "Some details could not be saved. Review your entries and try again."
        }
        else -> "Something went wrong. You can safely retry."
    }

/** Stable, privacy-safe purchase failure vocabulary for the analytics PRD. */
private fun purchaseFailureClass(error: TripTandemError): String = when (error) {
    TripTandemError.Offline -> "offline"
    TripTandemError.PurchasePending -> "pending"
    TripTandemError.PurchaseCancelled,
    TripTandemError.AuthCancelled,
    -> "cancelled"
    TripTandemError.PurchaseUnavailable -> "unavailable"
    TripTandemError.EntitlementRequired -> "entitlement_required"
    is TripTandemError.Unknown -> "unknown"
    else -> "failed"
}

/** Coarse restore outcome; no store identifiers or localized error text. */
private fun restoreResultClass(error: TripTandemError): String = when (error) {
    TripTandemError.Offline -> "offline"
    TripTandemError.PurchasePending -> "pending"
    TripTandemError.PurchaseCancelled,
    TripTandemError.AuthCancelled,
    -> "cancelled"
    TripTandemError.PurchaseUnavailable -> "unavailable"
    else -> "failed"
}

/** Keeps generation analytics within the documented coarse result vocabulary. */
private fun generationFailureClassForAnalytics(value: String?): String = when (value) {
    "quota_exceeded",
    "provider_unconfigured",
    "feature_disabled",
    "provider_rate_limited",
    "provider_timeout",
    "no_valid_suggestions",
    "invalid_job_input",
    "access_revoked",
    "safety_blocked",
    "invalid_provider_output",
    "provider_error",
    "cancelled",
    "expired",
    "poll",
    "timeout",
    "create_job",
    -> value
    else -> "unknown"
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            // ChoiceChip is used for both single-choice and multi-choice
            // fields. Expose the current state without guessing which group
            // semantics a caller intends; screen readers still announce the
            // visible label and the selected/not-selected state.
            .semantics { stateDescription = if (selected) "Selected" else "Not selected" },
        color = if (selected) CoralTint else Color.White,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, if (selected) CoralHero else CreamBorder),
    ) { Text(label, color = if (selected) Coral else InkSoft, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp)) }
}

@Composable
private fun StatusChip(label: String, background: Color, foreground: Color) {
    Surface(color = background, shape = RoundedCornerShape(50)) { Text(label, color = foreground, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)) }
}

@Composable
private fun FloatingTraveler(initial: String, modifier: Modifier, ring: Color) {
    val photo = when (initial) { "A" -> Res.drawable.traveler_0; "J" -> Res.drawable.traveler_1; else -> Res.drawable.traveler_2 }
    Image(painterResource(photo), contentDescription = null, contentScale = ContentScale.Crop,
        modifier = modifier.size(46.dp).border(2.5.dp, ring, CircleShape).padding(2.5.dp).clip(CircleShape))
}

@Composable
private fun TripTandemMark(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(Res.drawable.triptandem_app_icon),
        contentDescription = "TripTandem",
        contentScale = ContentScale.Crop,
        modifier = modifier.size(42.dp).clip(RoundedCornerShape(13.dp)),
    )
}

@Composable
private fun DestinationThumbnail(label: String) {
    Box(Modifier.size(width = 64.dp, height = 64.dp).background(Color(0xFFE9D7C6), RoundedCornerShape(14.dp)), contentAlignment = Alignment.BottomStart) {
        Column(Modifier.padding(8.dp)) { Text(label, color = Ink, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold); Spacer(Modifier.height(4.dp)); Box(Modifier.size(width = 26.dp, height = 4.dp).background(Coral, RoundedCornerShape(50))) }
    }
}

@Composable
private fun AvatarStack() {
    Row(horizontalArrangement = Arrangement.spacedBy((-9).dp)) {
        listOf(Res.drawable.traveler_3, Res.drawable.traveler_4, Res.drawable.traveler_5).forEach { photo ->
            Image(painterResource(photo), null, contentScale = ContentScale.Crop, modifier = Modifier.size(30.dp).border(2.dp, Cream, CircleShape).padding(2.dp).clip(CircleShape))
        }
    }
}

private enum class TripIconKind { Trips, Map, Discover, Activity, Profile, MapPin, Bell, Plus, ArrowRight, Calendar, Check, Users, Chevron, Trash, LogOut, MoveUp, MoveDown, Alert }

private fun tabIcon(index: Int): TripIconKind = when (index) { 0 -> TripIconKind.Trips; 1 -> TripIconKind.Discover; 2 -> TripIconKind.Bell; else -> TripIconKind.Profile }

@Composable
private fun TripIcon(kind: TripIconKind, contentDescription: String?, tint: Color = LocalContentColor.current, modifier: Modifier = Modifier.size(24.dp)) {
    val canvasModifier = if (contentDescription == null) modifier else modifier.semantics { this.contentDescription = contentDescription }
    Canvas(canvasModifier) {
        val w = size.width; val h = size.height; val min = size.minDimension; val stroke = (min * .095f).coerceAtLeast(1.5f); val line = Stroke(width = stroke, cap = StrokeCap.Round); val c = Offset(w / 2f, h / 2f)
        when (kind) {
            TripIconKind.Trips, TripIconKind.Map -> { drawLine(tint, Offset(w * .18f, h * .24f), Offset(w * .18f, h * .76f), strokeWidth = stroke, cap = StrokeCap.Round); drawLine(tint, Offset(w * .50f, h * .32f), Offset(w * .50f, h * .84f), strokeWidth = stroke, cap = StrokeCap.Round); drawLine(tint, Offset(w * .82f, h * .22f), Offset(w * .82f, h * .70f), strokeWidth = stroke, cap = StrokeCap.Round); drawLine(tint, Offset(w * .18f, h * .24f), Offset(w * .50f, h * .32f), strokeWidth = stroke, cap = StrokeCap.Round); drawLine(tint, Offset(w * .50f, h * .32f), Offset(w * .82f, h * .22f), strokeWidth = stroke, cap = StrokeCap.Round); drawLine(tint, Offset(w * .18f, h * .76f), Offset(w * .50f, h * .84f), strokeWidth = stroke, cap = StrokeCap.Round); drawLine(tint, Offset(w * .50f, h * .84f), Offset(w * .82f, h * .70f), strokeWidth = stroke, cap = StrokeCap.Round) }
            TripIconKind.Discover -> { drawCircle(tint, min * .34f, c, style = line); drawLine(tint, Offset(w * .57f, h * .40f), Offset(w * .42f, h * .60f), strokeWidth = stroke, cap = StrokeCap.Round); drawLine(tint, Offset(w * .42f, h * .60f), Offset(w * .57f, h * .40f), strokeWidth = stroke, cap = StrokeCap.Round) }
            TripIconKind.Activity -> { drawArc(color = tint, startAngle = 205f, sweepAngle = 130f, useCenter = false, topLeft = Offset(w * .23f, h * .18f), size = Size(w * .54f, h * .59f), style = line); drawLine(tint, Offset(w * .23f, h * .69f), Offset(w * .77f, h * .69f), strokeWidth = stroke, cap = StrokeCap.Round); drawCircle(tint, min * .055f, Offset(w / 2f, h * .82f)) }
            TripIconKind.Profile -> { drawCircle(tint, min * .15f, Offset(w / 2f, h * .30f), style = line); drawArc(color = tint, startAngle = 180f, sweepAngle = 180f, useCenter = false, topLeft = Offset(w * .22f, h * .44f), size = Size(w * .56f, h * .44f), style = line) }
            TripIconKind.MapPin -> { drawCircle(tint, min * .28f, Offset(w / 2f, h * .40f), style = line); drawLine(tint, Offset(w * .32f, h * .60f), Offset(w / 2f, h * .84f), strokeWidth = stroke, cap = StrokeCap.Round); drawLine(tint, Offset(w / 2f, h * .84f), Offset(w * .68f, h * .60f), strokeWidth = stroke, cap = StrokeCap.Round); drawCircle(tint, min * .06f, Offset(w / 2f, h * .40f)) }
            TripIconKind.Bell -> {
                val side = min
                val offsetX = (w - side) / 2f
                val offsetY = (h - side) / 2f
                fun px(fraction: Float) = offsetX + side * fraction
                fun py(fraction: Float) = offsetY + side * fraction
                val bell = Path().apply {
                    moveTo(px(.22f), py(.69f))
                    lineTo(px(.29f), py(.59f))
                    lineTo(px(.29f), py(.46f))
                    cubicTo(px(.29f), py(.27f), px(.39f), py(.17f), px(.50f), py(.17f))
                    cubicTo(px(.61f), py(.17f), px(.71f), py(.27f), px(.71f), py(.46f))
                    lineTo(px(.71f), py(.59f))
                    lineTo(px(.78f), py(.69f))
                    close()
                }
                drawPath(bell, tint, style = line)
                drawLine(tint, Offset(px(.18f), py(.69f)), Offset(px(.82f), py(.69f)), strokeWidth = stroke, cap = StrokeCap.Round)
                drawArc(color = tint, startAngle = 0f, sweepAngle = 180f, useCenter = false, topLeft = Offset(px(.42f), py(.69f)), size = Size(side * .16f, side * .14f), style = line)
            }
            TripIconKind.Plus -> { drawLine(tint, Offset(w / 2f, h * .24f), Offset(w / 2f, h * .76f), strokeWidth = stroke, cap = StrokeCap.Round); drawLine(tint, Offset(w * .24f, h / 2f), Offset(w * .76f, h / 2f), strokeWidth = stroke, cap = StrokeCap.Round) }
            TripIconKind.ArrowRight -> { drawLine(tint, Offset(w * .20f, h / 2f), Offset(w * .78f, h / 2f), strokeWidth = stroke, cap = StrokeCap.Round); drawLine(tint, Offset(w * .56f, h * .28f), Offset(w * .78f, h / 2f), strokeWidth = stroke, cap = StrokeCap.Round); drawLine(tint, Offset(w * .56f, h * .72f), Offset(w * .78f, h / 2f), strokeWidth = stroke, cap = StrokeCap.Round) }
            TripIconKind.Calendar -> { drawRoundRect(tint, Offset(w * .18f, h * .23f), Size(w * .64f, h * .58f), CornerRadius(min * .08f), style = line); drawLine(tint, Offset(w * .18f, h * .42f), Offset(w * .82f, h * .42f), strokeWidth = stroke, cap = StrokeCap.Round); drawLine(tint, Offset(w * .36f, h * .15f), Offset(w * .36f, h * .30f), strokeWidth = stroke, cap = StrokeCap.Round); drawLine(tint, Offset(w * .64f, h * .15f), Offset(w * .64f, h * .30f), strokeWidth = stroke, cap = StrokeCap.Round) }
            TripIconKind.Check -> { drawCircle(tint, min * .36f, c, style = line); drawLine(tint, Offset(w * .30f, h * .51f), Offset(w * .45f, h * .66f), strokeWidth = stroke, cap = StrokeCap.Round); drawLine(tint, Offset(w * .45f, h * .66f), Offset(w * .72f, h * .36f), strokeWidth = stroke, cap = StrokeCap.Round) }
            TripIconKind.Users -> { drawCircle(tint, min * .14f, Offset(w * .38f, h * .31f), style = line); drawCircle(tint, min * .12f, Offset(w * .67f, h * .37f), style = line); drawArc(color = tint, startAngle = 180f, sweepAngle = 180f, useCenter = false, topLeft = Offset(w * .18f, h * .46f), size = Size(w * .43f, h * .34f), style = line); drawArc(color = tint, startAngle = 180f, sweepAngle = 180f, useCenter = false, topLeft = Offset(w * .48f, h * .52f), size = Size(w * .36f, h * .28f), style = line) }
            TripIconKind.Chevron -> { drawLine(tint, Offset(w * .34f, h * .40f), Offset(w / 2f, h * .60f), strokeWidth = stroke, cap = StrokeCap.Round); drawLine(tint, Offset(w / 2f, h * .60f), Offset(w * .66f, h * .40f), strokeWidth = stroke, cap = StrokeCap.Round) }
            TripIconKind.Trash -> { drawRoundRect(tint, Offset(w * .25f, h * .28f), Size(w * .50f, h * .54f), CornerRadius(min * .05f), style = line); drawLine(tint, Offset(w * .20f, h * .22f), Offset(w * .80f, h * .22f), strokeWidth = stroke, cap = StrokeCap.Round); drawLine(tint, Offset(w * .40f, h * .15f), Offset(w * .60f, h * .15f), strokeWidth = stroke, cap = StrokeCap.Round) }
            TripIconKind.LogOut -> { drawRoundRect(tint, Offset(w * .16f, h * .18f), Size(w * .36f, h * .64f), CornerRadius(min * .05f), style = line); drawLine(tint, Offset(w * .43f, h / 2f), Offset(w * .83f, h / 2f), strokeWidth = stroke, cap = StrokeCap.Round); drawLine(tint, Offset(w * .65f, h * .30f), Offset(w * .83f, h / 2f), strokeWidth = stroke, cap = StrokeCap.Round); drawLine(tint, Offset(w * .65f, h * .70f), Offset(w * .83f, h / 2f), strokeWidth = stroke, cap = StrokeCap.Round) }
            TripIconKind.MoveUp -> { drawLine(tint, Offset(w / 2f, h * .80f), Offset(w / 2f, h * .20f), strokeWidth = stroke, cap = StrokeCap.Round); drawLine(tint, Offset(w * .25f, h * .42f), Offset(w / 2f, h * .20f), strokeWidth = stroke, cap = StrokeCap.Round); drawLine(tint, Offset(w * .75f, h * .42f), Offset(w / 2f, h * .20f), strokeWidth = stroke, cap = StrokeCap.Round) }
            TripIconKind.MoveDown -> { drawLine(tint, Offset(w / 2f, h * .20f), Offset(w / 2f, h * .80f), strokeWidth = stroke, cap = StrokeCap.Round); drawLine(tint, Offset(w * .25f, h * .58f), Offset(w / 2f, h * .80f), strokeWidth = stroke, cap = StrokeCap.Round); drawLine(tint, Offset(w * .75f, h * .58f), Offset(w / 2f, h * .80f), strokeWidth = stroke, cap = StrokeCap.Round) }
            TripIconKind.Alert -> { drawCircle(tint, min * .36f, c, style = line); drawLine(tint, Offset(w / 2f, h * .28f), Offset(w / 2f, h * .55f), strokeWidth = stroke, cap = StrokeCap.Round); drawCircle(tint, min * .045f, Offset(w / 2f, h * .72f)) }
        }
    }
}

private fun TripVisibility.label(): String = when (this) { TripVisibility.Private -> "Private"; TripVisibility.Unlisted -> "Unlisted"; TripVisibility.Open -> "Open" }
private fun TripStatus.label(): String = when (this) { TripStatus.Draft -> "Draft"; TripStatus.Planning -> "Planning"; TripStatus.Confirmed -> "Confirmed"; TripStatus.Completed -> "Completed"; TripStatus.Cancelled -> "Cancelled"; TripStatus.Archived -> "Archived" }
private fun TripMemberRole.label(): String = when (this) { TripMemberRole.Owner -> "Owner"; TripMemberRole.Editor -> "Editor"; TripMemberRole.Viewer -> "Viewer" }
private fun ItineraryVisibility.label(): String = when (this) {
    ItineraryVisibility.Members -> "Members only"
    ItineraryVisibility.TripSummary -> "Trip summary"
}
internal fun initials(name: String): String = name.trim().split(" ").filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "T" }
private fun capacityBucket(value: Int): String = when { value <= 4 -> "2_4"; value <= 8 -> "5_8"; else -> "9_12" }
private fun priceBucket(amountMicros: Long?): String = when {
    amountMicros == null -> "unknown"
    amountMicros <= 0L -> "free"
    amountMicros < 5_000_000L -> "under_5"
    amountMicros < 10_000_000L -> "5_10"
    else -> "10_plus"
}
private fun relativeTimeLabel(epochMillis: Long): String {
    val ageSeconds = ((currentEpochMillis() - epochMillis) / 1000L).coerceAtLeast(0L)
    return when {
        ageSeconds < 60L -> "just now"
        ageSeconds < 3_600L -> "${ageSeconds / 60L}m ago"
        ageSeconds < 86_400L -> "${ageSeconds / 3_600L}h ago"
        else -> "${ageSeconds / 86_400L}d ago"
    }
}
private fun inviteExpiryLabel(invite: InviteLink): String {
    val remainingHours = ((invite.expiresAtEpochMillis - currentEpochMillis()) / 3_600_000L).coerceAtLeast(0L)
    return when {
        remainingHours >= 24 -> "in ${remainingHours / 24}d"
        else -> "in ${remainingHours}h"
    }
}
private fun inviteExpiryBucket(hours: Int): String = when {
    hours <= 24 -> "24h"
    hours <= 24 * 7 -> "7d"
    else -> "30d"
}
private enum class ItineraryDateChangeChoice { MoveToFirstDay, KeepUnscheduled, DeleteAffected }

private fun isDateWithinRange(value: String?, start: String, end: String): Boolean {
    val date = value?.let(::parseDate) ?: return true
    val first = parseDate(start) ?: return true
    val last = parseDate(end) ?: return true
    val ordinal = toOrdinal(date)
    return ordinal in toOrdinal(first)..toOrdinal(last)
}

private fun overlaps(first: ItineraryItem, second: ItineraryItem): Boolean {
    if (first.flexibleTime || second.flexibleTime || first.status == ItineraryItemStatus.Cancelled || second.status == ItineraryItemStatus.Cancelled) return false
    val firstStart = parseClockMinutes(first.startTimeLabel) ?: return false
    val secondStart = parseClockMinutes(second.startTimeLabel) ?: return false
    val firstEnd = firstStart + first.durationMinutes.coerceAtLeast(1)
    val secondEnd = secondStart + second.durationMinutes.coerceAtLeast(1)
    return firstStart < secondEnd && secondStart < firstEnd
}

private fun parseClockMinutes(value: String?): Int? {
    val parts = value?.split(':') ?: return null
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    return if (hour in 0..23 && minute in 0..59) hour * 60 + minute else null
}

internal fun dayDateAt(trip: TripRecord, day: Int): String {
    var date = parseDate(trip.startDate) ?: return trip.startDate
    repeat(day.coerceAtLeast(0)) {
        val monthLength = when (date.month) {
            1, 3, 5, 7, 8, 10, 12 -> 31
            4, 6, 9, 11 -> 30
            else -> if (date.year % 4 == 0 && (date.year % 100 != 0 || date.year % 400 == 0)) 29 else 28
        }
        date = if (date.day < monthLength) date.copy(day = date.day + 1)
        else if (date.month < 12) date.copy(month = date.month + 1, day = 1)
        else date.copy(year = date.year + 1, month = 1, day = 1)
    }
    return date.toIsoString()
}

private fun dayLabel(trip: TripRecord, day: Int): String = dayDateAt(trip, day)

internal fun daysInclusive(start: String, end: String): Int {
    val a = parseDate(start) ?: return 1
    val b = parseDate(end) ?: return 1
    return (toOrdinal(b) - toOrdinal(a) + 1).coerceAtLeast(1)
}

internal data class SimpleDate(val year: Int, val month: Int, val day: Int)
internal fun parseDate(value: String): SimpleDate? = value.split('-').takeIf { it.size == 3 }?.let {
    runCatching { SimpleDate(it[0].toInt(), it[1].toInt(), it[2].toInt()) }.getOrNull()
}
internal fun toOrdinal(date: SimpleDate): Int {
    var y = date.year; val m = date.month; if (m <= 2) y--
    val era = y / 400; val yoe = y - era * 400; val mp = m + if (m > 2) -3 else 9; val doy = (153 * mp + 2) / 5 + date.day - 1; val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146097 + doe
}

internal fun SimpleDate.toIsoString(): String = "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"

private fun ItineraryItem.toUpdateInput(position: Int): UpdateItineraryItemInput = UpdateItineraryItemInput(
    type = type,
    title = title,
    startTimeEpochMillis = startTimeEpochMillis,
    startTimeLabel = startTimeLabel,
    durationMinutes = durationMinutes,
    place = place,
    note = note,
    status = status,
    visibility = visibility,
    position = position,
    expectedRevision = revision,
    dayDate = dayDate,
    flexibleTime = flexibleTime,
)

private fun CreateItineraryItemInput.toUpdateInput(current: ItineraryItem): UpdateItineraryItemInput = UpdateItineraryItemInput(
    type = type,
    title = title,
    startTimeEpochMillis = startTimeEpochMillis,
    startTimeLabel = startTimeLabel,
    durationMinutes = durationMinutes,
    place = place,
    note = note,
    status = status,
    visibility = visibility,
    position = current.position,
    expectedRevision = current.revision,
    dayDate = dayDate ?: current.dayDate,
    flexibleTime = flexibleTime,
)

/** Brand field: persistent label, white rounded input, explicit focus and error states. */
@Composable
private fun TripTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    isError: Boolean = false,
    enabled: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalTextStyle provides MaterialTheme.typography.labelLarge,
            LocalContentColor provides if (isError) ErrorRed else InkSoft,
        ) { label() }
        BasicTextField(
            value = value, onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
                .onFocusChanged { focused = it.isFocused }
                .border(if (focused || isError) 2.dp else 1.dp, if (isError) ErrorRed else if (focused) CoralHero else CreamBorder, RoundedCornerShape(14.dp))
                .background(Color.White, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
            enabled = enabled, singleLine = singleLine, minLines = minLines, maxLines = maxLines,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink),
            visualTransformation = visualTransformation, keyboardOptions = keyboardOptions,
            cursorBrush = androidx.compose.ui.graphics.SolidColor(Coral),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty() && placeholder != null) androidx.compose.runtime.CompositionLocalProvider(LocalContentColor provides InkMuted) { placeholder() }
                    inner()
                }
            },
        )
        if (supportingText != null) androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalTextStyle provides MaterialTheme.typography.labelSmall,
            LocalContentColor provides if (isError) ErrorRed else InkMuted,
        ) { supportingText() }
    }
}

@Composable
private fun DesignCard(containerColor: Color = Color.White, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), color = containerColor, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, CreamBorder), shadowElevation = 1.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

/** The settings reassurance shown immediately before destructive account actions. */
@Composable
private fun SettingsSafetyNote() {
    Surface(
        Modifier.fillMaxWidth(),
        color = SageTint.copy(alpha = .58f),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, SageTint),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(40.dp).background(SageTint, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                TripIcon(TripIconKind.Check, "Safety", tint = Sage, modifier = Modifier.size(22.dp))
            }
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Ink)) {
                        append("Safety is never premium. ")
                    }
                    withStyle(SpanStyle(color = InkSoft)) {
                        append("Blocking, reporting, and privacy controls stay free for every traveler.")
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

fun validateTripDetails(title: String, destination: String, start: String, end: String, timezone: String, currency: String): Map<String, String> = buildMap {
    if (title.trim().length !in 2..120) put("title", "Use 2–120 characters for your trip title.")
    if (destination.trim().length !in 1..160) put("destination", "Enter a city or region for your trip.")
    val a = localDateTimeToEpochMillis(start, "12:00", "UTC")
    val b = localDateTimeToEpochMillis(end, "12:00", "UTC")
    if (a == null || b == null) put("dates", "Choose both dates using the calendar.")
    else if (b < a) put("dates", "End date must be on or after the start date.")
    else if ((b - a) / 86_400_000L >= 60) put("dates", "Choose a trip lasting no more than 60 days.")
    if (localDateTimeToEpochMillis("2026-01-15", "12:00", timezone.trim()) == null) put("destinationTimezone", "Use a timezone such as Asia/Jakarta, Asia/Tokyo or Europe/Lisbon.")
    if (!currency.matches(Regex("[A-Z]{3}"))) put("currency", "Enter a three-letter currency code, such as IDR or USD.")
}

private fun monthDays(year: Int, month: Int): Int = when (month) {
    4, 6, 9, 11 -> 30
    2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
    else -> 31
}
private fun todayDate(): SimpleDate {
    var days = (currentEpochMillis() / 86_400_000L).toInt()
    var year = 1970
    while (days >= (if (monthDays(year, 2) == 29) 366 else 365)) { days -= if (monthDays(year, 2) == 29) 366 else 365; year++ }
    var month = 1
    while (days >= monthDays(year, month)) { days -= monthDays(year, month); month++ }
    return SimpleDate(year, month, days + 1)
}
@Composable
private fun TripDateField(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, isError: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    val date = parseDate(value)?.takeIf { it.month in 1..12 && it.day in 1..monthDays(it.year, it.month) }
    val months = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    Surface(modifier.clickable { open = true }.semantics { contentDescription = "$label date: ${value.ifBlank { "Choose date" }}" }, color = Cream, shape = RoundedCornerShape(14.dp), border = BorderStroke(if (isError) 2.dp else 1.dp, if (isError) ErrorRed else CreamBorder)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, color = InkMuted, style = MaterialTheme.typography.labelSmall)
            Text(date?.let { "${months[it.month - 1].take(3)} ${it.day}" } ?: "Choose date", style = MaterialTheme.typography.titleMedium)
            Text(date?.year?.toString() ?: "Open calendar", color = InkMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
    if (open) {
        val initial = date ?: todayDate()
        var year by remember { mutableIntStateOf(initial.year) }
        var month by remember { mutableIntStateOf(initial.month) }
        AlertDialog(onDismissRequest = { open = false }, containerColor = Cream, shape = RoundedCornerShape(24.dp),
            title = { Text("Choose ${label.lowercase()} date", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        IconButton(onClick = { if (month == 1) { month = 12; year-- } else month-- }, modifier = Modifier.semantics { contentDescription = "Previous month" }) { Text("‹", style = MaterialTheme.typography.titleLarge) }
                        Text("${months[month - 1]} $year", style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = { if (month == 12) { month = 1; year++ } else month++ }, modifier = Modifier.semantics { contentDescription = "Next month" }) { Text("›", style = MaterialTheme.typography.titleLarge) }
                    }
                    Row { listOf("M", "T", "W", "T", "F", "S", "S").forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, color = InkMuted, style = MaterialTheme.typography.labelSmall) } }
                    val offset = ((toOrdinal(SimpleDate(year, month, 1)) - toOrdinal(SimpleDate(1970, 1, 5))) % 7 + 7) % 7
                    val count = monthDays(year, month)
                    (0 until (offset + count + 6) / 7).forEach { week ->
                        Row {
                            (0..6).forEach { dayIndex ->
                                val day = week * 7 + dayIndex - offset + 1
                                val selected = date == SimpleDate(year, month, day)
                                Box(Modifier.weight(1f).height(40.dp).clip(CircleShape).background(if (selected) Coral else Color.Transparent)
                                    .then(if (day in 1..count) Modifier.clickable { onValueChange(SimpleDate(year, month, day).toIsoString()); open = false }.semantics { contentDescription = "$day ${months[month - 1]} $year" } else Modifier), contentAlignment = Alignment.Center) {
                                    if (day in 1..count) Text(day.toString(), color = if (selected) Color.White else Ink, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }, confirmButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PaceSelector(selected: TripPace?, onSelect: (TripPace) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        TripPace.entries.forEachIndexed { index, option ->
            Surface(Modifier.weight(1f).clickable { onSelect(option) }, color = Color.White, shape = RoundedCornerShape(16.dp), border = BorderStroke(if (selected == option) 2.dp else 1.dp, if (selected == option) CoralHero else CreamBorder)) {
                Column(Modifier.padding(horizontal = 6.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TripIcon(TripIconKind.Discover, null, tint = if (selected == option) Coral else InkMuted)
                    Text(option.label, style = MaterialTheme.typography.labelLarge)
                    Text(listOf("Lots of downtime", "Mix of both", "See it all")[index], style = MaterialTheme.typography.labelSmall, color = InkMuted, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun BudgetSelector(selected: BudgetBand?, onSelect: (BudgetBand) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        BudgetBand.entries.forEachIndexed { index, option ->
            Surface(Modifier.fillMaxWidth().clickable { onSelect(option) }, color = if (selected == option) SageTint else Color.White, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, if (selected == option) Sage else CreamBorder)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("$".repeat(index + 1), color = Sage, style = MaterialTheme.typography.titleMedium)
                    Column(Modifier.weight(1f)) {
                        Text(option.label, style = MaterialTheme.typography.labelLarge)
                        Text(when (option) { BudgetBand.Budget -> "Simple stays, street food, public transit"; BudgetBand.Moderate -> "Comfortable stays, a mix of meals out"; BudgetBand.Comfort -> "Boutique stays and special experiences"; BudgetBand.Premium -> "Luxury stays and curated experiences" }, color = InkMuted, style = MaterialTheme.typography.labelSmall)
                    }
                    if (selected == option) TripIcon(TripIconKind.Check, null, tint = Sage)
                }
            }
        }
    }
}

@Composable
private fun RegionSelector(value: String, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Home region", style = MaterialTheme.typography.labelLarge)
        Surface(
            Modifier.fillMaxWidth().clickable { open = true }.semantics {
                contentDescription = "Home region: ${value.ifBlank { "Choose a region" }}"
            },
            color = Color.White,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, CreamBorder),
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TripIcon(TripIconKind.MapPin, null, tint = InkMuted)
                Text(value.ifBlank { "Choose a region" }, Modifier.weight(1f))
                TripIcon(TripIconKind.Chevron, null, tint = InkMuted)
            }
        }
        Text("Share a region, never an exact address.", color = InkMuted, style = MaterialTheme.typography.labelSmall)
    }
    if (open) {
        var query by remember { mutableStateOf("") }
        val countries = remember { countryNames }
        AlertDialog(onDismissRequest = { open = false }, containerColor = Cream, title = { Text("Choose your country") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TripTextField(query, { query = it.take(80) }, label = { Text("Search countries") }, singleLine = true)
                LazyColumn(Modifier.height(280.dp)) {
                    item { TextButton(onClick = { onChange("Prefer not to say"); open = false }) { Text("Prefer not to say") } }
                    val matches = countries.filter { it.contains(query.trim(), ignoreCase = true) }
                    if (matches.isEmpty()) item { Text("No countries found. Try another search.", color = InkMuted) }
                    items(matches) { country ->
                        TextButton(onClick = { onChange(country); open = false }, modifier = Modifier.fillMaxWidth()) { Text(country, Modifier.weight(1f), color = Ink); if (country == value) TripIcon(TripIconKind.Check, null, tint = Sage) }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } })
    }
}

@Composable
private fun DestinationPicker(value: String, onChange: (String) -> Unit, error: String?) {
    var open by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxWidth().clickable { open = true }, shape = RoundedCornerShape(18.dp), color = Color.White, border = BorderStroke(if (error != null) 2.dp else 1.dp, if (error != null) ErrorRed else CreamBorder), shadowElevation = 1.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("DESTINATION", color = InkMuted, style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp).background(CoralTint, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { TripIcon(TripIconKind.MapPin, null, tint = Coral) }
                Column(Modifier.weight(1f)) {
                    Text(value.ifBlank { "Choose destination" }, style = MaterialTheme.typography.titleMedium)
                    Text("City or region", style = MaterialTheme.typography.bodySmall, color = InkMuted)
                }
                TripIcon(TripIconKind.ArrowRight, null, tint = InkMuted, modifier = Modifier.size(18.dp))
            }
            if (error != null) Text(error, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
        }
    }
    if (open) {
        var draft by remember { mutableStateOf(value) }
        AlertDialog(onDismissRequest = { open = false }, containerColor = Cream, title = { Text("Choose destination") }, text = {
            TripTextField(draft, { draft = it.take(160) }, label = { Text("City or region") }, placeholder = { Text("e.g. Kyoto, Japan") }, singleLine = true)
        }, confirmButton = { TextButton(enabled = draft.isNotBlank(), onClick = { onChange(draft.trim()); open = false }) { Text("Use destination") } }, dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } })
    }
}

@Composable
private fun TimezonePicker(value: String, onChange: (String) -> Unit, error: String?) {
    var open by remember { mutableStateOf(false) }
    Column {
        Row(Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            TripIcon(TripIconKind.Calendar, null, tint = if (error == null) InkMuted else ErrorRed, modifier = Modifier.size(18.dp))
            Text("  Local time · $value", Modifier.weight(1f), color = if (error == null) InkMuted else ErrorRed, style = MaterialTheme.typography.bodySmall)
            Text("Change", color = Coral, style = MaterialTheme.typography.labelLarge)
        }
        if (error != null) Text(error, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
    }
    if (open) {
        var draft by remember { mutableStateOf(value) }
        val valid = localDateTimeToEpochMillis("2026-01-15", "12:00", draft.trim()) != null
        AlertDialog(onDismissRequest = { open = false }, containerColor = Cream, title = { Text("Destination timezone") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TripTextField(draft, { draft = it.take(80) }, label = { Text("Timezone") }, placeholder = { Text("Asia/Jakarta") }, singleLine = true, isError = !valid,
                    supportingText = { Text(if (valid) "Itinerary times follow this timezone." else "Use an area/city, such as Asia/Jakarta.") })
                listOf("Asia/Jakarta", "Asia/Makassar", "Asia/Tokyo", "Europe/Lisbon", "UTC").forEach { option ->
                    TextButton(onClick = { onChange(option); open = false }) { Text(option) }
                }
            }
        }, confirmButton = { TextButton(enabled = valid, onClick = { onChange(draft.trim()); open = false }) { Text("Save") } }, dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } })
    }
}

/** Long editing forms use a full page with a fixed action footer. */
@Composable
private fun TripFormDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismissRequest, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Cream) {
            Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalTextStyle provides MaterialTheme.typography.titleLarge) { title() }
                }
                Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp)) { text() }
                HorizontalDivider(color = CreamBorder)
                Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    dismissButton()
                    confirmButton()
                }
            }
        }
    }
}

internal fun shortDate(value: String): String {
    val date = parseDate(value) ?: return value
    return "${listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec").getOrElse(date.month - 1) { "" }} ${date.day}"
}
internal fun weekday(value: String): String {
    val date = parseDate(value) ?: return "Day"
    val anchor = toOrdinal(SimpleDate(2026, 9, 6))
    return listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")[((toOrdinal(date) - anchor) % 7 + 7) % 7]
}
@Composable
private fun TripSectionTabs(selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().background(CreamMuted, RoundedCornerShape(14.dp)).padding(4.dp)) {
        listOf("Overview", "Itinerary", "Members").forEachIndexed { index, label ->
            Surface(Modifier.weight(1f).clickable { onSelect(index) }.semantics { stateDescription = if (selected == index) "Selected" else "Not selected" }, color = if (selected == index) Color.White else CreamMuted, shape = RoundedCornerShape(10.dp)) {
                Text(label, Modifier.padding(vertical = 13.dp), textAlign = TextAlign.Center, color = if (selected == index) Coral else InkMuted, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
@Composable
private fun TripOverview(
    trip: TripRecord,
    items: List<ItineraryItem>,
    team: List<TripMember>,
    loading: Boolean,
    error: TripTandemError?,
    retry: () -> Unit,
    onMembers: () -> Unit,
    onItinerary: () -> Unit,
    lastSyncTime: Long? = null,
    onSyncNow: (() -> Unit)? = null,
    refreshing: Boolean = false,
    onExport: (() -> Unit)? = null,
    isOnline: Boolean = true,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { DesignCard {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                DestinationThumbnail(trip.destination)
                Column(Modifier.weight(1f)) { Text("DESTINATION", color = InkMuted, style = MaterialTheme.typography.labelSmall); Text(trip.destination, style = MaterialTheme.typography.titleLarge); Text("${shortDate(trip.startDate)} – ${shortDate(trip.endDate)} · ${daysInclusive(trip.startDate, trip.endDate)} days", color = InkMuted, style = MaterialTheme.typography.bodySmall) }
            }
            HorizontalDivider(color = CreamBorder)
            listOf(listOf("BUDGET" to (trip.budgetBand?.label ?: "Not set"), "PACE" to (trip.pace?.label ?: "Not set")), listOf("VISIBILITY" to trip.visibility.label(), "PROGRESS" to if (loading) "Loading…" else "${items.size} activities")).forEach { fields ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { fields.forEach { (label, value) ->
                    Column(Modifier.weight(1f).background(Cream, RoundedCornerShape(12.dp)).padding(12.dp)) { Text(label, color = InkMuted, style = MaterialTheme.typography.labelSmall); Text(value, style = MaterialTheme.typography.titleMedium) }
                } }
            }
            if (!loading) Text("${items.mapNotNull { it.dayDate }.distinct().size} days planned · ${trip.destinationTimezone}", color = Sage, style = MaterialTheme.typography.bodySmall)
            HorizontalDivider(color = CreamBorder)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Synced: ${formatSyncAge(lastSyncTime)}",
                    color = InkMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (onSyncNow != null) {
                        TextButton(
                            onClick = onSyncNow,
                            enabled = !refreshing,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            if (refreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = Coral,
                                )
                            } else {
                                Text("Sync now", color = Coral, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    if (onExport != null) {
                        TextButton(
                            onClick = onExport,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text("Export", color = Coral, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        } }
        if (error != null) item { ErrorBanner(error, retry) }
        item { Text("UP NEXT", color = CoralHero, style = MaterialTheme.typography.labelLarge) }
        val next = items.filter { it.status != ItineraryItemStatus.Cancelled && it.status != ItineraryItemStatus.Completed && (it.dayDate == null || it.dayDate >= todayDate().toIsoString()) }.sortedWith(compareBy({ it.dayDate ?: "9999" }, { it.startTimeLabel ?: "99:99" })).firstOrNull()
        item { Surface(Modifier.fillMaxWidth().clickable(onClick = onItinerary), shape = RoundedCornerShape(18.dp), color = Color.White, border = BorderStroke(1.dp, CreamBorder)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(next?.let { "${it.dayDate?.let(::shortDate).orEmpty()} · ${it.startTimeLabel ?: "Flexible"}" } ?: "Your next adventure starts here", color = Coral, style = MaterialTheme.typography.labelLarge)
                Text(next?.title ?: "Plan your first activity", style = MaterialTheme.typography.titleLarge)
                Text(next?.place ?: "Open itinerary →", color = InkMuted)
            }
        } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Team", style = MaterialTheme.typography.titleLarge); TextButton(onClick = onMembers) { Text("${trip.activeMemberCount} members →") } } }
        items(team, key = { it.userId }) { member ->
            Surface(Modifier.fillMaxWidth().clickable(onClick = onMembers), color = Color.White, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, CreamBorder)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(44.dp).background(CoralTint, CircleShape), contentAlignment = Alignment.Center) { Text(initials(member.displayName), color = Coral, style = MaterialTheme.typography.titleMedium) }
                    Text(member.displayName, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    StatusChip(member.role.label(), if (member.role == TripMemberRole.Owner) CoralTint else SageTint, if (member.role == TripMemberRole.Owner) Coral else Sage)
                }
            }
        }
        item { OutlinedButton(onClick = onMembers, modifier = Modifier.fillMaxWidth()) { Text("View team & invite people") } }
    }
}
@Composable
private fun ItineraryEmptyState(day: Int, date: String, onAdd: () -> Unit, onGenerate: (() -> Unit)?) {
    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Box(Modifier.size(88.dp).background(Brush.linearGradient(listOf(CoralTint, SageTint)), RoundedCornerShape(28.dp)), contentAlignment = Alignment.Center) { TripIcon(TripIconKind.Plus, null, tint = CoralHero, modifier = Modifier.size(40.dp)) }
        Text("DAY ${day + 1} · ${weekday(date).uppercase()}", color = CoralHero, style = MaterialTheme.typography.labelLarge)
        Text("No plans yet.\nLet’s add one!", textAlign = TextAlign.Center, style = MaterialTheme.typography.headlineSmall)
        Text(if (onGenerate == null) "Start by adding your first activity." else "Start by adding your first activity, or let AI generate ideas for you.", textAlign = TextAlign.Center, color = InkMuted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onAdd, shape = RoundedCornerShape(14.dp)) { Text("+  Add activity") }
            if (onGenerate != null) Button(onClick = onGenerate, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = CoralHero)) { Text("Generate with AI") }
        }
        Surface(color = SageTint, shape = RoundedCornerShape(18.dp)) { Text("Activities can be meals, sightseeing, transport, accommodation, or anything else you want to do together.", Modifier.padding(18.dp), color = Sage) }
    }
}

@Composable
private fun OfflineModeBanner(
    lastSyncTime: Long?,
    onSyncNow: () -> Unit,
    refreshing: Boolean,
) {
    val isStale = lastSyncTime != null && !OfflineCachePolicy.isAuthorizedOffline(lastSyncTime)
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (isStale) DangerTint else CoralTint,
        border = BorderStroke(1.dp, if (isStale) DangerBorder else CreamBorder),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TripIcon(
                    TripIconKind.Alert,
                    contentDescription = null,
                    tint = if (isStale) Danger else Coral,
                    modifier = Modifier.size(18.dp),
                )
                Column {
                    if (isStale) {
                        Text(
                            "Offline authorization expired (7+ days)",
                            color = Danger,
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(
                            "Reconnect to refresh your access and view updates.",
                            color = Color(0xFF5C2416),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        Text(
                            "Offline mode (read-only)",
                            color = Coral,
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(
                            "Synced ${formatSyncAge(lastSyncTime)} · Reconnect to edit",
                            color = InkSoft,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            TextButton(
                onClick = onSyncNow,
                enabled = !refreshing,
            ) {
                if (refreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = if (isStale) Danger else Coral,
                    )
                } else {
                    Text(
                        "Sync now",
                        color = if (isStale) Danger else Coral,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExportItineraryDialog(
    trip: TripRecord,
    items: List<ItineraryItem>,
    members: List<TripMember>,
    onDismiss: () -> Unit,
    onShare: ((String) -> Unit)?,
    analytics: TripTandemAnalytics,
) {
    var format by remember { mutableStateOf(ExportFormat.PlainText) }
    var includeNotes by remember { mutableStateOf(false) }
    var includeLodging by remember { mutableStateOf(false) }
    var includeMemberNames by remember { mutableStateOf(false) }
    var includeLocations by remember { mutableStateOf(true) }
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        analytics.logEvent(
            TripTandemAnalytics.Events.EXPORT_STARTED,
            mapOf("format" to if (format == ExportFormat.PlainText) "plain_text" else "markdown"),
        )
    }

    val options = remember(format, includeNotes, includeLodging, includeMemberNames, includeLocations) {
        ExportOptions(
            format = format,
            includeNotes = includeNotes,
            includeLodgingDetails = includeLodging,
            includeMemberInitials = includeMemberNames,
            includePlaces = includeLocations,
        )
    }

    val summaryText = remember(trip, items, members, options) {
        ItineraryExporter.generateSummary(trip, items, members, options)
    }

    val includedFieldCount = listOf(includeNotes, includeLodging, includeMemberNames, includeLocations).count { it }

    fun logCompleted() {
        analytics.logEvent(
            TripTandemAnalytics.Events.EXPORT_COMPLETED,
            mapOf(
                "format" to if (format == ExportFormat.PlainText) "plain_text" else "markdown",
                "included_field_count" to includedFieldCount.toString(),
            ),
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f),
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Export Itinerary", style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = onDismiss) {
                        Text("✕", style = MaterialTheme.typography.titleMedium, color = InkMuted)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip(
                        label = "Plain text",
                        selected = format == ExportFormat.PlainText,
                        onClick = {
                            format = ExportFormat.PlainText
                            analytics.logEvent(
                                TripTandemAnalytics.Events.EXPORT_STARTED,
                                mapOf("format" to "plain_text"),
                            )
                        },
                    )
                    ChoiceChip(
                        label = "Markdown",
                        selected = format == ExportFormat.Markdown,
                        onClick = {
                            format = ExportFormat.Markdown
                            analytics.logEvent(
                                TripTandemAnalytics.Events.EXPORT_STARTED,
                                mapOf("format" to "markdown"),
                            )
                        },
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth().background(Cream, RoundedCornerShape(14.dp)).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "PRIVACY CONTROLS",
                        style = MaterialTheme.typography.labelSmall,
                        color = InkMuted,
                    )
                    ExportFieldCheckbox("Include private notes", includeNotes) { includeNotes = it }
                    ExportFieldCheckbox("Include lodging details", includeLodging) { includeLodging = it }
                    ExportFieldCheckbox("Include traveler names", includeMemberNames) { includeMemberNames = it }
                    ExportFieldCheckbox("Include stop locations", includeLocations) { includeLocations = it }
                    Text(
                        "Notes, lodging, and traveler names are off by default to protect privacy.",
                        style = MaterialTheme.typography.labelSmall,
                        color = InkSoft,
                    )
                }

                Text("PREVIEW", style = MaterialTheme.typography.labelSmall, color = InkMuted)
                Surface(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Cream,
                    border = BorderStroke(1.dp, CreamBorder),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                    ) {
                        item {
                            Text(
                                text = summaryText,
                                style = MaterialTheme.typography.bodySmall,
                                color = Ink,
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(summaryText))
                            copied = true
                            logCompleted()
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Coral),
                    ) {
                        Text(if (copied) "Copied!" else "Copy", color = Coral)
                    }
                    Button(
                        onClick = {
                            logCompleted()
                            onShare?.invoke(summaryText)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Coral),
                    ) {
                        Text("Share")
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportFieldCheckbox(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Ink)
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(checkedColor = Coral),
        )
    }
}

@Composable
private fun OfflineStorageCard(
    repositories: TripTandemRepositories,
    analytics: TripTandemAnalytics,
    userId: String?,
) {
    var cacheSizeBytes by remember(userId) { mutableStateOf<Long?>(null) }
    var clearing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(userId) {
        cacheSizeBytes = repositories.offlineCache.getCacheSizeBytes(userId)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, CreamBorder),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Offline Storage", style = MaterialTheme.typography.titleMedium)
                Text(
                    formatBytes(cacheSizeBytes ?: 0L),
                    style = MaterialTheme.typography.labelLarge,
                    color = Coral,
                )
            }
            Text(
                "Locally encrypted cache of your joined trips and itineraries for offline travel.",
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
            )
            OutlinedButton(
                onClick = {
                    clearing = true
                    scope.launch {
                        repositories.offlineCache.clearAll(userId, reason = "user_cleared")
                        cacheSizeBytes = 0L
                        clearing = false
                    }
                },
                enabled = !clearing && (cacheSizeBytes ?: 0L) > 0L,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, CreamBorder),
            ) {
                if (clearing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = InkMuted,
                    )
                } else {
                    Text("Clear offline cache", color = if ((cacheSizeBytes ?: 0L) > 0L) Ink else InkMuted)
                }
            }
        }
    }
}

private fun formatSyncAge(lastSyncEpochMillis: Long?): String {
    if (lastSyncEpochMillis == null || lastSyncEpochMillis <= 0L) return "not synced yet"
    val diff = (currentEpochMillis() - lastSyncEpochMillis).coerceAtLeast(0L)
    return when {
        diff < 60_000L -> "just now"
        diff < 3_600_000L -> "${diff / 60_000L}m ago"
        diff < 86_400_000L -> "${diff / 3_600_000L}h ago"
        else -> "${diff / 86_400_000L}d ago"
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    return when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
        else -> "${bytes / (1024L * 1024L)} MB"
    }
}
