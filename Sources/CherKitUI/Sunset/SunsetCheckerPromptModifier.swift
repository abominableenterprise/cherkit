#if canImport(SwiftUI)
import CherKitSunset
import SwiftUI

/// Backs ``SwiftUI/View/sunsetPrompt(checker:installedVersion:locale:minimumInterval:memory:strings:appIcon:tint:tintLabel:)``:
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
    @ObservedObject var runner: SunsetCheckRunner

    @Environment(\.scenePhase) private var scenePhase

    func body(content: Content) -> some View {
        content
            .modifier(SunsetPromptModifier(
                decision: $runner.decision,
                latestVersion: nil,
                installedVersion: request.installedVersion,
                memory: memory,
                strings: strings,
                appIcon: appIcon,
                tint: tint,
                tintLabel: tintLabel
            ))
            .onAppear {
                runner.setMinimumInterval(minimumInterval)
                runner.launched(request)
            }
            .onChange(of: scenePhase) { phase in
                runner.scenePhaseChanged(to: phase, request)
            }
    }
}

@available(iOS 16, macOS 13, *)
extension View {
    /// Checks for an update and shows the Sunset prompt (see ``sunsetPrompt(decision:latestVersion:memory:strings:appIcon:tint:tintLabel:)``
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
    public func sunsetPrompt(
        checker: SunsetChecker,
        installedVersion: String? = nil,
        locale: [String] = Locale.preferredLanguages,
        minimumInterval: TimeInterval = 60 * 60,
        memory: SunsetSoftPromptMemory = SunsetSoftPromptMemory(),
        strings: SunsetStrings = SunsetStrings(),
        appIcon: Image? = nil,
        tint: Color? = nil,
        tintLabel: Color? = nil
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
            runner: .shared
        ))
    }
}
#endif
