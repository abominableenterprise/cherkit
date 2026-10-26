/// What the app should show for the installed version.
public enum SunsetDecision: Equatable, Sendable {
    /// No prompt. Also the fail-open answer for a missing, malformed or unusable config.
    case upToDate
    /// A newer version exists: show a dismissible "update available" prompt.
    case softUpdate(SunsetPrompt)
    /// The installed version is no longer supported: show a blocking "update required" screen.
    case hardUpdate(SunsetPrompt)

    /// The prompt to show, or `nil` for ``upToDate``.
    public var prompt: SunsetPrompt? {
        switch self {
        case .upToDate: return nil
        case .softUpdate(let prompt), .hardUpdate(let prompt): return prompt
        }
    }
}
