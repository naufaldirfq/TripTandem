import Foundation
import CryptoKit
import Security
import UIKit
import AuthenticationServices
import FirebaseAuth
import FirebaseFirestore
import FirebaseFunctions
import FirebaseCore
import GoogleSignIn
import TripTandemShared

// The shared KMP contracts are deliberately platform-neutral. This file is
// the iOS equivalent of mobile/app/.../FirebaseRepositories.kt: it keeps
// Firebase SDK types out of shared Compose and returns the same DataResult
// vocabulary to the UI.

typealias RepositoryCallback = (DataResult?, Error?) -> Void

private let usersCollection = "users"
private let tripsCollection = "trips"
private let membersCollection = "members"
private let itineraryCollection = "itinerary"
private let invitesCollection = "invites"

private func complete<T: AnyObject>(_ value: T, _ callback: @escaping RepositoryCallback) {
    callback(DataResultSuccess(value: value), nil)
}

private func completeList<T: AnyObject>(_ value: [T], _ callback: @escaping RepositoryCallback) {
    complete(NSArray(array: value), callback)
}

private func completeUnit(_ callback: @escaping RepositoryCallback) {
    complete(KotlinUnit.shared, callback)
}

private func fail(_ error: TripTandemError, _ callback: @escaping RepositoryCallback) {
    callback(DataResultFailure(error: error), nil)
}

private func run<T: AnyObject>(
    _ operation: @escaping () async throws -> T,
    completion: @escaping RepositoryCallback,
) {
    Task {
        do {
            complete(try await operation(), completion)
        } catch {
            fail(mapError(error), completion)
        }
    }
}

private func runList<T: AnyObject>(
    _ operation: @escaping () async throws -> [T],
    completion: @escaping RepositoryCallback,
) {
    Task {
        do {
            completeList(try await operation(), completion)
        } catch {
            fail(mapError(error), completion)
        }
    }
}

private func runUnit(
    _ operation: @escaping () async throws -> Void,
    completion: @escaping RepositoryCallback,
) {
    Task {
        do {
            try await operation()
            completeUnit(completion)
        } catch {
            fail(mapError(error), completion)
        }
    }
}

private func runOptional<T: AnyObject>(
    _ operation: @escaping () async throws -> T?,
    completion: @escaping RepositoryCallback,
) {
    Task {
        do {
            completion(DataResultSuccess<T>(value: try await operation()), nil)
        } catch {
            fail(mapError(error), completion)
        }
    }
}

private func authenticatedUser() -> User? {
    Auth.auth().currentUser
}

private struct AppleSignInCancelled: Error {}

private func makeAuthSession(_ user: User) -> AuthSession {
    AuthSession(uid: user.uid, isAnonymous: user.isAnonymous, email: user.email)
}

private func validEmail(_ value: String) -> Bool {
    let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
    return trimmed.count >= 3 && trimmed.count <= 160 &&
        trimmed.filter { $0 == "@" }.count == 1 && trimmed.split(separator: "@").last?.contains(".") == true
}

private func unauthenticated(_ callback: @escaping RepositoryCallback) {
    fail(TripTandemErrorUnauthenticated.shared, callback)
}

private func mapError(_ error: Error) -> TripTandemError {
    if error is AppleSignInCancelled {
        return TripTandemErrorAuthCancelled.shared
    }
    if error is BridgeNotFound || error is BridgeUndoExpired {
        return TripTandemErrorNotFound.shared
    }
    if error is BridgeRejected {
        return TripTandemErrorPermissionDenied.shared
    }
    if error is BridgeInviteExpired {
        return TripTandemErrorInviteExpired.shared
    }
    if error is BridgeInviteRevoked {
        return TripTandemErrorInviteRevoked.shared
    }
    if error is BridgeInviteCapacity {
        return TripTandemErrorCapacityReached.shared
    }
    if error is BridgeOwnershipRequired {
        return TripTandemErrorValidation(field: "ownership")
    }
    if error is BridgeRecentLoginRequired {
        return TripTandemErrorReauthenticationRequired.shared
    }
    let nsError = error as NSError
    if nsError.domain == FirestoreErrorDomain,
       let firestoreCode = FirestoreErrorCode.Code(rawValue: nsError.code) {
        switch firestoreCode {
        case .permissionDenied:
            return TripTandemErrorPermissionDenied.shared
        case .notFound:
            return TripTandemErrorNotFound.shared
        case .aborted, .failedPrecondition:
            return TripTandemErrorConflict.shared
        case .unavailable, .deadlineExceeded:
            return TripTandemErrorOffline.shared
        case .invalidArgument:
            return TripTandemErrorValidation(field: nil)
        default:
            return TripTandemErrorUnknown(causeType: String(nsError.code))
        }
    }
    if nsError.domain == AuthErrorDomain,
       let authCode = AuthErrorCode(rawValue: nsError.code) {
        switch authCode {
        case .requiresRecentLogin:
            return TripTandemErrorReauthenticationRequired.shared
        case .invalidEmail, .wrongPassword, .userNotFound, .weakPassword:
            return TripTandemErrorValidation(field: "credentials")
        case .emailAlreadyInUse, .credentialAlreadyInUse, .accountExistsWithDifferentCredential:
            return TripTandemErrorAuthConflict.shared
        case .networkError:
            return TripTandemErrorOffline.shared
        case .webContextCancelled:
            return TripTandemErrorAuthCancelled.shared
        default:
            break
        }
    }
    if nsError.localizedDescription.localizedCaseInsensitiveContains("cancel") {
        return TripTandemErrorAuthCancelled.shared
    }
    if nsError.domain == NSURLErrorDomain {
        return TripTandemErrorOffline.shared
    }
    // Firebase Functions exposes the domain through the SDK constant
    // `FunctionsErrorDomain` (currently `com.firebase.functions`). Keep the
    // legacy literal as a compatibility fallback for older SDK builds.
    if nsError.domain == FunctionsErrorDomain || nsError.domain == "FunctionsErrorDomain" {
        switch nsError.code {
        case 16: return TripTandemErrorUnauthenticated.shared
        case 7: return TripTandemErrorPermissionDenied.shared
        case 5: return TripTandemErrorNotFound.shared
        case 6: return TripTandemErrorConflict.shared
        case 3:
            if nsError.localizedDescription.localizedCaseInsensitiveContains("isn't available for itinerary planning") {
                return TripTandemErrorGenerationFailed(failureClass: "safety_blocked")
            }
            return TripTandemErrorValidation(field: nil)
        case 10: return TripTandemErrorConflict.shared
        case 9:
            let message = nsError.localizedDescription.lowercased()
            if message.contains("ownership_required") {
                return TripTandemErrorValidation(field: "ownership")
            }
            if message.contains("organizer_pro_required") {
                return TripTandemErrorEntitlementRequired.shared
            }
            if let failureClass = generationFailureClass(in: message) {
                return TripTandemErrorGenerationFailed(failureClass: failureClass)
            }
            return TripTandemErrorConflict.shared
        case 8: return TripTandemErrorQuotaExceeded.shared
        case 14, 4: return TripTandemErrorOffline.shared
        default: return TripTandemErrorGenerationFailed(failureClass: "functions_\(nsError.code)")
        }
    }
    return TripTandemErrorUnknown(causeType: String(describing: type(of: error)))
}

/** Maps only the server's documented, coarse generation codes. Provider text
 * is never copied into a client error or analytics payload. */
private func generationFailureClass(in message: String) -> String? {
    let known: [(String, String)] = [
        ("ai_generation_disabled", "feature_disabled"),
        ("provider_unconfigured", "provider_unconfigured"),
        ("no_valid_suggestions", "no_valid_suggestions"),
        ("invalid_provider_output", "invalid_provider_output"),
        ("access_revoked", "access_revoked"),
        ("provider_timeout", "provider_timeout"),
        ("provider_rate_limited", "provider_rate_limited"),
        ("provider_error", "provider_error"),
        ("expired", "expired"),
        ("cancelled", "cancelled"),
    ]
    return known.first(where: { message.contains($0.0) })?.1
}

/** Cross-platform email identity adapter. Anonymous users are linked in place
 * so the first-use profile and trips survive account setup. */
final class FirebaseIdentityRepositoryBridge: NSObject, IdentityRepository {
    private let presenter: () -> UIViewController?
    private var appleSignInCoordinator: AppleSignInCoordinator?

    init(presenter: @escaping () -> UIViewController? = { nil }) {
        self.presenter = presenter
        super.init()
    }

    func currentSession(completionHandler: @escaping RepositoryCallback) {
        if let user = authenticatedUser() {
            complete(makeAuthSession(user), completionHandler)
        } else {
            completionHandler(DataResultSuccess<AuthSession>(value: nil), nil)
        }
    }

    func createOrLinkEmail(email: String, password: String, completionHandler: @escaping RepositoryCallback) {
        let trimmed = email.trimmingCharacters(in: .whitespacesAndNewlines)
        guard validEmail(trimmed) else { fail(TripTandemErrorValidation(field: "email"), completionHandler); return }
        guard password.count >= 6 else { fail(TripTandemErrorValidation(field: "password"), completionHandler); return }
        let credential = EmailAuthProvider.credential(withEmail: trimmed, password: password)
        if let user = authenticatedUser(), user.isAnonymous {
            user.link(with: credential) { result, error in
                if let error { fail(mapError(error), completionHandler); return }
                guard let linked = result?.user else { fail(TripTandemErrorUnknown(causeType: "auth_user_missing"), completionHandler); return }
                complete(makeAuthSession(linked), completionHandler)
            }
        } else {
            Auth.auth().createUser(withEmail: trimmed, password: password) { result, error in
                if let error { fail(mapError(error), completionHandler); return }
                guard let user = result?.user else { fail(TripTandemErrorUnknown(causeType: "auth_user_missing"), completionHandler); return }
                complete(makeAuthSession(user), completionHandler)
            }
        }
    }

    func signInWithEmail(email: String, password: String, completionHandler: @escaping RepositoryCallback) {
        let trimmed = email.trimmingCharacters(in: .whitespacesAndNewlines)
        guard validEmail(trimmed) else { fail(TripTandemErrorValidation(field: "email"), completionHandler); return }
        guard password.count >= 6 else { fail(TripTandemErrorValidation(field: "password"), completionHandler); return }
        Auth.auth().signIn(withEmail: trimmed, password: password) { result, error in
            if let error { fail(mapError(error), completionHandler); return }
            guard let user = result?.user else { fail(TripTandemErrorUnknown(causeType: "auth_user_missing"), completionHandler); return }
            complete(makeAuthSession(user), completionHandler)
        }
    }

    func signInWithGoogle(completionHandler: @escaping RepositoryCallback) {
        DispatchQueue.main.async { [weak self] in
            guard let self, let presentingViewController = self.presenter() else {
                fail(TripTandemErrorUnknown(causeType: "auth_unavailable"), completionHandler)
                return
            }
            guard let clientID = FirebaseApp.app()?.options.clientID else {
                fail(TripTandemErrorUnknown(causeType: "google_client_id_missing"), completionHandler)
                return
            }

            GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)
            GIDSignIn.sharedInstance.signIn(withPresenting: presentingViewController) { [weak self] result, error in
                guard let self else { return }
                if let error {
                    fail(mapError(error), completionHandler)
                    return
                }
                guard let user = result?.user,
                      let idToken = user.idToken?.tokenString else {
                    fail(TripTandemErrorUnknown(causeType: "google_token_missing"), completionHandler)
                    return
                }
                let credential = GoogleAuthProvider.credential(
                    withIDToken: idToken,
                    accessToken: user.accessToken.tokenString,
                )
                self.authenticate(credential: credential, completionHandler: completionHandler)
            }
        }
    }

    func signInWithApple(completionHandler: @escaping RepositoryCallback) {
        DispatchQueue.main.async { [weak self] in
            guard let self, let presentingViewController = self.presenter() else {
                fail(TripTandemErrorUnknown(causeType: "auth_unavailable"), completionHandler)
                return
            }

            self.appleSignInCoordinator = AppleSignInCoordinator(presenting: presentingViewController) { [weak self] result in
                guard let self else { return }
                self.appleSignInCoordinator = nil
                switch result {
                case .failure(let error):
                    fail(mapError(error), completionHandler)
                case .success(let appleAuthorization):
                    let appleCredential = appleAuthorization.credential
                    guard let identityToken = appleCredential.identityToken,
                          let idToken = String(data: identityToken, encoding: .utf8),
                          !appleAuthorization.rawNonce.isEmpty else {
                        fail(TripTandemErrorUnknown(causeType: "apple_token_missing"), completionHandler)
                        return
                    }
                    let credential = OAuthProvider.appleCredential(
                        withIDToken: idToken,
                        rawNonce: appleAuthorization.rawNonce,
                        fullName: appleCredential.fullName,
                    )
                    self.authenticate(credential: credential, completionHandler: completionHandler)
                }
            }
            self.appleSignInCoordinator?.start()
        }
    }

    private func authenticate(
        credential: AuthCredential,
        completionHandler: @escaping RepositoryCallback,
    ) {
        if let user = authenticatedUser(), user.isAnonymous {
            user.link(with: credential) { result, error in
                if let error {
                    fail(mapError(error), completionHandler)
                    return
                }
                guard let linked = result?.user else {
                    fail(TripTandemErrorUnknown(causeType: "auth_user_missing"), completionHandler)
                    return
                }
                complete(makeAuthSession(linked), completionHandler)
            }
        } else {
            Auth.auth().signIn(with: credential) { result, error in
                if let error {
                    fail(mapError(error), completionHandler)
                    return
                }
                guard let user = result?.user else {
                    fail(TripTandemErrorUnknown(causeType: "auth_user_missing"), completionHandler)
                    return
                }
                complete(makeAuthSession(user), completionHandler)
            }
        }
    }

    func signOut(completionHandler: @escaping RepositoryCallback) {
        do {
            try Auth.auth().signOut()
            completeUnit(completionHandler)
        } catch {
            fail(mapError(error), completionHandler)
        }
    }
}

@available(iOS 13.0, *)
private struct AppleAuthorizationResult {
    let credential: ASAuthorizationAppleIDCredential
    let rawNonce: String
}

@available(iOS 13.0, *)
private final class AppleSignInCoordinator: NSObject, ASAuthorizationControllerDelegate, ASAuthorizationControllerPresentationContextProviding {
    private let presentingViewController: UIViewController
    private let completion: (Result<AppleAuthorizationResult, Error>) -> Void
    private var authorizationController: ASAuthorizationController?
    private var rawNonce: String?

    init(
        presenting: UIViewController,
        completion: @escaping (Result<AppleAuthorizationResult, Error>) -> Void,
    ) {
        self.presentingViewController = presenting
        self.completion = completion
        super.init()
    }

    func start() {
        let nonce = randomAuthNonce()
        rawNonce = nonce
        let request = ASAuthorizationAppleIDProvider().createRequest()
        request.requestedScopes = [.fullName, .email]
        request.nonce = sha256(nonce)

        let controller = ASAuthorizationController(authorizationRequests: [request])
        controller.delegate = self
        controller.presentationContextProvider = self
        authorizationController = controller
        controller.performRequests()
    }

    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        if let window = presentingViewController.viewIfLoaded?.window {
            return window
        }
        if let window = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene })
            .flatMap(\.windows)
            .first(where: { $0.isKeyWindow }) {
            return window
        }
        return UIWindow(frame: UIScreen.main.bounds)
    }

    func authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithAuthorization authorization: ASAuthorization,
    ) {
        guard let credential = authorization.credential as? ASAuthorizationAppleIDCredential else {
            completion(.failure(AppleSignInCancelled()))
            return
        }
        // Keep the raw nonce on the coordinator until Firebase accepts the
        // token; Apple only returns the hashed nonce in the request flow.
        completion(.success(AppleAuthorizationResult(credential: credential, rawNonce: rawNonce ?? "")))
    }

    func authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithError error: Error,
    ) {
        if let authorizationError = error as? ASAuthorizationError,
           authorizationError.code == .canceled {
            completion(.failure(AppleSignInCancelled()))
        } else {
            completion(.failure(error))
        }
    }
}

/** UserDefaults-backed draft storage keeps unfinished trip details available
 * after a process restart without sending them to Firebase. */
final class IOSTripDraftRepository: NSObject, TripDraftRepository {
    private let defaults = UserDefaults.standard
    private let prefix = "triptandem.tripDraft."

    func load() -> TripDraftSnapshot? {
        guard defaults.bool(forKey: prefix + "present") else { return nil }
        let visibilityValue = defaults.string(forKey: prefix + "visibility") ?? TripVisibility.private_.wireValue
        let paceValue = defaults.string(forKey: prefix + "pace") ?? TripPace.balanced.wireValue
        let budgetValue = defaults.string(forKey: prefix + "budgetBand") ?? BudgetBand.moderate.wireValue
        let visibility = TripVisibility.entries.first { $0.wireValue == visibilityValue } ?? TripVisibility.private_
        let pace = TripPace.entries.first { $0.wireValue == paceValue } ?? TripPace.balanced
        let budget: BudgetBand = {
            switch budgetValue {
            case "mid": return .moderate
            case "flexible": return .comfort
            default: return BudgetBand.entries.first { $0.wireValue == budgetValue } ?? .moderate
            }
        }()
        return TripDraftSnapshot(
            title: defaults.string(forKey: prefix + "title") ?? "",
            destination: defaults.string(forKey: prefix + "destination") ?? "",
            startDate: defaults.string(forKey: prefix + "startDate") ?? "",
            endDate: defaults.string(forKey: prefix + "endDate") ?? "",
            destinationTimezone: defaults.string(forKey: prefix + "timezone") ?? "UTC",
            datesFlexible: defaults.bool(forKey: prefix + "flexible"),
            visibility: visibility,
            capacity: Int32(defaults.integer(forKey: prefix + "capacity")).clamped(to: 2...12),
            pace: pace,
            budgetBand: budget,
            currency: defaults.string(forKey: prefix + "currency") ?? "USD",
            expectationNote: defaults.string(forKey: prefix + "expectation") ?? "",
            interests: defaults.stringArray(forKey: prefix + "interests") ?? [],
            coverColor: defaults.string(forKey: prefix + "coverColor") ?? "#E8704A",
        )
    }

    func save(snapshot: TripDraftSnapshot) {
        defaults.set(true, forKey: prefix + "present")
        defaults.set(snapshot.title, forKey: prefix + "title")
        defaults.set(snapshot.destination, forKey: prefix + "destination")
        defaults.set(snapshot.startDate, forKey: prefix + "startDate")
        defaults.set(snapshot.endDate, forKey: prefix + "endDate")
        defaults.set(snapshot.destinationTimezone, forKey: prefix + "timezone")
        defaults.set(snapshot.datesFlexible, forKey: prefix + "flexible")
        defaults.set(snapshot.visibility.wireValue, forKey: prefix + "visibility")
        defaults.set(snapshot.capacity, forKey: prefix + "capacity")
        defaults.set(snapshot.pace.wireValue, forKey: prefix + "pace")
        defaults.set(snapshot.budgetBand.wireValue, forKey: prefix + "budgetBand")
        defaults.set(snapshot.currency, forKey: prefix + "currency")
        defaults.set(snapshot.expectationNote, forKey: prefix + "expectation")
        defaults.set(snapshot.interests, forKey: prefix + "interests")
        defaults.set(snapshot.coverColor, forKey: prefix + "coverColor")
    }

    func clear() {
        ["present", "title", "destination", "startDate", "endDate", "timezone", "flexible", "visibility", "capacity", "pace", "budgetBand", "currency", "expectation", "interests", "coverColor"].forEach {
            defaults.removeObject(forKey: prefix + $0)
        }
    }
}

/** UserDefaults-backed generation drafts and active job IDs. Provider prompts
 * and generated content are deliberately never persisted here. */
final class IOSTripGenerationDraftRepository: NSObject, ItineraryGenerationDraftRepository {
    private let defaults = UserDefaults.standard
    private let prefix = "triptandem.generationDraft."

    func load(tripId: String) -> ItineraryGenerationInput? {
        let key = prefix + tripId
        guard let data = defaults.dictionary(forKey: key) else { return nil }
        let scope = GenerationScope.entries.first { $0.wireValue == (data["scope"] as? String) } ?? .wholetrip
        let pace = TripPace.entries.first { $0.wireValue == (data["pace"] as? String) }
        let budget = BudgetBand.entries.first { $0.wireValue == (data["budgetBand"] as? String) }
        return ItineraryGenerationInput(
            scope: scope,
            dayDate: data["dayDate"] as? String,
            pace: pace,
            budgetBand: budget,
            interests: data["interests"] as? [String] ?? [],
            dailyStartLabel: data["dailyStartLabel"] as? String,
            dailyEndLabel: data["dailyEndLabel"] as? String,
            accessibilityNotes: data["accessibilityNotes"] as? String,
            dietNotes: data["dietNotes"] as? String,
            lockedItemIds: data["lockedItemIds"] as? [String] ?? [],
        )
    }

    func save(tripId: String, input: ItineraryGenerationInput) {
        var data: [String: Any] = [
            "scope": input.scope.wireValue,
            "interests": input.interests,
            "lockedItemIds": input.lockedItemIds,
        ]
        if let value = input.dayDate { data["dayDate"] = value }
        if let value = input.pace?.wireValue { data["pace"] = value }
        if let value = input.budgetBand?.wireValue { data["budgetBand"] = value }
        if let value = input.dailyStartLabel { data["dailyStartLabel"] = value }
        if let value = input.dailyEndLabel { data["dailyEndLabel"] = value }
        if let value = input.accessibilityNotes { data["accessibilityNotes"] = value }
        if let value = input.dietNotes { data["dietNotes"] = value }
        defaults.set(data, forKey: prefix + tripId)
    }

    func clear(tripId: String) {
        defaults.removeObject(forKey: prefix + tripId)
        defaults.removeObject(forKey: activeJobKey(tripId))
    }

    func loadActiveJobId(tripId: String) -> String? {
        guard let value = defaults.string(forKey: activeJobKey(tripId))?.trimmingCharacters(in: .whitespacesAndNewlines),
              !value.isEmpty else { return nil }
        return value
    }

    func saveActiveJobId(tripId: String, jobId: String) {
        defaults.set(jobId.trimmingCharacters(in: .whitespacesAndNewlines), forKey: activeJobKey(tripId))
    }

    func clearActiveJobId(tripId: String) {
        defaults.removeObject(forKey: activeJobKey(tripId))
    }

    private func activeJobKey(_ tripId: String) -> String { prefix + "activeJob." + tripId }
}

private extension Int32 {
    func clamped(to range: ClosedRange<Int32>) -> Int32 { Swift.min(Swift.max(self, range.lowerBound), range.upperBound) }
}

private func string(_ data: [String: Any], _ key: String) -> String? {
    data[key] as? String
}

private func bool(_ data: [String: Any], _ key: String) -> Bool? {
    (data[key] as? NSNumber)?.boolValue
}

private func int(_ data: [String: Any], _ key: String) -> Int32? {
    (data[key] as? NSNumber).map { $0.int32Value }
}

private func long(_ data: [String: Any], _ key: String) -> Int64? {
    (data[key] as? NSNumber).map { $0.int64Value }
}

private func epoch(_ value: Any?) -> Int64? {
    if let timestamp = value as? Timestamp {
        return Int64(timestamp.dateValue().timeIntervalSince1970 * 1000)
    }
    if let date = value as? Date {
        return Int64(date.timeIntervalSince1970 * 1000)
    }
    if let number = value as? NSNumber {
        return number.int64Value
    }
    return nil
}

private func kotlinLong(_ value: Int64?) -> KotlinLong? {
    value.map { KotlinLong(longLong: $0) }
}

private func timestamp(_ value: KotlinLong?) -> Timestamp? {
    guard let value else { return nil }
    return Timestamp(date: Date(timeIntervalSince1970: TimeInterval(value.int64Value) / 1000))
}

private func strings(_ data: [String: Any], _ key: String) -> [String] {
    (data[key] as? [String]) ?? []
}

private func pace(_ value: Any?) -> TripPace? {
    guard let value = value as? String else { return nil }
    return TripPace.entries.first { $0.wireValue == value }
}

private func budget(_ value: Any?) -> BudgetBand? {
    guard let value = value as? String else { return nil }
    return BudgetBand.entries.first { $0.wireValue == value }
}

private func profileVisibility(_ value: Any?) -> ProfileVisibility {
    guard let value = value as? String else { return .private_ }
    return ProfileVisibility.entries.first { $0.wireValue == value } ?? .private_
}

private func tripVisibility(_ value: Any?) -> TripVisibility {
    guard let value = value as? String else { return .private_ }
    return TripVisibility.entries.first { $0.wireValue == value } ?? .private_
}

private func tripStatus(_ value: Any?) -> TripStatus {
    guard let value = value as? String else { return .planning }
    return TripStatus.entries.first { $0.wireValue == value } ?? .planning
}

private func memberRole(_ value: Any?) -> TripMemberRole {
    guard let value = value as? String else { return .viewer }
    return TripMemberRole.entries.first { $0.wireValue == value } ?? .viewer
}

private func memberStatus(_ value: Any?) -> MembershipStatus {
    guard let value = value as? String else { return .active }
    return MembershipStatus.entries.first { $0.wireValue == value } ?? .active
}

private func itineraryType(_ value: Any?) -> ItineraryItemType {
    guard let value = value as? String else { return .activity }
    return ItineraryItemType.entries.first { $0.wireValue == value } ?? .activity
}

private func itineraryStatus(_ value: Any?) -> ItineraryItemStatus {
    guard let value = value as? String else { return .planned }
    return ItineraryItemStatus.entries.first { $0.wireValue == value } ?? .planned
}

private func itineraryVisibility(_ value: Any?) -> ItineraryVisibility {
    guard let value = value as? String else { return .members }
    return ItineraryVisibility.entries.first { $0.wireValue == value } ?? .members
}

private func accountStatus(_ value: Any?) -> AccountStatus {
    guard let value = value as? String else { return .active }
    return AccountStatus.entries.first { $0.wireValue == value } ?? .active
}

private func makeProfile(_ snapshot: DocumentSnapshot) -> TravelerProfile {
    let data = snapshot.data() ?? [:]
    return TravelerProfile(
        uid: string(data, "uid") ?? snapshot.documentID,
        displayName: string(data, "displayName") ?? "Traveler",
        avatarUrl: string(data, "avatarUrl"),
        bio: string(data, "bio"),
        homeRegion: string(data, "homeRegion"),
        primaryLanguage: string(data, "primaryLanguage") ?? "English",
        ageConfirmed: bool(data, "ageConfirmed") ?? false,
        pace: pace(data["pace"]),
        budgetBand: budget(data["budgetBand"]),
        visibility: profileVisibility(data["visibility"]),
        additionalLanguages: strings(data, "additionalLanguages"),
        interests: strings(data, "interests"),
        termsVersion: string(data, "termsVersion") ?? PolicyVersions.shared.TERMS,
        privacyVersion: string(data, "privacyVersion") ?? PolicyVersions.shared.PRIVACY,
        consentedAtEpochMillis: kotlinLong(epoch(data["consentedAt"])),
        accountStatus: accountStatus(data["accountStatus"]),
        createdAtEpochMillis: kotlinLong(epoch(data["createdAt"])),
        updatedAtEpochMillis: kotlinLong(epoch(data["updatedAt"])),
    )
}

private func makeTrip(_ snapshot: DocumentSnapshot) -> TripRecord {
    let data = snapshot.data() ?? [:]
    return TripRecord(
        id: snapshot.documentID,
        ownerId: string(data, "ownerId") ?? "",
        title: string(data, "title") ?? "Untitled trip",
        destination: string(data, "destination") ?? "",
        startDate: string(data, "startDate") ?? "",
        endDate: string(data, "endDate") ?? "",
        destinationTimezone: string(data, "destinationTimezone") ?? "UTC",
        datesFlexible: bool(data, "datesFlexible") ?? false,
        visibility: tripVisibility(data["visibility"]),
        capacity: int(data, "capacity") ?? 2,
        status: tripStatus(data["status"]),
        currency: string(data, "currency"),
        budgetBand: budget(data["budgetBand"]),
        pace: pace(data["pace"]),
        expectationNote: string(data, "expectationNote"),
        revision: int(data, "revision") ?? 0,
        activeMemberCount: int(data, "activeMemberCount") ?? 1,
        interests: strings(data, "interests"),
        coverColor: string(data, "coverColor"),
        createdAtEpochMillis: kotlinLong(epoch(data["createdAt"])),
        updatedAtEpochMillis: kotlinLong(epoch(data["updatedAt"])),
    )
}

private func makeItem(_ snapshot: DocumentSnapshot, tripId: String) -> ItineraryItem {
    let data = snapshot.data() ?? [:]
    let startEpoch = epoch(data["startTime"])
    return ItineraryItem(
        id: snapshot.documentID,
        tripId: tripId,
        type: itineraryType(data["type"]),
        title: string(data, "title") ?? "Untitled item",
        startTimeEpochMillis: kotlinLong(startEpoch),
        startTimeLabel: string(data, "startTimeLabel"),
        durationMinutes: int(data, "durationMinutes") ?? 0,
        place: string(data, "place"),
        note: string(data, "note"),
        status: itineraryStatus(data["status"]),
        visibility: itineraryVisibility(data["visibility"]),
        position: int(data, "position") ?? 0,
        revision: int(data, "revision") ?? 0,
        lastEditedBy: string(data, "lastEditedBy"),
        updatedAtEpochMillis: kotlinLong(epoch(data["updatedAt"])),
        dayDate: string(data, "dayDate"),
        deletedAtEpochMillis: kotlinLong(epoch(data["deletedAt"])),
        undoExpiresAtEpochMillis: kotlinLong(epoch(data["undoExpiresAt"])),
        flexibleTime: bool(data, "flexibleTime") ?? (startEpoch == nil || startEpoch == 0),
        generatedBy: string(data, "generatedBy"),
        generationJobId: string(data, "generationJobId"),
    )
}

private func makeMember(_ snapshot: DocumentSnapshot, tripId: String) -> TripMember {
    let data = snapshot.data() ?? [:]
    return TripMember(
        tripId: tripId,
        userId: string(data, "userId") ?? snapshot.documentID,
        displayName: string(data, "displayName") ?? "Traveler",
        role: memberRole(data["role"]),
        status: memberStatus(data["status"]),
        inviteId: string(data, "inviteId"),
        joinedAtEpochMillis: kotlinLong(epoch(data["joinedAt"])),
        updatedAtEpochMillis: kotlinLong(epoch(data["updatedAt"])),
    )
}

private func makeInvite(_ snapshot: DocumentSnapshot, tripId: String, shareToken: String) -> InviteLink {
    let data = snapshot.data() ?? [:]
    return InviteLink(
        id: snapshot.documentID,
        tripId: tripId,
        role: memberRole(data["role"]),
        expiresAtEpochMillis: epoch(data["expiresAt"]) ?? 0,
        maxUses: int(data, "maxUses") ?? 1,
        uses: int(data, "uses") ?? 0,
        revoked: data["revokedAt"] != nil && !(data["revokedAt"] is NSNull),
        shareToken: shareToken,
        createdAtEpochMillis: kotlinLong(epoch(data["createdAt"])),
        tripTitle: string(data, "tripTitle"),
        destination: string(data, "previewDestination"),
        startDate: string(data, "previewStartDate"),
        endDate: string(data, "previewEndDate"),
        inviterDisplayName: string(data, "inviterDisplayName"),
    )
}

private func profileData(_ input: SaveTravelerProfileInput, uid: String, createdAt: Any) -> [String: Any] {
    var data: [String: Any] = [
        "uid": uid,
        "displayName": input.displayName.trimmingCharacters(in: .whitespacesAndNewlines),
        "primaryLanguage": input.primaryLanguage,
        "ageConfirmed": input.ageConfirmed,
        "visibility": input.visibility.wireValue,
        "additionalLanguages": input.additionalLanguages,
        "interests": input.interests,
        "termsVersion": input.termsVersion,
        "privacyVersion": input.privacyVersion,
        "consentedAt": FieldValue.serverTimestamp(),
        "accountStatus": AccountStatus.active.wireValue,
        "createdAt": createdAt,
        "updatedAt": FieldValue.serverTimestamp(),
    ]
    if let value = input.avatarUrl?.trimmingCharacters(in: .whitespacesAndNewlines), !value.isEmpty { data["avatarUrl"] = value }
    if let value = input.bio?.trimmingCharacters(in: .whitespacesAndNewlines), !value.isEmpty { data["bio"] = value }
    data["homeRegion"] = input.homeRegion?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
    if let value = input.pace { data["pace"] = value.wireValue }
    if let value = input.budgetBand { data["budgetBand"] = value.wireValue }
    return data
}

/** Fields allowed to change after onboarding; immutable consent metadata stays server-owned. */
private func profileMutableData(_ input: SaveTravelerProfileInput) -> [String: Any] {
    let displayName = input.displayName.trimmingCharacters(in: .whitespacesAndNewlines)
    let region = input.homeRegion?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
    let language = input.primaryLanguage.trimmingCharacters(in: .whitespacesAndNewlines)
    var data: [String: Any] = [
        "displayName": displayName,
        "homeRegion": region,
        "primaryLanguage": language.isEmpty ? "English" : language,
        "visibility": input.visibility.wireValue,
        "additionalLanguages": input.additionalLanguages,
        "interests": input.interests,
        "updatedAt": FieldValue.serverTimestamp(),
    ]
    data["avatarUrl"] = input.avatarUrl?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false
        ? input.avatarUrl!.trimmingCharacters(in: .whitespacesAndNewlines)
        : FieldValue.delete()
    data["bio"] = input.bio?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false
        ? input.bio!.trimmingCharacters(in: .whitespacesAndNewlines)
        : FieldValue.delete()
    data["pace"] = input.pace?.wireValue ?? FieldValue.delete()
    data["budgetBand"] = input.budgetBand?.wireValue ?? FieldValue.delete()
    return data
}

private func profileValidationError(_ input: SaveTravelerProfileInput) -> TripTandemError? {
    let displayName = input.displayName.trimmingCharacters(in: .whitespacesAndNewlines)
    let region = input.homeRegion?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
    let language = input.primaryLanguage.trimmingCharacters(in: .whitespacesAndNewlines)
    switch true {
    case !(2...40).contains(displayName.count): return TripTandemErrorValidation(field: "displayName")
    case !input.ageConfirmed: return TripTandemErrorValidation(field: "ageConfirmed")
    case region.isEmpty || region.count > 80: return TripTandemErrorValidation(field: "homeRegion")
    case !(2...80).contains(language.count): return TripTandemErrorValidation(field: "primaryLanguage")
    case (input.avatarUrl?.count ?? 0) > 2048: return TripTandemErrorValidation(field: "avatarUrl")
    case (input.bio?.count ?? 0) > 240: return TripTandemErrorValidation(field: "bio")
    case input.additionalLanguages.count > 5: return TripTandemErrorValidation(field: "additionalLanguages")
    case input.interests.count > 12: return TripTandemErrorValidation(field: "interests")
    case input.termsVersion.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty: return TripTandemErrorValidation(field: "consent")
    case input.privacyVersion.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty: return TripTandemErrorValidation(field: "consent")
    default: return nil
    }
}

private func strictISODate(_ value: String) -> Date? {
    guard value.count == 10, value[value.index(value.startIndex, offsetBy: 4)] == "-", value[value.index(value.startIndex, offsetBy: 7)] == "-" else { return nil }
    let formatter = DateFormatter()
    formatter.calendar = Calendar(identifier: .gregorian)
    formatter.locale = Locale(identifier: "en_US_POSIX")
    formatter.timeZone = TimeZone(secondsFromGMT: 0)
    formatter.dateFormat = "yyyy-MM-dd"
    guard let date = formatter.date(from: value), formatter.string(from: date) == value else { return nil }
    return date
}

private func tripValidationError(_ input: CreateTripInput) -> TripTandemError? {
    let title = input.title.trimmingCharacters(in: .whitespacesAndNewlines)
    let destination = input.destination.trimmingCharacters(in: .whitespacesAndNewlines)
    guard (2...120).contains(title.count) else { return TripTandemErrorValidation(field: "title") }
    guard (1...160).contains(destination.count) else { return TripTandemErrorValidation(field: "destination") }
    guard let start = strictISODate(input.startDate), let end = strictISODate(input.endDate), start <= end else { return TripTandemErrorValidation(field: "dates") }
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = TimeZone(secondsFromGMT: 0)!
    let length = (calendar.dateComponents([.day], from: start, to: end).day ?? -1) + 1
    guard (1...60).contains(length) else { return TripTandemErrorValidation(field: "dates") }
    guard (1...80).contains(input.destinationTimezone.trimmingCharacters(in: .whitespacesAndNewlines).count) else { return TripTandemErrorValidation(field: "destinationTimezone") }
    guard (2...12).contains(Int(input.capacity)) else { return TripTandemErrorValidation(field: "capacity") }
    if let currency = input.currency, currency.range(of: "^[A-Z]{3}$", options: .regularExpression) == nil { return TripTandemErrorValidation(field: "currency") }
    if (input.expectationNote?.count ?? 0) > 500 { return TripTandemErrorValidation(field: "expectationNote") }
    if input.interests.count > 12 { return TripTandemErrorValidation(field: "interests") }
    if let coverColor = input.coverColor, coverColor.range(of: "^#[0-9A-Fa-f]{6}$", options: .regularExpression) == nil { return TripTandemErrorValidation(field: "coverColor") }
    return nil
}

private func tripValidationError(_ input: UpdateTripInput) -> TripTandemError? {
    let createEquivalent = CreateTripInput(
        title: input.title,
        destination: input.destination,
        startDate: input.startDate,
        endDate: input.endDate,
        destinationTimezone: input.destinationTimezone,
        datesFlexible: input.datesFlexible,
        visibility: input.visibility,
        capacity: input.capacity,
        status: input.status,
        currency: input.currency,
        budgetBand: input.budgetBand,
        pace: input.pace,
        expectationNote: input.expectationNote,
        interests: input.interests,
        coverColor: input.coverColor,
    )
    return tripValidationError(createEquivalent)
}

private func itineraryValidationError(_ input: CreateItineraryItemInput) -> TripTandemError? {
    guard (1...160).contains(input.title.trimmingCharacters(in: .whitespacesAndNewlines).count) else { return TripTandemErrorValidation(field: "title") }
    guard (0...1440).contains(Int(input.durationMinutes)) else { return TripTandemErrorValidation(field: "durationMinutes") }
    if !input.flexibleTime && input.startTimeEpochMillis == nil { return TripTandemErrorValidation(field: "startTime") }
    if (input.startTimeLabel?.count ?? 0) > 32 { return TripTandemErrorValidation(field: "startTimeLabel") }
    if (input.place?.count ?? 0) > 200 { return TripTandemErrorValidation(field: "place") }
    if (input.note?.count ?? 0) > 1000 { return TripTandemErrorValidation(field: "note") }
    return nil
}

private func itineraryValidationError(_ input: UpdateItineraryItemInput) -> TripTandemError? {
    guard (1...160).contains(input.title.trimmingCharacters(in: .whitespacesAndNewlines).count) else { return TripTandemErrorValidation(field: "title") }
    guard (0...1440).contains(Int(input.durationMinutes)) else { return TripTandemErrorValidation(field: "durationMinutes") }
    guard (0...500).contains(Int(input.position)) else { return TripTandemErrorValidation(field: "position") }
    if !input.flexibleTime && input.startTimeEpochMillis == nil { return TripTandemErrorValidation(field: "startTime") }
    if (input.startTimeLabel?.count ?? 0) > 32 { return TripTandemErrorValidation(field: "startTimeLabel") }
    if (input.place?.count ?? 0) > 200 { return TripTandemErrorValidation(field: "place") }
    if (input.note?.count ?? 0) > 1000 { return TripTandemErrorValidation(field: "note") }
    return nil
}

private func inviteValidationError(_ input: CreateInviteInput) -> TripTandemError? {
    guard input.role != TripMemberRole.owner else { return TripTandemErrorValidation(field: "role") }
    guard (1...(24 * 30)).contains(Int(input.expiresInHours)) else { return TripTandemErrorValidation(field: "expiresInHours") }
    guard (1...20).contains(Int(input.maxUses)) else { return TripTandemErrorValidation(field: "maxUses") }
    return nil
}

private func tripCallableData(_ input: CreateTripInput) -> [String: Any] {
    var data: [String: Any] = [
        "title": input.title.trimmingCharacters(in: .whitespacesAndNewlines),
        "destination": input.destination.trimmingCharacters(in: .whitespacesAndNewlines),
        "startDate": input.startDate,
        "endDate": input.endDate,
        "destinationTimezone": input.destinationTimezone,
        "datesFlexible": input.datesFlexible,
        "visibility": input.visibility.wireValue,
        "capacity": input.capacity,
        "status": input.status.wireValue,
        "interests": input.interests,
    ]
    if let value = input.currency { data["currency"] = value }
    if let value = input.budgetBand { data["budgetBand"] = value.wireValue }
    if let value = input.pace { data["pace"] = value.wireValue }
    if let value = input.expectationNote { data["expectationNote"] = value }
    if let value = input.coverColor { data["coverColor"] = value }
    return data
}

private func tripUpdateCallableData(_ input: UpdateTripInput) -> [String: Any] {
    var data: [String: Any] = [
        "title": input.title.trimmingCharacters(in: .whitespacesAndNewlines),
        "destination": input.destination.trimmingCharacters(in: .whitespacesAndNewlines),
        "startDate": input.startDate,
        "endDate": input.endDate,
        "destinationTimezone": input.destinationTimezone,
        "datesFlexible": input.datesFlexible,
        "visibility": input.visibility.wireValue,
        "capacity": input.capacity,
        "status": input.status.wireValue,
        "interests": input.interests,
    ]
    if let value = input.currency?.trimmingCharacters(in: .whitespacesAndNewlines), !value.isEmpty { data["currency"] = value }
    if let value = input.budgetBand { data["budgetBand"] = value.wireValue }
    if let value = input.pace { data["pace"] = value.wireValue }
    if let value = input.expectationNote?.trimmingCharacters(in: .whitespacesAndNewlines), !value.isEmpty { data["expectationNote"] = value }
    if let value = input.coverColor?.trimmingCharacters(in: .whitespacesAndNewlines), !value.isEmpty { data["coverColor"] = value }
    return data
}

private func itemData(_ input: CreateItineraryItemInput, uid: String) -> [String: Any] {
    var data: [String: Any] = [
        "type": input.type.wireValue,
        "title": input.title.trimmingCharacters(in: .whitespacesAndNewlines),
        "startTime": timestamp(input.startTimeEpochMillis) ?? Timestamp(date: Date(timeIntervalSince1970: 0)),
        "flexibleTime": input.flexibleTime,
        "durationMinutes": input.durationMinutes,
        "status": input.status.wireValue,
        "visibility": input.visibility.wireValue,
        "position": input.position,
        "revision": 0,
        "lastEditedBy": uid,
        "createdAt": FieldValue.serverTimestamp(),
        "updatedAt": FieldValue.serverTimestamp(),
    ]
    if let value = input.startTimeLabel { data["startTimeLabel"] = value }
    if let value = input.place { data["place"] = value }
    if let value = input.note { data["note"] = value }
    if let value = input.dayDate { data["dayDate"] = value }
    return data
}

private func itemUpdateData(_ input: UpdateItineraryItemInput, uid: String) -> [String: Any] {
    [
        "type": input.type.wireValue,
        "title": input.title.trimmingCharacters(in: .whitespacesAndNewlines),
        "startTime": timestamp(input.startTimeEpochMillis) ?? Timestamp(date: Date(timeIntervalSince1970: 0)),
        "flexibleTime": input.flexibleTime,
        "startTimeLabel": input.startTimeLabel ?? FieldValue.delete(),
        "durationMinutes": input.durationMinutes,
        "place": input.place ?? FieldValue.delete(),
        "note": input.note ?? FieldValue.delete(),
        "status": input.status.wireValue,
        "visibility": input.visibility.wireValue,
        "position": input.position,
        "revision": input.expectedRevision + 1,
        "lastEditedBy": uid,
        "dayDate": input.dayDate ?? FieldValue.delete(),
        "updatedAt": FieldValue.serverTimestamp(),
    ]
}

final class FirebaseTravelerProfileRepositoryBridge: NSObject, TravelerProfileRepository {
    private let db = Firestore.firestore()
    private let functions = Functions.functions(region: "asia-southeast2")

    func getCurrentProfile(completionHandler: @escaping RepositoryCallback) {
        guard let user = authenticatedUser() else {
            // A fresh anonymous session can finish after the Compose host is
            // created. Treat that moment as an empty profile; the next save
            // will use the authenticated Firebase user.
            completionHandler(DataResultSuccess<TravelerProfile>(value: nil), nil)
            return
        }
        runOptional({
            let snapshot = try await self.db.collection(usersCollection).document(user.uid).getDocument()
            return snapshot.exists ? makeProfile(snapshot) : (nil as TravelerProfile?)
        }, completion: completionHandler)
    }

    func saveCurrentProfile(input: SaveTravelerProfileInput, completionHandler: @escaping RepositoryCallback) {
        guard let user = authenticatedUser() else { unauthenticated(completionHandler); return }
        if let error = profileValidationError(input) { fail(error, completionHandler); return }
        run({
            let reference = self.db.collection(usersCollection).document(user.uid)
            let existing = try await reference.getDocument()
            if existing.exists {
                // Consent, account status, UID, and creation time are
                // immutable under firestore.rules. Updating the full create
                // payload would rewrite consentedAt/accountStatus and make
                // every subsequent profile edit fail closed.
                try await reference.updateData(profileMutableData(input))
            } else {
                try await reference.setData(profileData(input, uid: user.uid, createdAt: FieldValue.serverTimestamp()))
            }
            return makeProfile(try await reference.getDocument())
        }, completion: completionHandler)
    }

    func deleteCurrentAccount(completionHandler: @escaping RepositoryCallback) {
        guard let user = authenticatedUser() else { unauthenticated(completionHandler); return }
        runUnit({
            if let lastSignIn = user.metadata.lastSignInDate,
               Date().timeIntervalSince(lastSignIn) > 5 * 60 {
                throw BridgeRecentLoginRequired()
            }
            let owned = try await self.db.collection(tripsCollection).whereField("ownerId", isEqualTo: user.uid).getDocuments()
            if owned.documents.contains(where: { document in
                let data = document.data()
                let count = int(data, "activeMemberCount") ?? 1
                let status = data["status"] as? String
                return count > 1 && ["draft", "planning", "confirmed"].contains(status)
            }) {
                throw BridgeOwnershipRequired()
            }
            // Firestore parent deletes do not cascade into subcollections. The
            // authenticated server boundary owns trip-graph cleanup, metadata
            // cleanup, and the guarded profile delete as one account operation.
            _ = try await callTripTandemFunction(
                self.functions,
                name: "deleteAccountProfile",
                data: [:],
            )
            try await user.delete()
        }, completion: completionHandler)
    }
}

final class FirebaseTripRepositoryBridge: NSObject, TripRepository {
    private let db = Firestore.firestore()
    private let functions = Functions.functions(region: "asia-southeast2")

    func listMyTrips(completionHandler: @escaping RepositoryCallback) {
        guard let user = authenticatedUser() else { unauthenticated(completionHandler); return }
        runList({
            let owned = try await self.db.collection(tripsCollection).whereField("ownerId", isEqualTo: user.uid).getDocuments()
            let memberships = try await self.db.collectionGroup(membersCollection)
                .whereField("userId", isEqualTo: user.uid)
                .whereField("status", isEqualTo: MembershipStatus.active.wireValue)
                .getDocuments()
            let ids = Array(Set(owned.documents.map(\.documentID) + memberships.documents.compactMap { document in
                document.reference.parent.parent?.documentID
            }))
            var records: [TripRecord] = []
            for id in ids {
                let snapshot: DocumentSnapshot
                if let ownedSnapshot = owned.documents.first(where: { $0.documentID == id }) {
                    snapshot = ownedSnapshot
                } else {
                    snapshot = try await self.db.collection(tripsCollection).document(id).getDocument()
                }
                if snapshot.exists { records.append(makeTrip(snapshot)) }
            }
            return records
        }, completion: completionHandler)
    }

    func createTrip(input: CreateTripInput, completionHandler: @escaping RepositoryCallback) {
        guard authenticatedUser() != nil else { unauthenticated(completionHandler); return }
        if let error = tripValidationError(input) { fail(error, completionHandler); return }
        run({
            let payload = try await callTripTandemFunction(
                self.functions,
                name: "createTrip",
                data: tripCallableData(input),
            )
            guard let tripId = payload["tripId"] as? String, !tripId.isEmpty else {
                throw BridgeInvalidResponse()
            }
            let reference = self.db.collection(tripsCollection).document(tripId)
            let snapshot = try await reference.getDocument()
            guard snapshot.exists else { throw BridgeNotFound() }
            return makeTrip(snapshot)
        }, completion: completionHandler)
    }

    func getTrip(tripId: String, completionHandler: @escaping RepositoryCallback) {
        guard authenticatedUser() != nil else { unauthenticated(completionHandler); return }
        run({
            let snapshot = try await self.db.collection(tripsCollection).document(tripId).getDocument()
            guard snapshot.exists else { throw BridgeNotFound() }
            return makeTrip(snapshot)
        }, completion: completionHandler)
    }

    func updateTrip(tripId: String, input: UpdateTripInput, completionHandler: @escaping RepositoryCallback) {
        guard authenticatedUser() != nil else { unauthenticated(completionHandler); return }
        if let error = tripValidationError(input) { fail(error, completionHandler); return }
        run({
            // Keep status reactivation, capacity expansion, and the revision
            // check inside the same regional server transaction as Android.
            let payload = try await callTripTandemFunction(
                self.functions,
                name: "updateTrip",
                data: [
                    "tripId": tripId,
                    "expectedRevision": input.expectedRevision,
                    "trip": tripUpdateCallableData(input),
                ],
            )
            guard let returnedTripId = payload["tripId"] as? String, returnedTripId == tripId else {
                throw BridgeInvalidResponse()
            }
            let reference = self.db.collection(tripsCollection).document(tripId)
            let snapshot = try await reference.getDocument()
            guard snapshot.exists else { throw BridgeNotFound() }
            return makeTrip(snapshot)
        }, completion: completionHandler)
    }

    func deleteTrip(tripId: String, completionHandler: @escaping RepositoryCallback) {
        guard authenticatedUser() != nil else { unauthenticated(completionHandler); return }
        runUnit({
            // Firestore parent deletes do not cascade into the trip graph. Use
            // the regional callable so the server locks the trip, drains all
            // known subcollections, and only then removes the root.
            let payload = try await callTripTandemFunction(
                self.functions,
                name: "deleteTrip",
                data: ["tripId": tripId],
            )
            guard let returnedTripId = payload["tripId"] as? String, returnedTripId == tripId else {
                throw BridgeInvalidResponse()
            }
        }, completion: completionHandler)
    }
}

private struct BridgeInvalidResponse: Error {}
private struct BridgeNotFound: Error {}
private struct BridgeUndoExpired: Error {}
private struct BridgeRejected: Error {}
private struct BridgeInviteExpired: Error {}
private struct BridgeInviteRevoked: Error {}
private struct BridgeInviteCapacity: Error {}
private struct BridgeOwnershipRequired: Error {}
private struct BridgeRecentLoginRequired: Error {}

final class FirebaseItineraryRepositoryBridge: NSObject, ItineraryRepository {
    private let db = Firestore.firestore()

    private func reference(tripId: String, itemId: String? = nil) -> DocumentReference {
        let collection = db.collection(tripsCollection).document(tripId).collection(itineraryCollection)
        return itemId.map { collection.document($0) } ?? collection.document()
    }

    func listItems(tripId: String, completionHandler: @escaping RepositoryCallback) {
        guard authenticatedUser() != nil else { unauthenticated(completionHandler); return }
        runList({
            let snapshots = try await self.db.collection(tripsCollection).document(tripId)
                .collection(itineraryCollection).order(by: "position").getDocuments()
            return snapshots.documents
                .filter { document in
                    let deleted = document.data()["deletedAt"]
                    return deleted == nil || deleted is NSNull
                }
                .map { makeItem($0, tripId: tripId) }
        }, completion: completionHandler)
    }

    func createItem(tripId: String, input: CreateItineraryItemInput, completionHandler: @escaping RepositoryCallback) {
        guard let user = authenticatedUser() else { unauthenticated(completionHandler); return }
        if let error = itineraryValidationError(input) { fail(error, completionHandler); return }
        run({
            let reference = self.reference(tripId: tripId)
            try await reference.setData(itemData(input, uid: user.uid))
            return makeItem(try await reference.getDocument(), tripId: tripId)
        }, completion: completionHandler)
    }

    func updateItem(tripId: String, itemId: String, input: UpdateItineraryItemInput, completionHandler: @escaping RepositoryCallback) {
        guard let user = authenticatedUser() else { unauthenticated(completionHandler); return }
        if let error = itineraryValidationError(input) { fail(error, completionHandler); return }
        run({
            let reference = self.reference(tripId: tripId, itemId: itemId)
            try await reference.updateData(itemUpdateData(input, uid: user.uid))
            return makeItem(try await reference.getDocument(), tripId: tripId)
        }, completion: completionHandler)
    }

    func deleteItem(tripId: String, itemId: String, completionHandler: @escaping RepositoryCallback) {
        guard let user = authenticatedUser() else { unauthenticated(completionHandler); return }
        runUnit({
            let reference = self.reference(tripId: tripId, itemId: itemId)
            let current = try await reference.getDocument()
            guard current.exists else { throw BridgeNotFound() }
            let currentData = current.data() ?? [:]
            guard currentData["deletedAt"] == nil || currentData["deletedAt"] is NSNull else { throw BridgeNotFound() }
            let revision = (currentData["revision"] as? NSNumber)?.intValue ?? 0
            try await reference.updateData([
                "deletedAt": FieldValue.serverTimestamp(),
                "undoExpiresAt": Timestamp(date: Date().addingTimeInterval(30)),
                "deletedBy": user.uid,
                "revision": revision + 1,
                "updatedAt": FieldValue.serverTimestamp(),
            ])
        }, completion: completionHandler)
    }

    func restoreItem(tripId: String, itemId: String, completionHandler: @escaping RepositoryCallback) {
        guard let user = authenticatedUser() else { unauthenticated(completionHandler); return }
        run({
            let reference = self.reference(tripId: tripId, itemId: itemId)
            let current = try await reference.getDocument()
            guard current.exists else { throw BridgeNotFound() }
            let currentData = current.data() ?? [:]
            let deleted = currentData["deletedAt"]
            if deleted == nil || deleted is NSNull { return makeItem(current, tripId: tripId) }
            guard let expiresAt = epoch(currentData["undoExpiresAt"]), Date().timeIntervalSince1970 * 1000 <= Double(expiresAt) else {
                throw BridgeUndoExpired()
            }
            let revision = (currentData["revision"] as? NSNumber)?.intValue ?? 0
            try await reference.updateData([
                "deletedAt": FieldValue.delete(),
                "undoExpiresAt": FieldValue.delete(),
                "deletedBy": FieldValue.delete(),
                "revision": revision + 1,
                "lastEditedBy": user.uid,
                "updatedAt": FieldValue.serverTimestamp(),
            ])
            return makeItem(try await reference.getDocument(), tripId: tripId)
        }, completion: completionHandler)
    }
}

/** Callable Functions adapter for the server-side AI itinerary job flow. */
final class FirebaseItineraryGenerationRepositoryBridge: NSObject, ItineraryGenerationRepository {
    private let functions = Functions.functions(region: "asia-southeast2")

    func createJob(tripId: String, input: ItineraryGenerationInput, completionHandler: @escaping RepositoryCallback) {
        guard authenticatedUser() != nil else { unauthenticated(completionHandler); return }
        var inputData: [String: Any] = [
            "scope": input.scope.wireValue,
            "interests": input.interests,
            "lockedItemIds": input.lockedItemIds,
        ]
        if let value = input.dayDate, !value.isEmpty { inputData["dayDate"] = value }
        if let value = input.pace?.wireValue { inputData["pace"] = value }
        if let value = input.budgetBand?.wireValue { inputData["budgetBand"] = value }
        if let value = input.dailyStartLabel, !value.isEmpty { inputData["dailyStartLabel"] = value }
        if let value = input.dailyEndLabel, !value.isEmpty { inputData["dailyEndLabel"] = value }
        if let value = input.accessibilityNotes, !value.isEmpty { inputData["accessibilityNotes"] = value }
        if let value = input.dietNotes, !value.isEmpty { inputData["dietNotes"] = value }
        functions.httpsCallable("createItineraryGenerationJob").call(["tripId": tripId, "input": inputData]) { result, error in
            if let error { fail(mapError(error), completionHandler); return }
            guard let data = result?.data as? [String: Any] else {
                fail(TripTandemErrorGenerationFailed(failureClass: "invalid_response"), completionHandler)
                return
            }
            complete(makeGenerationJob(data), completionHandler)
        }
    }

    func getJob(jobId: String, completionHandler: @escaping RepositoryCallback) {
        guard authenticatedUser() != nil else { unauthenticated(completionHandler); return }
        functions.httpsCallable("getItineraryGenerationJob").call(["jobId": jobId]) { result, error in
            if let error { fail(mapError(error), completionHandler); return }
            guard let data = result?.data as? [String: Any] else {
                fail(TripTandemErrorGenerationFailed(failureClass: "invalid_response"), completionHandler)
                return
            }
            complete(makeGenerationJob(data), completionHandler)
        }
    }

    func cancelJob(jobId: String, completionHandler: @escaping RepositoryCallback) {
        guard authenticatedUser() != nil else { unauthenticated(completionHandler); return }
        functions.httpsCallable("cancelItineraryGenerationJob").call(["jobId": jobId]) { result, error in
            if let error { fail(mapError(error), completionHandler); return }
            guard let data = result?.data as? [String: Any] else {
                fail(TripTandemErrorGenerationFailed(failureClass: "invalid_response"), completionHandler)
                return
            }
            complete(makeGenerationJob(data), completionHandler)
        }
    }

    func applyJob(
        jobId: String,
        selectedItemIds: [String],
        idempotencyKey: String,
        editedItems: [GeneratedItineraryItem],
        completionHandler: @escaping RepositoryCallback,
    ) {
        guard authenticatedUser() != nil else { unauthenticated(completionHandler); return }
        var payload: [String: Any] = [
            "jobId": jobId,
            "selectedItemIds": selectedItemIds,
            "idempotencyKey": idempotencyKey,
        ]
        if !editedItems.isEmpty {
            payload["editedItems"] = editedItems.map { item in
                let value: [String: Any] = [
                    "id": item.id,
                    "title": item.title,
                    "startTimeLabel": item.startTimeLabel ?? NSNull(),
                    "flexibleTime": item.flexibleTime,
                    "durationMinutes": item.durationMinutes,
                    "place": item.place ?? NSNull(),
                    "note": item.note ?? NSNull(),
                ]
                return value
            }
        }
        functions.httpsCallable("applyItineraryGenerationJob").call(payload) { result, error in
            if let error { fail(mapError(error), completionHandler); return }
            guard let data = result?.data as? [String: Any] else {
                fail(TripTandemErrorGenerationFailed(failureClass: "invalid_response"), completionHandler)
                return
            }
            let applied = ApplyGenerationResult(
                jobId: string(data, "jobId") ?? jobId,
                appliedItemIds: strings(data, "appliedItemIds"),
                idempotent: bool(data, "idempotent") ?? false,
            )
            complete(applied, completionHandler)
        }
    }
}

private func makeGenerationJob(_ data: [String: Any]) -> ItineraryGenerationJob {
    ItineraryGenerationJob(
        jobId: string(data, "jobId") ?? "",
        state: generationState(data["state"]),
        schemaVersion: string(data, "schemaVersion"),
        preview: (data["preview"] as? [String: Any]).map(makeGenerationPreview),
        generatedItemCount: int(data, "generatedItemCount") ?? 0,
        rejectedItemCount: int(data, "rejectedItemCount") ?? 0,
        failureClass: string(data, "failureClass"),
        errorMessage: string(data, "errorMessage"),
    )
}

private func makeGenerationPreview(_ data: [String: Any]) -> ItineraryGenerationPreview {
    ItineraryGenerationPreview(
        schemaVersion: string(data, "schemaVersion") ?? "",
        assumptions: strings(data, "assumptions"),
        warnings: strings(data, "warnings"),
        items: (data["items"] as? [[String: Any]] ?? []).compactMap(makeGeneratedItem),
        unverifiedInformationNotice: string(data, "unverifiedInformationNotice") ?? "AI suggestions may be outdated. Check details before you go.",
    )
}

private func makeGeneratedItem(_ data: [String: Any]) -> GeneratedItineraryItem? {
    guard let id = string(data, "id"), let title = string(data, "title"),
          let dayDate = string(data, "dayDate") else { return nil }
    return GeneratedItineraryItem(
        id: id,
        type: itineraryType(data["type"]),
        title: title,
        dayDate: dayDate,
        startTimeLabel: string(data, "startTimeLabel"),
        flexibleTime: bool(data, "flexibleTime") ?? true,
        durationMinutes: int(data, "durationMinutes") ?? 60,
        place: string(data, "place"),
        note: string(data, "note"),
        duplicateOfItemId: string(data, "duplicateOfItemId"),
        warning: string(data, "warning"),
    )
}

private func generationState(_ value: Any?) -> GenerationJobState {
    guard let value = value as? String else { return .failed }
    return GenerationJobState.entries.first { $0.wireValue == value } ?? .failed
}

private func firestoreTransaction(
    _ db: Firestore,
    operation: @escaping (Transaction) throws -> Any?,
) async throws {
    try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
        db.runTransaction({ transaction, errorPointer in
            do {
                return try operation(transaction)
            } catch {
                errorPointer?.pointee = error as NSError
                return nil
            }
        }) { _, error in
            if let error {
                continuation.resume(throwing: error)
            } else {
                continuation.resume()
            }
        }
    }
}

private func callTripTandemFunction(
    _ functions: Functions,
    name: String,
    data: [String: Any],
) async throws -> [String: Any] {
    try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<[String: Any], Error>) in
        functions.httpsCallable(name).call(data) { result, error in
            if let error {
                continuation.resume(throwing: error)
                return
            }
            guard let payload = result?.data as? [String: Any] else {
                continuation.resume(throwing: BridgeInvalidResponse())
                return
            }
            continuation.resume(returning: payload)
        }
    }
}

final class FirebaseTripMemberRepositoryBridge: NSObject, TripMemberRepository {
    private let db = Firestore.firestore()
    private let functions = Functions.functions(region: "asia-southeast2")

    private func memberCollection(_ tripId: String) -> CollectionReference {
        db.collection(tripsCollection).document(tripId).collection(membersCollection)
    }

    func listMembers(tripId: String, completionHandler: @escaping RepositoryCallback) {
        guard authenticatedUser() != nil else { unauthenticated(completionHandler); return }
        runList({
            let snapshots = try await self.memberCollection(tripId).getDocuments()
            return snapshots.documents.map { makeMember($0, tripId: tripId) }
        }, completion: completionHandler)
    }

    func getCurrentMember(tripId: String, completionHandler: @escaping RepositoryCallback) {
        guard let user = authenticatedUser() else { unauthenticated(completionHandler); return }
        runOptional({
            let snapshot = try await self.memberCollection(tripId).document(user.uid).getDocument()
            return snapshot.exists ? makeMember(snapshot, tripId: tripId) : (nil as TripMember?)
        }, completion: completionHandler)
    }

    func updateRole(tripId: String, userId: String, role: TripMemberRole, completionHandler: @escaping RepositoryCallback) {
        guard authenticatedUser() != nil else { unauthenticated(completionHandler); return }
        run({
            let reference = self.memberCollection(tripId).document(userId)
            try await reference.updateData(["role": role.wireValue, "updatedAt": FieldValue.serverTimestamp()])
            let snapshot = try await reference.getDocument()
            guard snapshot.exists else { throw BridgeNotFound() }
            return makeMember(snapshot, tripId: tripId)
        }, completion: completionHandler)
    }

    func removeMember(tripId: String, userId: String, completionHandler: @escaping RepositoryCallback) {
        guard authenticatedUser() != nil else { unauthenticated(completionHandler); return }
        runUnit({
            // Pairing an owner-authored parent counter with an arbitrary member
            // path cannot be expressed safely in client-side Rules. The
            // regional callable validates ownership and commits both writes via
            // the Admin SDK transaction.
            _ = try await callTripTandemFunction(
                self.functions,
                name: "removeTripMember",
                data: ["tripId": tripId, "userId": userId],
            )
        }, completion: completionHandler)
    }

    func leaveTrip(tripId: String, completionHandler: @escaping RepositoryCallback) {
        guard let user = authenticatedUser() else { unauthenticated(completionHandler); return }
        runUnit({
            let tripReference = self.db.collection(tripsCollection).document(tripId)
            let memberReference = self.memberCollection(tripId).document(user.uid)
            try await firestoreTransaction(self.db) { transaction in
                let trip = try transaction.getDocument(tripReference)
                let member = try transaction.getDocument(memberReference)
                guard trip.exists, member.exists else { throw BridgeNotFound() }
                let memberData = member.data() ?? [:]
                guard (memberData["status"] as? String) == MembershipStatus.active.wireValue else { throw BridgeNotFound() }
                guard (memberData["role"] as? String) != TripMemberRole.owner.wireValue else { throw BridgeRejected() }
                let tripData = trip.data() ?? [:]
                transaction.updateData([
                    "activeMemberCount": max(1, (int(tripData, "activeMemberCount") ?? 1) - 1),
                    "revision": (int(tripData, "revision") ?? 0) + 1,
                    "updatedAt": FieldValue.serverTimestamp(),
                ], forDocument: tripReference)
                transaction.updateData([
                    "status": MembershipStatus.removed.wireValue,
                    "updatedAt": FieldValue.serverTimestamp(),
                ], forDocument: memberReference)
                return nil
            }
        }, completion: completionHandler)
    }

    func transferOwnership(tripId: String, newOwnerId: String, completionHandler: @escaping RepositoryCallback) {
        guard let user = authenticatedUser() else { unauthenticated(completionHandler); return }
        guard user.uid != newOwnerId else { fail(TripTandemErrorValidation(field: "ownership"), completionHandler); return }
        runUnit({
            let tripReference = self.db.collection(tripsCollection).document(tripId)
            let oldOwnerReference = self.memberCollection(tripId).document(user.uid)
            let newOwnerReference = self.memberCollection(tripId).document(newOwnerId)
            try await firestoreTransaction(self.db) { transaction in
                let trip = try transaction.getDocument(tripReference)
                let oldOwner = try transaction.getDocument(oldOwnerReference)
                let newOwner = try transaction.getDocument(newOwnerReference)
                guard trip.exists, oldOwner.exists, newOwner.exists else { throw BridgeNotFound() }
                let tripData = trip.data() ?? [:]
                let oldData = oldOwner.data() ?? [:]
                let newData = newOwner.data() ?? [:]
                guard (tripData["ownerId"] as? String) == user.uid,
                      (oldData["role"] as? String) == TripMemberRole.owner.wireValue,
                      (newData["status"] as? String) == MembershipStatus.active.wireValue else { throw BridgeRejected() }
                transaction.updateData([
                    "ownerId": newOwnerId,
                    "revision": (int(tripData, "revision") ?? 0) + 1,
                    "updatedAt": FieldValue.serverTimestamp(),
                ], forDocument: tripReference)
                transaction.updateData(["role": TripMemberRole.editor.wireValue, "updatedAt": FieldValue.serverTimestamp()], forDocument: oldOwnerReference)
                transaction.updateData(["role": TripMemberRole.owner.wireValue, "updatedAt": FieldValue.serverTimestamp()], forDocument: newOwnerReference)
                return nil
            }
        }, completion: completionHandler)
    }
}

final class FirebaseInviteRepositoryBridge: NSObject, InviteRepository {
    private let db = Firestore.firestore()

    private func collection(_ tripId: String) -> CollectionReference {
        db.collection(tripsCollection).document(tripId).collection(invitesCollection)
    }

    func createInvite(tripId: String, input: CreateInviteInput, completionHandler: @escaping RepositoryCallback) {
        guard let user = authenticatedUser() else { unauthenticated(completionHandler); return }
        guard input.role != TripMemberRole.owner,
              (1...(24 * 30)).contains(input.expiresInHours),
              (1...20).contains(input.maxUses) else {
            fail(TripTandemErrorValidation(field: "invite"), completionHandler)
            return
        }
        run({
            let token = randomInviteToken()
            let tripSnapshot = try await self.db.collection(tripsCollection).document(tripId).getDocument()
            guard tripSnapshot.exists else { throw BridgeNotFound() }
            let tripData = tripSnapshot.data() ?? [:]
            // The hash is the document id, allowing invite resolution to use
            // a direct read instead of an enumerable collection query.
            let tokenHash = sha256(token)
            let reference = self.collection(tripId).document(tokenHash)
            let expiresAt = Date().addingTimeInterval(TimeInterval(input.expiresInHours * 3600))
            try await reference.setData([
                "tokenHash": tokenHash,
                "role": input.role.wireValue,
                "expiresAt": Timestamp(date: expiresAt),
                "maxUses": input.maxUses,
                "uses": 0,
                "revokedAt": NSNull(),
                "createdBy": user.uid,
                "tripTitle": string(tripData, "title") ?? "Trip",
                "previewDestination": string(tripData, "destination") ?? "",
                "previewStartDate": string(tripData, "startDate") ?? "",
                "previewEndDate": string(tripData, "endDate") ?? "",
                "inviterDisplayName": user.displayName ?? "Trip host",
                "createdAt": FieldValue.serverTimestamp(),
                "updatedAt": FieldValue.serverTimestamp(),
            ])
            return InviteLink(
                id: reference.documentID,
                tripId: tripId,
                role: input.role,
                expiresAtEpochMillis: Int64(expiresAt.timeIntervalSince1970 * 1000),
                maxUses: input.maxUses,
                uses: 0,
                revoked: false,
                shareToken: token,
                createdAtEpochMillis: nil,
                tripTitle: string(tripData, "title"),
                destination: string(tripData, "destination"),
                startDate: string(tripData, "startDate"),
                endDate: string(tripData, "endDate"),
                inviterDisplayName: user.displayName ?? "Trip host",
            )
        }, completion: completionHandler)
    }

    func revokeInvite(tripId: String, inviteId: String, completionHandler: @escaping RepositoryCallback) {
        guard authenticatedUser() != nil else { unauthenticated(completionHandler); return }
        runUnit({
            try await self.collection(tripId).document(inviteId).updateData([
                "revokedAt": FieldValue.serverTimestamp(),
                "updatedAt": FieldValue.serverTimestamp(),
            ])
        }, completion: completionHandler)
    }

    func resolveInvite(tripId: String, shareToken: String, completionHandler: @escaping RepositoryCallback) {
        guard authenticatedUser() != nil else { unauthenticated(completionHandler); return }
        guard !shareToken.isEmpty else { fail(TripTandemErrorValidation(field: "token"), completionHandler); return }
        run({
            let document = try await self.collection(tripId).document(sha256(shareToken)).getDocument()
            guard document.exists else { throw BridgeNotFound() }
            let data = document.data() ?? [:]
            if data["revokedAt"] != nil && !(data["revokedAt"] is NSNull) { throw BridgeInviteRevoked() }
            if (epoch(data["expiresAt"]) ?? 0) <= Int64(Date().timeIntervalSince1970 * 1000) { throw BridgeInviteExpired() }
            if (int(data, "uses") ?? 0) >= (int(data, "maxUses") ?? 1) { throw BridgeInviteCapacity() }
            return makeInvite(document, tripId: tripId, shareToken: shareToken)
        }, completion: completionHandler)
    }

    func acceptInvite(tripId: String, inviteId: String, role: TripMemberRole, completionHandler: @escaping RepositoryCallback) {
        guard let user = authenticatedUser() else { unauthenticated(completionHandler); return }
        guard role != TripMemberRole.owner else { fail(TripTandemErrorValidation(field: "role"), completionHandler); return }
        run({
            let tripReference = self.db.collection(tripsCollection).document(tripId)
            let inviteReference = self.collection(tripId).document(inviteId)
            let memberReference = self.db.collection(tripsCollection).document(tripId).collection(membersCollection).document(user.uid)
            var accepted: TripMember?
            try await firestoreTransaction(self.db) { transaction in
                let trip = try transaction.getDocument(tripReference)
                let invite = try transaction.getDocument(inviteReference)
                let member = try transaction.getDocument(memberReference)
                guard trip.exists, invite.exists else { throw BridgeNotFound() }
                let inviteData = invite.data() ?? [:]
                if inviteData["revokedAt"] != nil && !(inviteData["revokedAt"] is NSNull) { throw BridgeInviteRevoked() }
                if (epoch(inviteData["expiresAt"]) ?? 0) <= Int64(Date().timeIntervalSince1970 * 1000) { throw BridgeInviteExpired() }
                guard (inviteData["role"] as? String) == role.wireValue else { throw BridgeRejected() }
                let uses = int(inviteData, "uses") ?? 0
                let maxUses = int(inviteData, "maxUses") ?? 1
                if uses >= maxUses { throw BridgeInviteCapacity() }
                let existing = member.data() ?? [:]
                if (existing["status"] as? String) == MembershipStatus.active.wireValue {
                    accepted = makeMember(member, tripId: tripId)
                    return nil
                }
                let tripData = trip.data() ?? [:]
                let activeCount = int(tripData, "activeMemberCount") ?? 1
                let capacity = int(tripData, "capacity") ?? 1
                if activeCount >= capacity { throw BridgeInviteCapacity() }
                let now = FieldValue.serverTimestamp()
                let memberData: [String: Any] = [
                    "userId": user.uid,
                    "displayName": user.displayName ?? "Traveler",
                    "role": role.wireValue,
                    "status": MembershipStatus.active.wireValue,
                    "inviteId": inviteId,
                    "joinedAt": existing["joinedAt"] ?? now,
                    "createdAt": existing["createdAt"] ?? now,
                    "updatedAt": now,
                ]
                transaction.setData(memberData, forDocument: memberReference, merge: true)
                transaction.updateData([
                    "activeMemberCount": activeCount + 1,
                    "revision": (int(tripData, "revision") ?? 0) + 1,
                    "updatedAt": now,
                ], forDocument: tripReference)
                transaction.updateData(["uses": uses + 1, "updatedAt": now], forDocument: inviteReference)
                accepted = TripMember(tripId: tripId, userId: user.uid, displayName: user.displayName ?? "Traveler", role: role, status: .active, inviteId: inviteId, joinedAtEpochMillis: nil, updatedAtEpochMillis: nil)
                return nil
            }
            guard let accepted else { throw BridgeNotFound() }
            return accepted
        }, completion: completionHandler)
    }
}

private func randomInviteToken() -> String {
    var bytes = [UInt8](repeating: 0, count: 32)
    _ = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
    return bytes.map { String(format: "%02x", $0) }.joined()
}

private func randomAuthNonce(length: Int = 32) -> String {
    let charset = Array("0123456789ABCDEFGHIJKLMNOPQRSTUVXYZabcdefghijklmnopqrstuvwxyz-._")
    var bytes = [UInt8](repeating: 0, count: length)
    guard SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes) == errSecSuccess else {
        return UUID().uuidString.replacingOccurrences(of: "-", with: "")
    }
    return bytes.map { charset[Int($0) % charset.count] }.map(String.init).joined()
}

private func sha256(_ value: String) -> String {
    SHA256.hash(data: Data(value.utf8)).map { String(format: "%02x", $0) }.joined()
}

func makeFirebaseRepositories(
    presenter: @escaping () -> UIViewController? = { nil },
) -> TripTandemRepositories {
    TripTandemRepositories(
        profile: FirebaseTravelerProfileRepositoryBridge(),
        trips: FirebaseTripRepositoryBridge(),
        itinerary: FirebaseItineraryRepositoryBridge(),
        members: FirebaseTripMemberRepositoryBridge(),
        invites: FirebaseInviteRepositoryBridge(),
        identity: FirebaseIdentityRepositoryBridge(presenter: presenter),
        tripDrafts: IOSTripDraftRepository(),
        generation: FirebaseItineraryGenerationRepositoryBridge(),
        generationDrafts: IOSTripGenerationDraftRepository(),
        community: FirebaseCommunityRepositoryBridge(),
        offlineCache: StandardProtectedTripCacheRepository(storage: InMemorySecurePayloadStorage(), analytics: nil),
    )
}


final class FirebaseCommunityRepositoryBridge: CommunityRepository {
    func execute(operation: String, input: [String: String], completionHandler: @escaping (DataResult?, Error?) -> Void) {
        run({
            var payload = input
            if operation == "preferences" && input["save"] == "true" {
                if input["push"] == "true" { payload["push"] = try await IOSCommunityPush.shared.enable() ? "true" : "false" }
                else { await IOSCommunityPush.shared.unregister() }
            }
            let response = try await Functions.functions(region: "asia-southeast2").httpsCallable("communityAction").call(["operation": operation, "input": payload])
            guard let data = response.data as? [String: Any], let json = data["json"] as? String else {
                throw NSError(domain: "community", code: 1)
            }
            return json as NSString
        }, completion: completionHandler)
    }
}
