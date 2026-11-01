/// The review prompt's persisted, per-device state. ``ReviewCoordinator`` reads it once at init and writes it through
/// its ``ReviewStore`` on every change.
public struct ReviewState: Equatable, Sendable {
    /// Successes recorded since the last prompt (all versions). Capped at `Int32.max`; a negative value counts as 0.
    public var successCount: Int
    /// A trigger fired and no prompt has been shown for it yet.
    public var isDue: Bool
    /// The app version that last showed the prompt, or `nil` before the first one.
    public var lastPromptedVersion: String?

    public init(successCount: Int = 0, isDue: Bool = false, lastPromptedVersion: String? = nil) {
        self.successCount = successCount
        self.isDue = isDue
        self.lastPromptedVersion = lastPromptedVersion
    }
}
