#if canImport(SwiftUI)
import CherKitSunset
import SwiftUI

/// Backs ``SwiftUI/View/sunsetPrompt(checker:installedVersion:locale:minimumInterval:memory:strings:appIcon:tint:tintLabel:onVisibilityChange:onPromptChange:onFirstCheckComplete:)``:
/// runs the checks through a ``SunsetCheckRunner`` and shows its decision with ``SunsetPromptModifier``.
@available(iOS 16, macOS 13, *)
struct SunsetCheckerPromptModifier: ViewModifier {
    let request: SunsetCheckRunner.Request
    let minimumInterval: TimeInterval
    let memory: SunsetSoftPromptMemory
    let strings: SunsetStrings
    let appIcon: Image?
    let tint: Color?
    let tintLabel: Color?
    var onVisibilityChange: ((Bool) -> Void)? = nil
    var onPromptChange: ((SunsetPromptKind?) -> Void)? = nil
    var onFirstCheckComplete: (() -> Void)? = nil
    @ObservedObject var runner: SunsetCheckRunner

    @Environment(\.scenePhase) private var scenePhase

    func body(content: Content) -> some View {
        content
            .modifier(promptModifier)
            .onAppear {
                runner.setMinimumInterval(minimumInterval)
                runner.launched(request)
            }
            .onChange(of: scenePhase) { phase in
                runner.scenePhaseChanged(to: phase, request)
            }
    }

    /// Shows the runner's decision and reports it, with the launch check's completion, to the callbacks.
    var promptModifier: SunsetPromptModifier {
        SunsetPromptModifier(
            decision: $runner.decision,
            latestVersion: nil,
            installedVersion: request.installedVersion,
            memory: memory,
            strings: strings,
            appIcon: appIcon,
            tint: tint,
            tintLabel: tintLabel,
            onVisibilityChange: onVisibilityChange,
            onPromptChange: onPromptChange,
            firstCheckComplete: runner.firstCheckComplete,
            onFirstCheckComplete: firstCheckCompleteOnce
        )
    }

    /// `onFirstCheckComplete`, guarded by the runner so it runs once per process even if the view is rebuilt.
    private var firstCheckCompleteOnce: (() -> Void)? {
        guard let onFirstCheckComplete else { return nil }
        let runner = runner
        return { runner.reportFirstCheckComplete(onFirstCheckComplete) }
    }
}

@available(iOS 16, macOS 13, *)
extension View {
    /// Checks for an update and shows the Sunset prompt (see ``sunsetPrompt(decision:latestVersion:memory:strings:appIcon:tint:tintLabel:onVisibilityChange:onPromptChange:)``
    /// for what each decision shows). Attach it once, to your root view:
    ///
    /// ```swift
    /// ContentView().sunsetPrompt(checker: isUITest ? .disabled : SunsetChecker(url: url), tint: .accentColor)
    /// ```
    ///
    /// When it checks:
    ///
    /// - Once at launch, on the first appear. Only once per process, even if the view appears again or is rebuilt.
    /// - Again when the app comes back from the background (the scene phase goes `.background`, then `.active`), at
    ///   most once per `minimumInterval`. The initial `.active` and `.inactive` → `.active` (Control Center, a system
    ///   alert) do not check, so the launch fetch never runs twice.
    /// - Never while a check is still running.
    /// - After a failed check (the fetch failed or did not parse, and no usable config is cached), once more a few
    ///   seconds later. A failed check does not count toward `minimumInterval`: while the last one failed, the next
    ///   return from the background re-checks whatever the interval.
    ///
    /// A re-check replaces the decision: a hard block goes away once the config no longer requires the update, and
    /// a soft prompt answered with "Not now" stays hidden for that `latestVersion`. ``SunsetChecker/disabled`` never
    /// shows anything.
    ///
    /// - Parameters:
    ///   - checker: The checker, e.g. `SunsetChecker(url:)`, or ``SunsetChecker/disabled`` in UI tests.
    ///   - installedVersion: The installed version; `nil` (the default) uses `CFBundleShortVersionString`.
    ///   - locale: The preferred languages for the config's messages.
    ///   - minimumInterval: The least time, in seconds, between the start of one check and a re-check on return from
    ///     the background. One hour by default; `0` re-checks on every return.
    ///   - memory: Where "Not now" is stored.
    ///   - strings: The text; English defaults.
    ///   - appIcon: Optional icon for the hard block.
    ///   - tint: The hard block's button and accent color; on iOS it runs in its own window that does not inherit the
    ///     app's tint. `nil` keeps the system tint. The soft alert always uses the app's tint.
    ///   - tintLabel: The hard block's button label color on the `tint` fill. `nil` (the default) picks black or
    ///     white, whichever contrasts more with `tint` in the current light or dark mode. Ignored without a `tint`.
    ///   - onVisibilityChange: Called with `true` when the soft "Update available" alert appears and `false` when it
    ///     goes away. Not called for the hard block, so checks can keep running behind it; use `onPromptChange`.
    ///   - onPromptChange: Called with the prompt showing (`.soft`, `.hard` or `nil`) whenever that changes, e.g.
    ///     `{ review.setSunsetPromptKind($0) }` so a review never shows over either.
    ///   - onFirstCheckComplete: Called once per process when the launch check's first attempt has resolved, with
    ///     any outcome: up to date, a soft prompt, a hard block, or a failed fetch (which fails open after the
    ///     checker's timeout). `onPromptChange` and `onVisibilityChange` hear that first decision before it runs, so
    ///     a prompt kind is never stale when it does. ``SunsetChecker/disabled`` completes on the first appear.
    ///     Re-checks and the quick retry after a failure never call it again. Use it to hold What's New until Sunset
    ///     has decided: `.whatsNew(checker:suppressed: !sunsetSettled || sunsetKind != nil)`.
    public func sunsetPrompt(
        checker: SunsetChecker,
        installedVersion: String? = nil,
        locale: [String] = Locale.preferredLanguages,
        minimumInterval: TimeInterval = 60 * 60,
        memory: SunsetSoftPromptMemory = SunsetSoftPromptMemory(),
        strings: SunsetStrings = SunsetStrings(),
        appIcon: Image? = nil,
        tint: Color? = nil,
        tintLabel: Color? = nil,
        onVisibilityChange: ((Bool) -> Void)? = nil,
        onPromptChange: ((SunsetPromptKind?) -> Void)? = nil,
        onFirstCheckComplete: (() -> Void)? = nil
    ) -> some View {
        modifier(SunsetCheckerPromptModifier(
            request: SunsetCheckRunner.Request(
                checker: checker,
                installedVersion: installedVersion ?? installedAppVersion(),
                locale: locale
            ),
            minimumInterval: minimumInterval,
            memory: memory,
            strings: strings,
            appIcon: appIcon,
            tint: tint,
            tintLabel: tintLabel,
            onVisibilityChange: onVisibilityChange,
            onPromptChange: onPromptChange,
            onFirstCheckComplete: onFirstCheckComplete,
            runner: .shared
        ))
    }
}
#endif
