/// The typed analytics facade apps call instead of a provider SDK.
///
/// An event is sent only when the gate is open: `buildEnabled && consentGranted && sessionActive`, and its name is
/// valid (see `AnalyticsEventName`). The backend is started lazily when the gate opens, stopped whenever it
/// closes, and started again if it re-opens. Analytics is best-effort: it never throws into app code.
///
/// `@MainActor` keeps the state and the backend calls on one thread without locks; call it from UI code or
/// hop with `Task { @MainActor in ... }`.
@MainActor
public final class Analytics<Event: AnalyticsEvent> {
    /// Fixed at init. Apps pass `false` for debug builds, tests (see `AnalyticsTestHost`) and emulators.
    public let buildEnabled: Bool

    /// The user's opt-in. Starts `false`; the app sets it from its persisted consent.
    public var consentGranted = false {
        didSet { syncBackend() }
    }

    /// Whether analytics may run for the current session, e.g. a signed-in adult. Never `true` on a kid's device.
    public var sessionActive = false {
        didSet { syncBackend() }
    }

    public var isGateOpen: Bool { buildEnabled && consentGranted && sessionActive }

    private let backend: AnalyticsBackend
    private var backendStarted = false

    public init(backend: AnalyticsBackend, buildEnabled: Bool) {
        self.backend = backend
        self.buildEnabled = buildEnabled
    }

    /// Sends `event` if the gate is open and its name is valid. Returns whether it was handed to the backend.
    @discardableResult
    public func track(_ event: Event) -> Bool {
        guard isGateOpen, AnalyticsEventName.isValid(event.name) else { return false }
        if !backendStarted { startBackend() }
        guard backendStarted else { return false }
        do {
            try backend.capture(event.name)
            return true
        } catch {
            return false
        }
    }

    private func syncBackend() {
        if isGateOpen, !backendStarted {
            startBackend()
        } else if !isGateOpen, backendStarted {
            backendStarted = false
            try? backend.stop()
        }
    }

    private func startBackend() {
        do {
            try backend.start()
            backendStarted = true
        } catch {
            // Roll back a partial start: the SDK may have opted in before throwing.
            backendStarted = false
            try? backend.stop()
        }
    }
}
