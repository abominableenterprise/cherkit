/// What the user did with a soft prompt.
public enum SunsetPromptAction: String, CaseIterable, Sendable {
    /// Tapped "Not now".
    case notNow
    /// Tapped "Update" (opened the store or TestFlight).
    case update
}

/// When the ``SunsetUpdateKind/testBuild`` prompt may appear: at most once per offered build.
///
/// Both "Not now" and "Update" remember the build, so a build that TestFlight does not list yet costs at most one
/// no-op tap, never a re-prompt loop; a different `latestBuild` prompts again. Keep the remembered build under its own
/// key (``defaultMemoryKey``), separate from the release prompt's "Not now" version. Pure; the contract is
/// `fixtures/sunset/test-build-prompt.json`, shared with Kotlin's `SunsetTestBuildPromptRule`.
public enum SunsetTestBuildPromptRule {
    /// The suggested storage key for the remembered build.
    public static let defaultMemoryKey = "cherkit.sunset.testBuildRememberedBuild"

    /// `true` only for a soft ``SunsetUpdateKind/testBuild`` prompt whose `latestBuild` differs from
    /// `rememberedBuild` (compared as ``SunsetBuildNumber``; `nil` or unparsable means none remembered).
    public static func shouldShow(_ decision: SunsetDecision?, rememberedBuild: String?) -> Bool {
        guard case .softUpdate(let prompt)? = decision, prompt.kind == .testBuild, let build = prompt.latestBuild
        else { return false }
        return rememberedBuild.flatMap(SunsetBuildNumber.init) != build
    }

    /// The build to remember after `action` on `prompt`: its `latestBuild` for a ``SunsetUpdateKind/testBuild`` prompt
    /// (for "Not now" and "Update" alike), `nil` for other prompts.
    public static func buildToRemember(after action: SunsetPromptAction, on prompt: SunsetPrompt) -> String? {
        guard prompt.kind == .testBuild else { return nil }
        return prompt.latestBuild?.description
    }
}
