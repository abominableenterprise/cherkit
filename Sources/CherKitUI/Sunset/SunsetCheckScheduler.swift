#if canImport(SwiftUI)
import Foundation
import SwiftUI

/// Decides when ``SwiftUI/View/sunsetPrompt(checker:installedVersion:locale:minimumInterval:strings:appIcon:tint:)``
/// runs a check. Pure (the caller passes the time), so its rules are unit-tested on their own:
///
/// - Launch: the first ``shouldCheckAtLaunch(now:)`` checks; later calls never do.
/// - Return from background: `.active` checks only after a `.background` since the last `.active`. The initial
///   `.active` and `.inactive` → `.active` (Control Center, a system alert, the app switcher) do not.
///   `.background` → `.inactive` → `.active`, the real order on iOS, does.
/// - Never two at once: nothing starts while a check is in flight (until ``checkFinished()``).
/// - Re-checks wait at least ``minimumInterval`` since the last check started. The launch check always runs.
struct SunsetCheckScheduler: Equatable, Sendable {
    /// The default ``minimumInterval``: one hour.
    static let defaultMinimumInterval: TimeInterval = 60 * 60

    /// The least time between the start of one check and a re-check. `0` re-checks on every return from background.
    var minimumInterval: TimeInterval
    /// Whether the launch check has started.
    private(set) var hasLaunched = false
    /// Whether a check is running.
    private(set) var isChecking = false
    /// When the last check started.
    private(set) var lastCheckStarted: Date?
    /// Whether the scene went to the background since it was last active.
    private(set) var wasInBackground = false

    init(minimumInterval: TimeInterval = defaultMinimumInterval) {
        self.minimumInterval = minimumInterval
    }

    /// Call on the first appear. `true` (start a check) only the first time.
    mutating func shouldCheckAtLaunch(now: Date) -> Bool {
        guard !hasLaunched else { return false }
        hasLaunched = true
        return start(now)
    }

    /// Call on every scene phase change. `true` (start a check) only for a return from the background, with no check
    /// in flight and ``minimumInterval`` passed.
    mutating func shouldCheck(onScenePhase phase: ScenePhase, now: Date) -> Bool {
        switch phase {
        case .background:
            wasInBackground = true
            return false
        case .active:
            guard wasInBackground else { return false }
            wasInBackground = false
            // Before the launch check (an app launched in the background), the first appear does the check.
            guard hasLaunched else { return false }
            if let lastCheckStarted, now.timeIntervalSince(lastCheckStarted) < minimumInterval { return false }
            return start(now)
        default:
            // `.inactive` sits between `.background` and `.active` on the way back, so it keeps `wasInBackground`.
            return false
        }
    }

    /// Call when the check started by a `true` answer has finished.
    mutating func checkFinished() {
        isChecking = false
    }

    private mutating func start(_ now: Date) -> Bool {
        guard !isChecking else { return false }
        isChecking = true
        lastCheckStarted = now
        return true
    }
}
#endif
