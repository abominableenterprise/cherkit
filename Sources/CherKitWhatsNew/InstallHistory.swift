import CherKitCore

/// The versions installed on this device, oldest first, for the full history view ("you are here" is the last one).
/// Contract: `fixtures/whatsnew/install-history.json`.
public enum InstallHistory {
    /// Default number of entries kept.
    public static let defaultCap = 100

    /// `history` after launching `current`: the normalized version (e.g. `1.4.0`) is appended when it differs from
    /// the last entry (a downgrade is a change too), then the oldest entries are dropped to keep at most `cap`
    /// (at least 1). An unreadable `current` fails open: nothing is appended. Stored entries are never rewritten.
    public static func updated(_ history: [String], current: String, cap: Int = defaultCap) -> [String] {
        var result = history
        if let version = AppVersion(current), history.last.flatMap(AppVersion.init) != version {
            result.append(version.description)
        }
        return Array(result.suffix(max(cap, 1)))
    }
}
