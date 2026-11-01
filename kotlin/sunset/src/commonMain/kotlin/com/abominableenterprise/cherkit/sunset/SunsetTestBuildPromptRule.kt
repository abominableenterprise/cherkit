package com.abominableenterprise.cherkit.sunset

/**
 * When the [SunsetUpdateKind.TEST_BUILD] prompt may appear: at most once per offered build.
 *
 * Both "Not now" and "Update" remember the build, so a build that TestFlight does not list yet costs at most one no-op
 * tap, never a re-prompt loop; a different `latestBuild` prompts again. Keep the remembered build under its own key
 * ([DEFAULT_MEMORY_KEY]), separate from the release prompt's "Not now" version. Pure; the contract is
 * `fixtures/sunset/test-build-prompt.json`, shared with Swift's `SunsetTestBuildPromptRule`.
 */
public object SunsetTestBuildPromptRule {
    /** The suggested storage key for the remembered build. */
    public const val DEFAULT_MEMORY_KEY: String = "cherkit.sunset.testBuildRememberedBuild"

    /**
     * `true` only for a soft [SunsetUpdateKind.TEST_BUILD] prompt whose `latestBuild` differs from [rememberedBuild]
     * (compared as [SunsetBuildNumber]; `null` or unparsable means none remembered).
     */
    public fun shouldShow(decision: SunsetDecision?, rememberedBuild: String?): Boolean {
        if (decision !is SunsetDecision.SoftUpdate || decision.prompt.kind != SunsetUpdateKind.TEST_BUILD) return false
        val build = decision.prompt.latestBuild ?: return false
        return rememberedBuild?.let(SunsetBuildNumber::parse) != build
    }

    /**
     * The build to remember after [action] on [prompt]: its `latestBuild` for a [SunsetUpdateKind.TEST_BUILD] prompt
     * (for "Not now" and "Update" alike), `null` for other prompts.
     */
    @Suppress("UNUSED_PARAMETER")
    public fun buildToRemember(action: SunsetPromptAction, prompt: SunsetPrompt): String? =
        if (prompt.kind == SunsetUpdateKind.TEST_BUILD) prompt.latestBuild?.toString() else null
}
