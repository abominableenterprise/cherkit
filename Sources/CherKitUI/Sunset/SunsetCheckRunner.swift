#if canImport(SwiftUI)
import CherKitSunset
import Combine
import Foundation
import SwiftUI

/// Runs the checks for ``SwiftUI/View/sunsetPrompt(checker:installedVersion:locale:minimumInterval:memory:strings:appIcon:tint:tintLabel:)``
/// when ``SunsetCheckScheduler`` says so, and holds the latest decision for the prompts.
///
/// The modifier uses one ``shared`` runner for the process, so the launch check runs once per process and the
/// decision (a hard block included) survives the root view being rebuilt.
///
/// A check that finds no usable config (the fetch failed and nothing is cached) is retried once after
/// ``retryDelay``; if that fails too, the scheduler re-checks on the next return from the background.
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
    /// The quick retry waiting for ``retryDelay`` after a failed check, if any.
    private(set) var retryTask: Task<Void, Never>?
    /// How long to wait, in seconds, before the one quick retry after a failed check.
    let retryDelay: TimeInterval
    private let now: () -> Date

    /// The default ``retryDelay``: 3 seconds.
    nonisolated static let defaultRetryDelay: TimeInterval = 3

    init(
        minimumInterval: TimeInterval = SunsetCheckScheduler.defaultMinimumInterval,
        retryDelay: TimeInterval = defaultRetryDelay,
        now: @escaping () -> Date = Date.init
    ) {
        scheduler = SunsetCheckScheduler(minimumInterval: minimumInterval)
        self.retryDelay = retryDelay
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
        retryTask?.cancel()
        retryTask = nil
        task = Task { [weak self] in
            let result = await request.checker.checkReportingFailure(
                installedVersion: request.installedVersion,
                osVersion: SunsetChecker.osVersion(),
                locale: request.locale
            )
            guard let self else { return }
            self.decision = result.decision
            let retry = self.scheduler.checkFinished(failed: result.failed)
            self.task = nil
            if retry { self.scheduleRetry(request) }
        }
    }

    /// The one quick retry after a failed check, unless another check started meanwhile.
    private func scheduleRetry(_ request: Request) {
        let nanoseconds = UInt64(max(retryDelay, 0) * 1_000_000_000)
        retryTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: nanoseconds)
            guard let self, !Task.isCancelled else { return }
            self.retryTask = nil
            if self.scheduler.shouldRetry(now: self.now()) { self.run(request) }
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
