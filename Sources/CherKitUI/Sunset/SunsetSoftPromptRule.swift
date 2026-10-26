#if canImport(SwiftUI)
import CherKitSunset

/// Whether the soft "update available" prompt should appear. Pure, so it is unit-tested on its own.
public enum SunsetSoftPromptRule {
    /// `true` only for ``SunsetDecision/softUpdate(_:)`` when the user has not tapped "Not now" for `version`.
    ///
    /// - Parameters:
    ///   - decision: The decision for this launch, or `nil` while it is not known yet.
    ///   - version: The version the prompt is about (see ``SunsetSoftPromptMemory``), e.g. the config's
    ///     `latestVersion`.
    ///   - dismissedVersion: The version the user last tapped "Not now" for, or `nil` when never.
    /// - Returns: `false` for `nil`, ``SunsetDecision/upToDate`` and ``SunsetDecision/hardUpdate(_:)``: the hard block
    ///   ignores "Not now" and has its own presentation.
    public static func shouldShow(_ decision: SunsetDecision?, version: String, dismissedVersion: String?) -> Bool {
        guard case .softUpdate = decision else { return false }
        return dismissedVersion != version
    }
}
#endif
