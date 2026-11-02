import CherKitCore
import Foundation

/// The store-review ("rate this app") rules. Pure logic: it never shows anything; a platform adapter asks
/// ``shouldPrompt(isIdle:)`` and calls ``markPrompted()`` right before it hands off to the system review API.
///
/// - **Due** after the ``successThreshold``th ``recordSuccess()`` (counted across launches and versions until the
///   next prompt) or an ``arm()``. The due flag persists until a prompt is shown.
/// - **At most once per app version**: after a prompt the count restarts at 0, and triggers in that same version are
///   ignored. A later version prompts again once re-armed (by ``arm()`` or N fresh successes).
/// - **Eligibility**: while ``isEligible`` is false nothing counts or arms and nothing prompts; a due prompt is held.
/// - **Interruptions**: ``noteInterruption()`` or a source hidden through ``setInterruptionVisible(_:source:)`` holds
///   the prompt for ``cooldown`` (it stays due); while any source is visible it never prompts.
@MainActor
public final class ReviewCoordinator {
    /// Successes that make the prompt due by default.
    public nonisolated static let defaultSuccessThreshold = 5
    /// How long an interruption holds the prompt by default: 2 minutes.
    public nonisolated static let defaultCooldown: TimeInterval = 120

    /// `true` for ``disabled`` and for `enabled: false`: nothing counts, arms or prompts and the store is never read
    /// or written.
    public let isDisabled: Bool
    /// The version a prompt is recorded for, by default `CFBundleShortVersionString`.
    public let appVersion: String
    /// Successes that make the prompt due (at least 1).
    public let successThreshold: Int
    /// How long an interruption holds the prompt, in seconds (at least 0).
    public let cooldown: TimeInterval
    /// Whether the current session may count and prompt, e.g. `{ !session.isKidDevice }`. Read on every call.
    public var isEligible: () -> Bool

    private let telemetrySink: (any TelemetrySink)?
    private let store: ReviewStore
    private let now: () -> Date
    private var state: ReviewState
    private var lastInterruption: Date?
    /// The interruption sources currently visible.
    public private(set) var visibleInterruptions: Set<String> = []

    /// - Parameters:
    ///   - store: Where the state persists; `UserDefaults.standard` by default.
    ///   - appVersion: The installed version; one prompt per distinct string.
    ///   - enabled: Pass `false` for debug builds and test hosts; then the coordinator is ``isDisabled``.
    ///   - successThreshold: The ``recordSuccess()`` call that makes the prompt due; values below 1 count as 1.
    ///   - cooldown: Seconds an interruption holds the prompt; negative values count as 0.
    ///   - isEligible: Whether the current session may count and prompt.
    ///   - now: The clock.
    public init(
        store: ReviewStore = UserDefaultsReviewStore(),
        appVersion: String = ReviewCoordinator.installedVersion(),
        enabled: Bool = true,
        successThreshold: Int = defaultSuccessThreshold,
        cooldown: TimeInterval = defaultCooldown,
        isEligible: @escaping () -> Bool = { true },
        now: @escaping () -> Date = Date.init,
        telemetrySink: (any TelemetrySink)? = nil
    ) {
        self.telemetrySink = telemetrySink
        self.store = store
        self.appVersion = appVersion
        self.isDisabled = !enabled
        self.successThreshold = max(1, successThreshold)
        self.cooldown = max(0, cooldown)
        self.isEligible = isEligible
        self.now = now
        self.state = enabled ? store.load() : ReviewState()
    }

    /// `CFBundleShortVersionString` of the main bundle, or `""` when absent.
    public nonisolated static func installedVersion(bundle: Bundle = .main) -> String {
        bundle.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? ""
    }

    /// Something the app counts as a success happened (e.g. an entry was saved). The Nth makes the prompt due.
    public func recordSuccess() {
        guard acceptsTriggers else { return }
        state.successCount = Int(min(Int64(max(0, state.successCount)) + 1, Int64(Int32.max)))
        if state.successCount >= successThreshold { state.isDue = true }
        store.save(state)
    }

    /// Makes the prompt due now, e.g. for a milestone.
    public func arm() {
        guard acceptsTriggers else { return }
        state.isDue = true
        store.save(state)
    }

    /// A paywall, error or failed write was shown: hold the prompt for ``cooldown`` from now. It stays due.
    public func noteInterruption() {
        guard !isDisabled else { return }
        lastInterruption = now()
    }

    /// Reports one of the app's own modals. While any `source` is visible the prompt never shows; hiding a visible
    /// source starts the ``cooldown``, so the prompt waits for the next settle after it instead of firing at once.
    public func setInterruptionVisible(_ visible: Bool, source: String) {
        guard !isDisabled else { return }
        if visible {
            visibleInterruptions.insert(source)
        } else if visibleInterruptions.remove(source) != nil {
            lastInterruption = now()
        }
    }

    /// A trigger fired and this version has not prompted yet. Ignores eligibility and interruptions.
    public var isDue: Bool {
        !isDisabled && state.isDue && !promptedThisVersion
    }

    /// How long the last interruption still holds the prompt; 0 when it no longer does.
    public var cooldownRemaining: TimeInterval {
        guard let lastInterruption else { return 0 }
        return min(cooldown, max(0, lastInterruption.addingTimeInterval(cooldown).timeIntervalSince(now())))
    }

    /// Whether to ask now: due, eligible, `isIdle` (the adapter's settled screen), no interruption visible and the
    /// cooldown over.
    public func shouldPrompt(isIdle: Bool = true) -> Bool {
        isIdle && isDue && isEligible() && visibleInterruptions.isEmpty && cooldownRemaining == 0
    }

    /// Records the prompt for ``appVersion``, right before the adapter calls the system review API. Rechecks
    /// ``shouldPrompt(isIdle:)`` and returns `false` (changing nothing) when it no longer holds; then don't ask.
    @discardableResult
    public func markPrompted() -> Bool {
        guard shouldPrompt() else { return false }
        state = ReviewState(successCount: 0, isDue: false, lastPromptedVersion: appVersion)
        store.save(state)
        telemetrySink?.breadcrumb(name: "cherkit.review.prompted", attributes: [:])
        return true
    }

    private var promptedThisVersion: Bool { state.lastPromptedVersion == appVersion }

    private var acceptsTriggers: Bool { !isDisabled && isEligible() && !promptedThisVersion }
}
