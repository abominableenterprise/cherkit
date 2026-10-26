import CherKitCore
import Foundation

/// One platform's block of the hosted config.
public struct SunsetPlatformConfig: Equatable, Sendable {
    /// Builds below this get a hard (blocking) update prompt. `nil`: never hard.
    public let minimumVersion: AppVersion?
    /// Builds below this get a soft (dismissible) update prompt. `nil`: never soft.
    public let latestVersion: AppVersion?
    /// Lowest OS version (iOS version, or Android API level) that can install ``latestVersion``. Devices below it are
    /// never prompted, not even for a hard update, because they cannot install the update. `nil`: OS is not checked.
    public let latestMinimumOS: AppVersion?
    /// Where the update prompt sends the user. Always `https` in parsed configs.
    public let storeURL: URL
    /// Custom prompt text by language tag (`"en"`, `"de"`, `"pt-BR"`); resolve it with ``SunsetMessages/resolve(_:languages:)``.
    public let messages: [String: String]

    /// Creates a block. ``SunsetConfig/parse(_:)`` validates hosted values; this initializer does not.
    public init(
        minimumVersion: AppVersion? = nil,
        latestVersion: AppVersion? = nil,
        latestMinimumOS: AppVersion? = nil,
        storeURL: URL,
        messages: [String: String] = [:]
    ) {
        self.minimumVersion = minimumVersion
        self.latestVersion = latestVersion
        self.latestMinimumOS = latestMinimumOS
        self.storeURL = storeURL
        self.messages = messages
    }
}
