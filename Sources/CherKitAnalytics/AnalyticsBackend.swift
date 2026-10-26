/// What a provider adapter (e.g. the PostHog adapter) implements. `Analytics` decides when each call happens;
/// the backend only forwards to the provider SDK.
///
/// - All calls arrive on the main actor and must return quickly: enqueue work, never block on the network.
/// - Throwing is allowed; `Analytics` catches every error and treats it as a failed, best-effort call.
public protocol AnalyticsBackend: AnyObject {
    /// Set up (or resume) the provider SDK and opt in. Called lazily, each time the gate opens.
    /// If it throws, `Analytics` calls `stop()` right away to roll back any partial opt-in.
    func start() throws

    /// Send one event by name. Only called between a successful `start()` and the next `stop()`.
    func capture(_ name: String) throws

    /// Opt out and close the provider SDK. Called whenever the gate closes. Must not send anything
    /// queued after the gate closed, and must leave the SDK able to `start()` again.
    func stop() throws
}
