#if canImport(SwiftUI)
import CherKitSunset
import SwiftUI

/// Presents the Sunset prompts for a decision. Use it through ``SwiftUI/View/sunsetPrompt(decision:latestVersion:memory:strings:appIcon:tint:tintLabel:onVisibilityChange:onPromptChange:)``
/// or ``SwiftUI/View/sunsetPrompt(checker:installedVersion:locale:minimumInterval:memory:strings:appIcon:tint:tintLabel:onVisibilityChange:onPromptChange:onFirstCheckComplete:)``.
@available(iOS 16, macOS 13, *)
struct SunsetPromptModifier: ViewModifier {
    @Binding var decision: SunsetDecision?
    /// The version passed to `.sunsetPrompt(latestVersion:)`, or `nil` to use the prompt's.
    let latestVersion: String?
    /// The installed app version: the last fallback for the "Not now" version.
    let installedVersion: String
    let memory: SunsetSoftPromptMemory
    let strings: SunsetStrings
    let appIcon: Image?
    /// The hard block's tint; the soft alert keeps the app's.
    var tint: Color?
    /// The hard block's button label color on `tint`; `nil` picks black or white by contrast.
    var tintLabel: Color?
    /// Hears `true` / `false` when the soft alert appears / goes away.
    var onVisibilityChange: ((Bool) -> Void)? = nil
    /// Hears which prompt is showing whenever that changes.
    var onPromptChange: ((SunsetPromptKind?) -> Void)? = nil
    /// Whether the `checker:` form's launch check has resolved; always `false` for the `decision:` form.
    var firstCheckComplete = false
    /// Called once `firstCheckComplete` turns `true`, right after the prompt callbacks heard that decision. The
    /// `checker:` form guards it to once per process.
    var onFirstCheckComplete: (() -> Void)? = nil

    @Environment(\.openURL) private var openURL

    /// The version "Not now" is remembered for when `prompt` is answered: the `latestVersion` parameter, else the
    /// prompt's ``SunsetPrompt/latestVersion``, else the installed version.
    func version(for prompt: SunsetPrompt?) -> String {
        latestVersion ?? prompt?.latestVersion?.description ?? installedVersion
    }

    /// What "Not now" does for `prompt`.
    func rememberNotNow(for prompt: SunsetPrompt) {
        memory.rememberNotNow(for: version(for: prompt))
    }

    /// Whether the soft alert is up for the current decision.
    var showsSoftPrompt: Bool {
        memory.shouldShowSoftPrompt(for: decision, version: version(for: softPrompt))
    }

    /// The prompt showing for the current decision, as the callbacks report it.
    var promptKind: SunsetPromptKind? {
        SunsetPromptKind.showing(for: decision, showsSoftPrompt: showsSoftPrompt)
    }

    /// Passes a change of ``promptKind`` to the callbacks: `onPromptChange` always, `onVisibilityChange` only when
    /// the soft alert appears or goes away.
    func report(from old: SunsetPromptKind?, to new: SunsetPromptKind?) {
        onPromptChange?(new)
        if (old == .soft) != (new == .soft) { onVisibilityChange?(new == .soft) }
    }

    /// What the callbacks report for the current decision.
    var promptState: SunsetPromptState {
        SunsetPromptState(kind: promptKind, settled: firstCheckComplete)
    }

    /// Passes a change of ``promptState`` to the callbacks: a kind change first (``report(from:to:)``), then
    /// `onFirstCheckComplete` when the launch check has just resolved, so it never runs before that decision's kind.
    func report(from old: SunsetPromptState, to new: SunsetPromptState) {
        if old.kind != new.kind { report(from: old.kind, to: new.kind) }
        if new.settled, !old.settled { onFirstCheckComplete?() }
    }

    func body(content: Content) -> some View {
        content
            .alert(strings.softTitle, isPresented: softPresented, presenting: softPrompt) { prompt in
                Button(strings.updateButton) { openURL(prompt.storeURL) }
                    .accessibilityHint(strings.updateHint)
                Button(strings.notNowButton, role: .cancel) { rememberNotNow(for: prompt) }
            } message: { prompt in
                Text(strings.softText(for: prompt))
            }
            .modifier(SunsetHardBlockPresenter(
                prompt: hardPrompt, strings: strings, appIcon: appIcon, tint: tint, tintLabel: tintLabel
            ))
            .modifier(VisibilityChangeReporter(
                value: promptState, hidden: SunsetPromptState(), report: report(from:to:)
            ))
    }

    private var softPrompt: SunsetPrompt? {
        guard case .softUpdate(let prompt) = decision else { return nil }
        return prompt
    }

    private var hardPrompt: SunsetPrompt? {
        guard case .hardUpdate(let prompt) = decision else { return nil }
        return prompt
    }

    /// Shown while the rule says so; answering the alert clears the decision so it is shown once per check.
    private var softPresented: Binding<Bool> {
        Binding(
            get: { showsSoftPrompt },
            set: { presented in
                if !presented, case .softUpdate = decision { decision = nil }
            }
        )
    }
}

/// Covers the content with ``SunsetUpdateView`` while `prompt` is set, with no way to dismiss it.
@available(iOS 16, macOS 13, *)
struct SunsetHardBlockPresenter: ViewModifier {
    let prompt: SunsetPrompt?
    let strings: SunsetStrings
    let appIcon: Image?
    let tint: Color?
    var tintLabel: Color? = nil

    func body(content: Content) -> some View {
        #if canImport(UIKit)
        // iOS: its own window above the scene's windows rather than a `fullScreenCover`, which cannot appear while
        // the app already presents a sheet or cover (onboarding, an editor), leaving the old version usable.
        content.background(SunsetHardBlockHost(
            prompt: prompt, strings: strings, appIcon: appIcon, tint: tint, tintLabel: tintLabel
        ))
        #else
        // macOS: an overlay instead of a sheet, so neither Escape nor a window command can dismiss it. The content
        // below is disabled and hidden from VoiceOver so it cannot be reached from the keyboard either.
        let blocked = prompt != nil
        content
            .disabled(blocked)
            .accessibilityHidden(blocked)
            .overlay {
                if let prompt {
                    SunsetUpdateView(prompt: prompt, strings: strings, appIcon: appIcon, tint: tint, tintLabel: tintLabel)
                }
            }
        #endif
    }
}

@available(iOS 16, macOS 13, *)
extension View {
    /// Shows the Sunset prompt for `decision`:
    ///
    /// - ``SunsetDecision/softUpdate(_:)``: a dismissible alert with "Update" and "Not now". "Not now" is remembered
    ///   for the config's latest version, so the alert does not come back for that version (see
    ///   ``SunsetSoftPromptRule``).
    ///   Either answer sets `decision` to `nil`.
    /// - ``SunsetDecision/hardUpdate(_:)``: ``SunsetUpdateView`` over everything, with no way to dismiss it
    ///   (on iOS a window above the app's windows, so it also covers sheets the app is presenting; on macOS an
    ///   overlay).
    /// - ``SunsetDecision/upToDate`` or `nil`: nothing.
    ///
    /// This modifier only displays a decision; it never fetches. Get one from the Sunset checker (or
    /// ``SunsetDecider``) and assign it to `decision`, or let
    /// ``sunsetPrompt(checker:installedVersion:locale:minimumInterval:memory:strings:appIcon:tint:tintLabel:onVisibilityChange:onPromptChange:onFirstCheckComplete:)`` run the checks.
    ///
    /// - Parameters:
    ///   - decision: The decision to show, `nil` while unknown.
    ///   - latestVersion: Overrides the version "Not now" is remembered for. Leave it `nil` (the default) to use the
    ///     prompt's ``SunsetPrompt/latestVersion`` (the config's `latestVersion`), so a newer one shows the prompt
    ///     again. When both are `nil`, the installed app version is used, so "Not now" lasts until the app is
    ///     updated.
    ///   - memory: Where "Not now" is stored. Defaults to `UserDefaults.standard` under
    ///     ``SunsetSoftPromptMemory/defaultKey``.
    ///   - strings: The text; English defaults.
    ///   - appIcon: Optional icon for the hard block.
    ///   - tint: The hard block's button and accent color, e.g. your accent color. On iOS the block runs in its own
    ///     window, which does not inherit your app's tint, so pass it to match. `nil` (the default) keeps the system
    ///     tint. The soft alert always uses the app's tint.
    ///   - tintLabel: The hard block's button label color on the `tint` fill. `nil` (the default) picks black or
    ///     white, whichever contrasts more with `tint` in the current light or dark mode. Ignored without a `tint`.
    ///   - onVisibilityChange: Called with `true` when the soft "Update available" alert appears and `false` when it
    ///     goes away (answered, cleared, or this view goes away). Not called for the hard block; use
    ///     `onPromptChange` for both.
    ///   - onPromptChange: Called with the prompt showing (`.soft`, `.hard` or `nil`) whenever that changes, never for
    ///     the initial `nil`, and with `nil` when this view goes away while one was showing. Report both to a review
    ///     coordinator with `{ review.setSunsetPromptKind($0) }`.
    public func sunsetPrompt(
        decision: Binding<SunsetDecision?>,
        latestVersion: String? = nil,
        memory: SunsetSoftPromptMemory = SunsetSoftPromptMemory(),
        strings: SunsetStrings = SunsetStrings(),
        appIcon: Image? = nil,
        tint: Color? = nil,
        tintLabel: Color? = nil,
        onVisibilityChange: ((Bool) -> Void)? = nil,
        onPromptChange: ((SunsetPromptKind?) -> Void)? = nil
    ) -> some View {
        modifier(SunsetPromptModifier(
            decision: decision,
            latestVersion: latestVersion,
            installedVersion: installedAppVersion(),
            memory: memory,
            strings: strings,
            appIcon: appIcon,
            tint: tint,
            tintLabel: tintLabel,
            onVisibilityChange: onVisibilityChange,
            onPromptChange: onPromptChange
        ))
    }
}

/// The installed app's `CFBundleShortVersionString`, or `""` when it has none.
func installedAppVersion() -> String {
    Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? ""
}
#endif
