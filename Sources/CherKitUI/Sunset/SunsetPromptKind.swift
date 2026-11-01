#if canImport(SwiftUI)
import CherKitSunset

/// Which Sunset prompt is showing, reported by `.sunsetPrompt(onPromptChange:)`; `nil` means none. The same cases as
/// Android's `SunsetPromptKind`.
public enum SunsetPromptKind: Equatable, Sendable {
    /// The dismissible "Update available" alert.
    case soft
    /// The "Update required" block that cannot be dismissed.
    case hard

    /// The prompt showing for `decision`: `.soft` while the soft alert is up (`showsSoftPrompt`), `.hard` for a hard
    /// update, else `nil`.
    static func showing(for decision: SunsetDecision?, showsSoftPrompt: Bool) -> SunsetPromptKind? {
        if case .hardUpdate? = decision { return .hard }
        return showsSoftPrompt ? .soft : nil
    }
}
#endif
