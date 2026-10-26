import CherKitCore

/// What to do with "What's new" on app launch. Build it with ``LaunchPlan/make(notes:installed:lastSeen:)``.
public struct LaunchPlan: Hashable, Sendable {
    /// Notes to show now, newest first; empty means show nothing.
    public let show: [ReleaseNotes]
    /// The last-seen version to store after showing ``show`` (normalized, e.g. `1.4.0`), or `nil` to store nothing.
    public let lastSeen: String?

    /// Creates a plan.
    public init(show: [ReleaseNotes], lastSeen: String?) {
        self.show = show
        self.lastSeen = lastSeen
    }

    /// The plan for `installed` (the running app's version string) given every bundled `notes` and the stored
    /// `lastSeen` version (`nil` on first launch). Contract: `fixtures/whatsnew/launch.json`.
    ///
    /// - First launch (or an unreadable `lastSeen`): show nothing, store `installed`.
    /// - Update: show the `show == true` notes with `lastSeen < version <= installed`, newest first; store `installed`.
    /// - Same version or downgrade: show nothing, keep `lastSeen`, so re-upgrading does not repeat notes.
    /// - Unreadable `installed`: fail open, show nothing and keep `lastSeen`.
    ///
    /// When two notes have the same version (`1.3`, `1.3.0`) the first one wins.
    public static func make(notes: [ReleaseNotes], installed: String, lastSeen: String?) -> LaunchPlan {
        guard let current = AppVersion(installed) else { return LaunchPlan(show: [], lastSeen: lastSeen) }
        guard let seen = lastSeen.flatMap(AppVersion.init) else {
            return LaunchPlan(show: [], lastSeen: current.description)
        }
        guard seen < current else { return LaunchPlan(show: [], lastSeen: lastSeen) }
        var versions = Set<AppVersion>()
        let shown = notes
            .filter { versions.insert($0.version).inserted }
            .filter { $0.show && seen < $0.version && $0.version <= current }
            .sorted { $1.version < $0.version }
        return LaunchPlan(show: shown, lastSeen: current.description)
    }
}
