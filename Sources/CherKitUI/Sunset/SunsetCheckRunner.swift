#if canImport(SwiftUI)
import CherKitSunset
import Combine
import Foundation
import SwiftUI

/// Runs the checks for ``SwiftUI/View/sunsetPrompt(checker:installedVersion:locale:channel:minimumInterval:memory:strings:appIcon:tint:tintLabel:onVisibilityChange:onPromptChange:onFirstCheckComplete:)``
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
    /// Whether the launch check's first attempt has resolved, with any outcome (a failed fetch fails open). Set in the
    /// same main-actor turn as that attempt's ``decision``, after it, so the prompt callbacks hear the decision first.
    /// Re-checks and the quick retry never change it back.
    @Published private(set) var firstCheckComplete = false
    /// Whether an `onFirstCheckComplete` callback has run (``reportFirstCheckComplete(_:)``).
    private(set) var hasReportedFirstCheckComplete = false
    /// Whether the user has answered a soft prompt in this process (``softPromptAnswered()``). From then on, later
    /// checks show no soft prompt until the next launch: at most one soft prompt per session. Hard blocks still show.
    private(set) var hasAnsweredSoftPrompt = false
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

    /// Runs `callback` (the modifier's `onFirstCheckComplete`) unless one already ran: at most once per runner, so
    /// once per process for ``shared``, even when the root view is rebuilt.
    func reportFirstCheckComplete(_ callback: (() -> Void)?) {
        guard let callback, !hasReportedFirstCheckComplete else { return }
        hasReportedFirstCheckComplete = true
        callback()
    }

    /// The user answered the soft prompt ("Update" or "Not now"): no other soft prompt in this process.
    func softPromptAnswered() {
        hasAnsweredSoftPrompt = true
    }

    private func run(_ request: Request) {
        retryTask?.cancel()
        retryTask = nil
        if request.checker.isDisabled {
            // Answers at once, so the first check completes on the first appear rather than a turn later.
            finish(decision: .upToDate)
            scheduler.checkFinished(failed: false)
            return
        }
        task = Task { [weak self] in
            let result = await request.checker.checkReportingFailure(
                installedVersion: request.installedVersion,
                osVersion: SunsetChecker.osVersion(),
                locale: request.locale,
                channel: request.channel,
                installedBuild: request.installedBuild
            )
            guard let self else { return }
            self.finish(decision: result.decision)
            let retry = self.scheduler.checkFinished(failed: result.failed)
            self.task = nil
            if retry { self.scheduleRetry(request) }
        }
    }

    /// Shows `decision` (a soft one only while none was answered this session), then marks the first check complete
    /// if this was it.
    private func finish(decision: SunsetDecision) {
        if case .softUpdate = decision, hasAnsweredSoftPrompt {
            self.decision = .upToDate
        } else {
            self.decision = decision
        }
        if !firstCheckComplete { firstCheckComplete = true }
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
        /// How the app was installed; ``SunsetChannel/store`` unless the app passes one.
        var channel: SunsetChannel = .store
        /// The installed build number (`CFBundleVersion`), for TestFlight test-build prompts.
        var installedBuild: String? = nil
    }
}
#endif
