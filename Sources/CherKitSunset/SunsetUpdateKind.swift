/// What a ``SunsetPrompt`` offers, so the UI can pick its copy and its "Not now" memory.
public enum SunsetUpdateKind: String, CaseIterable, Sendable {
    /// A newer release in the store (config `latestVersion`, the App Store lookup, or a hard block).
    case update
    /// A newer TestFlight build (config `ios.testFlight.latestBuild`). Always soft. "Not now" and "Update" are remembered
    /// per build with ``SunsetTestBuildPromptRule``, separately from the release prompt.
    case testBuild
}
