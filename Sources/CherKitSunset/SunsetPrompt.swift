import CherKitCore
import Foundation

/// What an update prompt shows and where it links.
public struct SunsetPrompt: Equatable, Sendable {
    /// The page to open: the config's `storeURL` (else the App Store lookup's `trackViewUrl`), or for a
    /// ``SunsetUpdateKind/testBuild`` prompt the `testFlight.url` (else ``SunsetTestFlightConfig/defaultURL``).
    public let storeURL: URL
    /// Custom text in the user's language, or `nil` when the config has none that fits: the UI uses its default text.
    public let message: String?
    /// The newest release version offered (the higher of the config's `latestVersion` and the App Store lookup's), or
    /// `nil` when there is none (e.g. a hard block from `minimumVersion` alone, or a test-build prompt). The UI
    /// remembers "Not now" for this version.
    public let latestVersion: AppVersion?
    /// What the prompt offers. ``SunsetUpdateKind/update`` unless it is a TestFlight test build.
    public let kind: SunsetUpdateKind
    /// For a ``SunsetUpdateKind/testBuild`` prompt, the config's `testFlight.latestBuild`; else `nil`.
    public let latestBuild: SunsetBuildNumber?

    /// Creates a prompt.
    public init(
        storeURL: URL,
        message: String?,
        latestVersion: AppVersion? = nil,
        kind: SunsetUpdateKind = .update,
        latestBuild: SunsetBuildNumber? = nil
    ) {
        self.storeURL = storeURL
        self.message = message
        self.latestVersion = latestVersion
        self.kind = kind
        self.latestBuild = latestBuild
    }
}
