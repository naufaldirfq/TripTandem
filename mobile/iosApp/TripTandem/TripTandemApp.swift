import SwiftUI
import UIKit
import FirebaseAnalytics
import FirebaseAuth
import FirebaseCore
import FirebaseCrashlytics
import FirebaseRemoteConfig
import GoogleSignIn
import Network
import TripTandemShared

@main
struct TripTandemApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @State private var pendingInvite: PendingInvite?
    @StateObject private var connectivity = IOSConnectivityMonitor()
    @Environment(\.openURL) private var openURL

    var body: some Scene {
        WindowGroup {
        TripTandemSharedView(
            pendingInvite: pendingInvite,
            revenueCat: appDelegate.revenueCatCoordinator,
            featureFlags: appDelegate.featureFlags,
            connectivity: connectivity.monitor,
            onOpenExternalUrl: { value in
                guard let url = URL(string: value) else { return }
                openURL(url)
            },
        )
            .id("\(pendingInvite.map { "\($0.tripId):\($0.shareToken)" } ?? "none"):rc\(appDelegate.revenueCatVersion):flags\(appDelegate.featureFlags.aiGenerationEnabled)-\(appDelegate.featureFlags.openTripPublishingEnabled)-\(appDelegate.featureFlags.discoveryEnabled)-\(appDelegate.featureFlags.joinRequestsEnabled)-\(appDelegate.featureFlags.pushEnabled)")
            .onOpenURL { url in
                if !GIDSignIn.sharedInstance.handle(url) {
                    pendingInvite = PendingInvite.from(url: url)
                }
            }
        }
    }
}

private struct TripTandemSharedView: UIViewControllerRepresentable {
    private let analytics = FirebaseAnalyticsBridge()
    let pendingInvite: PendingInvite?
    let revenueCat: RevenueCatCoordinator?
    let featureFlags: TripTandemFeatureFlags
    let connectivity: ConnectivityMonitor
    let onOpenExternalUrl: ((String) -> Void)?

    func makeUIViewController(context: Context) -> UIViewController {
        let presenterBox = WeakViewControllerBox()
        let controller = MainViewControllerKt.MainViewControllerWithAnalyticsAndRepositoriesAndPendingInviteAndRevenueCat(
            analytics: analytics,
            repositories: makeFirebaseRepositories(presenter: { presenterBox.controller }),
            pendingInvite: pendingInvite,
            revenueCat: revenueCat,
            featureFlags: featureFlags,
            onSignOut: {
                GIDSignIn.sharedInstance.signOut()
                try? Auth.auth().signOut()
                Auth.auth().signInAnonymously(completion: nil)
            },
            onOpenExternalUrl: onOpenExternalUrl,
            onManageSubscription: {
                guard let url = URL(string: "https://apps.apple.com/account/subscriptions") else { return }
                UIApplication.shared.open(url)
            },
            connectivity: connectivity,
        )
        presenterBox.controller = controller
        return controller
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

private final class WeakViewControllerBox {
    weak var controller: UIViewController?
}

private extension PendingInvite {
    static func from(url: URL) -> PendingInvite? {
        guard url.scheme == "triptandem", url.host == "join" else { return nil }
        let tripId = url.pathComponents.last(where: { $0 != "/" })
        guard let tripId, !tripId.isEmpty,
              let components = URLComponents(url: url, resolvingAgainstBaseURL: false),
              let token = components.queryItems?.first(where: { $0.name == "token" })?.value,
              !token.isEmpty else { return nil }
        return PendingInvite(tripId: tripId, shareToken: token)
    }
}

private final class FirebaseAnalyticsBridge: NSObject, TripTandemAnalytics {
    func logEvent(name: String, parameters: [String: String]) {
        guard Self.allowedEvents.contains(name) else { return }
        var firebaseParameters: [String: Any] = [:]
        parameters
            .filter { Self.allowedParameters[name, default: []].contains($0.key) }
            .filter { $0.value.count <= 40 && !$0.value.contains(where: { $0.isWhitespace }) }
            .forEach { firebaseParameters[$0.key] = $0.value }
        Analytics.logEvent(name, parameters: firebaseParameters.isEmpty ? nil : firebaseParameters)
    }

    func logScreen(screenName: String) {
        guard Self.allowedScreenNames.contains(screenName),
              screenName.count <= 40,
              !screenName.contains(where: { $0.isWhitespace }) else { return }
        Analytics.logEvent(
            AnalyticsEventScreenView,
            parameters: [AnalyticsParameterScreenName: screenName],
        )
    }

    private static let allowedEvents: Set<String> = [
        "screen_view", "auth_anonymous_succeeded", "auth_anonymous_failed", "auth_session_restored",
        "sign_up_started", "sign_up_completed", "profile_essentials_completed", "profile_optional_completed",
        "profile_preview_opened", "account_deletion_started", "account_deletion_completed",
        "trip_create_started", "trip_draft_continued", "trip_created",
        "trip_conflict_detected", "trip_visibility_changed", "trip_cancelled", "trip_archived", "trip_deleted",
        "itinerary_item_created", "itinerary_item_updated", "itinerary_item_reordered", "itinerary_item_deleted",
        "itinerary_conflict_detected", "itinerary_conflict_resolved", "itinerary_undo_restored", "itinerary_day_viewed",
        "invite_created", "invite_share_opened",
        "invite_accepted", "invite_failed", "member_role_changed", "member_removed", "member_left",
        "ownership_transferred",
        "paywall_viewed", "package_selected", "purchase_started", "purchase_completed", "purchase_cancelled",
        "purchase_failed", "restore_started", "restore_completed", "entitlement_changed",
        "generation_started", "generation_completed", "generation_preview_edited", "generation_applied",
        "generation_failed", "generation_paywall_viewed",
    ]

    private static let allowedScreenNames: Set<String> = [
        "welcome", "auth", "profile_setup", "trips", "create_trip",
        "itinerary", "members", "invite",
    ]

    private static let allowedParameters: [String: Set<String>] = [
        "screen_view": ["screen_name"],
        "auth_anonymous_failed": ["error_type"],
        "sign_up_started": ["method"],
        "sign_up_completed": ["method"],
        "profile_optional_completed": ["field_count_bucket"],
        "trip_create_started": ["source"],
        "trip_created": ["visibility", "capacity_bucket"],
        "trip_visibility_changed": ["from", "to"],
        "trip_cancelled": ["member_count_bucket"],
        "itinerary_item_created": ["type", "entry_method"],
        "itinerary_item_updated": ["field_group"],
        "itinerary_item_reordered": ["method"],
        "itinerary_conflict_detected": ["conflict_type"],
        "itinerary_day_viewed": ["relative_day_bucket"],
        "invite_created": ["role", "expiry_bucket"],
        "invite_share_opened": ["channel_category"],
        "invite_accepted": ["role"],
        "invite_failed": ["reason_class"],
        "member_role_changed": ["from", "to"],
        "paywall_viewed": ["trigger", "offering_id", "entitlement_state"],
        "package_selected": ["package_type"],
        "purchase_started": ["package_type"],
        "purchase_completed": ["package_type", "trial", "localized_price_bucket", "active"],
        "purchase_cancelled": ["package_type"],
        "purchase_failed": ["package_type", "error_class"],
        "restore_completed": ["result_class", "active"],
        "entitlement_changed": ["from", "to", "source"],
        "generation_started": ["scope", "entitlement_state", "existing_item_bucket"],
        "generation_completed": ["scope", "latency_bucket", "result_status", "item_count_bucket"],
        "generation_applied": ["selected_count_bucket", "duplicate_warning"],
        "generation_failed": ["failure_class"],
        "generation_paywall_viewed": ["trigger"],
    ]
}

final class AppDelegate: NSObject, UIApplicationDelegate, ObservableObject {
    private let revenueCatKey = "RevenueCatPublicAPIKey"
    @Published var revenueCatCoordinator: RevenueCatCoordinator?
    @Published private(set) var revenueCatVersion = 0
    @Published var featureFlags = TripTandemFeatureFlags(
        aiGenerationEnabled: false,
        openTripPublishingEnabled: false,
        discoveryEnabled: false,
        joinRequestsEnabled: false,
        pushEnabled: false,
    )
    private var authStateHandle: AuthStateDidChangeListenerHandle?
    private let analytics = FirebaseAnalyticsBridge()

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil,
    ) -> Bool {
        configureFirebase()
        configureCrashlytics()
        configureRemoteConfig()
        startAuthenticatedSession()
        return true
    }

    func applicationDidBecomeActive(_ application: UIApplication) {
        // Refresh on the foreground boundary, then rebuild the shared host so
        // its Compose state consumes the latest entitlement. Without this
        // version bump the SDK cache could update while Pro-gated controls
        // continued showing the previous state until another navigation.
        revenueCatCoordinator?.refreshEntitlement { [weak self] _, _ in
            DispatchQueue.main.async {
                self?.revenueCatVersion += 1
            }
        }
    }

    func application(
        _ app: UIApplication,
        open url: URL,
        options: [UIApplication.OpenURLOptionsKey: Any] = [:],
    ) -> Bool {
        GIDSignIn.sharedInstance.handle(url)
    }

    private func configureFirebase() {
        if FirebaseApp.app() == nil {
            FirebaseApp.configure()
        }
    }

    private func configureCrashlytics() {
        let crashlytics = Crashlytics.crashlytics()
        crashlytics.setCustomValue("ios", forKey: "platform")
        crashlytics.setCustomValue(
            Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "unknown",
            forKey: "app_version",
        )
    }

    private func recordSafeDiagnostic(_ context: String, error: Error) {
        // Do not forward an SDK's localized description: it can contain an
        // invite URL, token, account detail, or provider payload. A static
        // domain and numeric code retain enough signal for triage without
        // copying user or network data into Crashlytics.
        let nsError = error as NSError
        Crashlytics.crashlytics().record(
            error: NSError(domain: "TripTandem.\(context)", code: nsError.code, userInfo: nil),
        )
    }

    private func configureRemoteConfig() {
        let remoteConfig = RemoteConfig.remoteConfig()
        let settings = RemoteConfigSettings()
        #if DEBUG
        settings.minimumFetchInterval = 0
        #else
        settings.minimumFetchInterval = 3600
        #endif
        settings.fetchTimeout = 10
        remoteConfig.configSettings = settings
        remoteConfig.setDefaults([
            "ai_generation_enabled": NSNumber(value: false),
            "open_trip_publishing_enabled": NSNumber(value: false),
            "discovery_enabled": NSNumber(value: false),
            "join_requests_enabled": NSNumber(value: false),
            "push_enabled": NSNumber(value: false),
        ])
        remoteConfig.fetchAndActivate { [weak self] _, error in
            // Firebase does not require the completion handler to run on the
            // main queue. Keep the @Published flag update on main so a remote
            // fetch cannot race SwiftUI rendering or publish from a background
            // thread during cold start.
            DispatchQueue.main.async {
                guard let self else { return }
                if let error {
                    // A fetch failure is expected while offline; preserve safe
                    // defaults and keep only a non-sensitive diagnostic.
                    self.featureFlags = TripTandemFeatureFlags(
                        aiGenerationEnabled: false,
                        openTripPublishingEnabled: false,
                        discoveryEnabled: false,
                        joinRequestsEnabled: false,
                        pushEnabled: false,
                    )
                    self.recordSafeDiagnostic("remote_config_fetch", error: error)
                    return
                }
                self.featureFlags = TripTandemFeatureFlags(
                    aiGenerationEnabled: self.strictRemoteConfigBool(remoteConfig, key: "ai_generation_enabled"),
                    openTripPublishingEnabled: self.strictRemoteConfigBool(remoteConfig, key: "open_trip_publishing_enabled"),
                    discoveryEnabled: self.strictRemoteConfigBool(remoteConfig, key: "discovery_enabled"),
                    joinRequestsEnabled: self.strictRemoteConfigBool(remoteConfig, key: "join_requests_enabled"),
                    pushEnabled: self.strictRemoteConfigBool(remoteConfig, key: "push_enabled"),
                )
            }
        }
    }

    private func strictRemoteConfigBool(_ remoteConfig: RemoteConfig, key: String) -> Bool {
        // RemoteConfigValue.boolValue follows NSString.boolValue, which also
        // treats values such as "Y" and "1" as true. The rollout contract is
        // stricter: only an explicit Boolean true may opt a high-risk feature
        // in; every malformed or unexpected value fails closed.
        let value = remoteConfig.configValue(forKey: key).stringValue
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
        return value == "true"
    }

    private func startAuthenticatedSession() {
        let auth = Auth.auth()
        authStateHandle = auth.addStateDidChangeListener { [weak self] _, user in
            guard let self else { return }
            guard let user else {
                // Do not keep the previous Firebase UID's RevenueCat
                // coordinator alive across sign-out. The shared view is
                // recreated below so its entitlement state is also cleared.
                self.revenueCatCoordinator = nil
                self.revenueCatVersion += 1
                return
            }
            self.configureRevenueCat(for: user.uid)
        }
        if let uid = auth.currentUser?.uid {
            configureRevenueCat(for: uid)
            analytics.logEvent(name: "auth_session_restored", parameters: [:])
            return
        }

        auth.signInAnonymously { [weak self] result, error in
            guard let self else { return }
            if let user = result?.user {
                self.configureRevenueCat(for: user.uid)
                self.analytics.logEvent(name: "auth_anonymous_succeeded", parameters: [:])
            } else {
                self.analytics.logEvent(
                    name: "auth_anonymous_failed",
                    parameters: ["error_type": error.map { String(describing: type(of: $0)) } ?? "unknown"],
                )
            }
        }
    }

    private func configureRevenueCat(for uid: String) {
        guard let apiKey = Bundle.main.object(forInfoDictionaryKey: revenueCatKey) as? String,
              !apiKey.isEmpty else {
            return
        }

        revenueCatCoordinator = RevenueCatIosKt.configureRevenueCat(apiKey: apiKey, appUserId: uid)
        // configureRevenueCat reuses the singleton after the first launch;
        // explicitly re-identify so an anonymous-to-email/provider auth change
        // follows the same Firebase UID and entitlement transfer rules.
        revenueCatCoordinator?.identify(appUserId: uid) { _, error in
            if let error {
                self.recordSafeDiagnostic("revenuecat_identify", error: error)
            }
        }
        revenueCatVersion += 1
    }
}

/** Bridges Apple's path monitor into the shared Compose connectivity flow. */
final class IOSConnectivityMonitor: ObservableObject {
    let monitor = MutableConnectivityMonitor(initialOnline: true)
    private let pathMonitor = NWPathMonitor()
    private let queue = DispatchQueue(label: "com.triptandem.connectivity")

    init() {
        pathMonitor.pathUpdateHandler = { [weak self] path in
            self?.monitor.setOnline(online: path.status == .satisfied)
        }
        pathMonitor.start(queue: queue)
    }

    deinit {
        pathMonitor.cancel()
        monitor.close()
    }
}
