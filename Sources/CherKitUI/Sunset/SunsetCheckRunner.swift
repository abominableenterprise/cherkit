#if canImport(SwiftUI)
import CherKitSunset
import Combine
import Foundation
import SwiftUI

/// Runs the checks for ``SwiftUI/View/sunsetPrompt(checker:installedVersion:locale:minimumInterval:memory:strings:appIcon:tint:)``
/// when ``SunsetCheckScheduler`` says so, and holds the latest decision for the prompts.
///
/// The modifier uses one ``shared`` runner for the process, so the launch check runs once per process and the
/// decision (a hard block included) survives the root view being rebuilt.
@MainActor
final class SunsetCheckRunner: ObservableObject {
    /// The runner every `.sunsetPrompt(checker:)` uses.
    static let shared = SunsetCheckRunner()

    /// The latest decision, `nil` before the first check finishes. The soft alert sets it back to `nil` when answered.
    @Published var decision: SunsetDecision?
    /// When to check.
    private(set) var scheduler: SunsetCheckScheduler
    /// The running check, if any.
    private(set) var task: Task<Void, Never>?
    private let now: () -> Date

    init(minimumInterval: TimeInterval = SunsetCheckScheduler.defaultMinimumInterval, now: @escaping () -> Date = Date.init) {
        scheduler = SunsetCheckScheduler(minimumInterval: minimumInterval)
        self.now = now
    }

    /// Sets the least time between re-checks (the modifier's `minimumInterval:`).
    func setMinimumInterval(_ interval: TimeInterval) {
        scheduler.minimumInterval = interval
    }

    /// The first appear: checks once per runner.
    func launched(_ request: Request) {
        if scheduler.shouldCheckAtLaunch(now: now()) { run(request) }
    }

    /// A scene phase change: checks on a return from the background.
    func scenePhaseChanged(to phase: ScenePhase, _ request: Request) {
        if scheduler.shouldCheck(onScenePhase: phase, now: now()) { run(request) }
    }

    private func run(_ request: Request) {
        task = Task { [weak self] in
            let decision = await request.checker.check(
                installedVersion: request.installedVersion,
                osVersion: SunsetChecker.osVersion(),
                locale: request.locale
            )
            guard let self else { return }
            self.decision = decision
            self.scheduler.checkFinished()
            self.task = nil
        }
    }

    /// What to check with.
    struct Request: Sendable {
        let checker: SunsetChecker
        let installedVersion: String
        let locale: [String]
    }
}
#endif
