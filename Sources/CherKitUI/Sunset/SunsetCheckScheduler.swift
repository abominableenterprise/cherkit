#if canImport(SwiftUI)
import Foundation
import SwiftUI

/// Decides when ``SwiftUI/View/sunsetPrompt(checker:installedVersion:locale:minimumInterval:memory:strings:appIcon:tint:tintLabel:)``
/// runs a check. Pure (the caller passes the time), so its rules are unit-tested on their own:
///
/// - Launch: the first ``shouldCheckAtLaunch(now:)`` checks; later calls never do.
/// - Return from background: `.active` checks only after a `.background` since the last `.active`. The initial
///   `.active` and `.inactive` → `.active` (Control Center, a system alert, the app switcher) do not.
///   `.background` → `.inactive` → `.active`, the real order on iOS, does.
/// - Never two at once: nothing starts while a check is in flight (until ``checkFinished(failed:)``).
/// - Re-checks wait at least ``minimumInterval`` since the last successful check started. The launch check always
///   runs.
/// - A failed check (no usable config: fetch failed and nothing cached) does not count toward ``minimumInterval``.
///   The first failure in a row asks for one quick retry (``shouldRetry(now:)``); while the last check failed, the
///   next return from the background re-checks whatever the interval.
struct SunsetCheckScheduler: Equatable, Sendable {
    /// The default ``minimumInterval``: one hour.
    static let defaultMinimumInterval: TimeInterval = 60 * 60

    /// The least time between the start of one check and a re-check. `0` re-checks on every return from background.
    var minimumInterval: TimeInterval
    /// Whether the launch check has started.
    private(set) var hasLaunched = false
    /// Whether a check is running.
    private(set) var isChecking = false
    /// When the last check that did not fail started (the running one counts until it fails).
    private(set) var lastCheckStarted: Date?
    /// Whether the scene went to the background since it was last active.
    private(set) var wasInBackground = false
    /// How many checks in a row have failed. Above `0`, a return from the background ignores ``minimumInterval``.
    private(set) var failuresInARow = 0
    /// Whether the quick retry after a first failure is due (``shouldRetry(now:)``).
    private(set) var isRetryPending = false
    /// ``lastCheckStarted`` before the running check started, restored if it fails.
    private var startBeforeRunningCheck: Date?

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
    /// in flight and ``minimumInterval`` passed (or the last check failed).
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
            if failuresInARow == 0, let lastCheckStarted, now.timeIntervalSince(lastCheckStarted) < minimumInterval {
                return false
            }
            return start(now)
        default:
            // `.inactive` sits between `.background` and `.active` on the way back, so it keeps `wasInBackground`.
            return false
        }
    }

    /// Call when the check started by a `true` answer has finished; `failed` when it found no usable config.
    ///
    /// - Returns: `true` when the caller should schedule the one quick retry, then call ``shouldRetry(now:)``.
    @discardableResult
    mutating func checkFinished(failed: Bool = false) -> Bool {
        isChecking = false
        guard failed else {
            failuresInARow = 0
            return false
        }
        lastCheckStarted = startBeforeRunningCheck
        failuresInARow += 1
        isRetryPending = failuresInARow == 1
        return isRetryPending
    }

    /// Call when the retry delay asked for by ``checkFinished(failed:)`` has passed. `true` (start the retry) only
    /// if no other check has started since.
    mutating func shouldRetry(now: Date) -> Bool {
        guard isRetryPending else { return false }
        return start(now)
    }

    private mutating func start(_ now: Date) -> Bool {
        guard !isChecking else { return false }
        isChecking = true
        isRetryPending = false
        startBeforeRunningCheck = lastCheckStarted
        lastCheckStarted = now
        return true
    }
}
#endif
