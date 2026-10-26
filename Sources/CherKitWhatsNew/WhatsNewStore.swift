/// Where WhatsNew keeps its per-device state. UserDefaults and SharedPreferences adapters come in a later PR;
/// tests and apps can use any implementation. Calls happen on the caller's thread and must be fast (local storage).
public protocol WhatsNewStore: Sendable {
    /// The version whose notes the user last saw, or `nil` on first launch.
    func lastSeenVersion() -> String?
    /// Stores the version from ``LaunchPlan/lastSeen``.
    func setLastSeenVersion(_ version: String)
    /// The stored install history, oldest first (see ``InstallHistory``).
    func installHistory() -> [String]
    /// Replaces the stored install history.
    func setInstallHistory(_ history: [String])
}
