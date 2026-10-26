package com.abominableenterprise.cherkit.sunset.compose

import com.abominableenterprise.cherkit.sunset.SunsetDecision
import com.abominableenterprise.cherkit.sunset.SunsetPrompt

/**
 * Whether the soft "update available" prompt should appear. Pure, so it is unit-tested on its own; the same rule as
 * iOS's `SunsetSoftPromptRule` in CherKitUI. The shared cases are `fixtures/sunset/soft-prompt.json`.
 */
public object SunsetSoftPromptRule {
    /**
     * `true` only for [SunsetDecision.SoftUpdate] when the user has not tapped "Not now" for [version].
     *
     * @param decision The decision for this launch, or `null` while it is not known yet.
     * @param version The version the prompt is about: [version] of the prompt, usually the config's `latestVersion`.
     * @param dismissedVersion The version the user last tapped "Not now" for, or `null` when never.
     * @return `false` for `null`, [SunsetDecision.UpToDate] and [SunsetDecision.HardUpdate]: the hard block ignores
     *   "Not now" and has its own presentation.
     */
    public fun shouldShow(decision: SunsetDecision?, version: String, dismissedVersion: String?): Boolean {
        if (decision !is SunsetDecision.SoftUpdate) return false
        return dismissedVersion != version
    }

    /**
     * The version "Not now" is remembered for: the prompt's [SunsetPrompt.latestVersion] (the config's
     * `latestVersion`, normalized like `1.4.0`), else [installedVersion], so "Not now" then lasts until the app is
     * updated.
     */
    public fun version(prompt: SunsetPrompt?, installedVersion: String): String =
        prompt?.latestVersion?.toString() ?: installedVersion
}
