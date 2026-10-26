#if canImport(SwiftUI)
import CherKitSunset
import SwiftUI

/// Presents the Sunset prompts for a decision. Use it through ``SwiftUI/View/sunsetPrompt(decision:latestVersion:memory:strings:appIcon:)``.
@available(iOS 16, macOS 13, *)
struct SunsetPromptModifier: ViewModifier {
    @Binding var decision: SunsetDecision?
    let version: String
    let memory: SunsetSoftPromptMemory
    let strings: SunsetStrings
    let appIcon: Image?

    @Environment(\.openURL) private var openURL

    func body(content: Content) -> some View {
        content
            .alert(strings.softTitle, isPresented: softPresented, presenting: softPrompt) { prompt in
                Button(strings.updateButton) { openURL(prompt.storeURL) }
                    .accessibilityHint(strings.updateHint)
                Button(strings.notNowButton, role: .cancel) { memory.rememberNotNow(for: version) }
            } message: { prompt in
                Text(strings.softText(for: prompt))
            }
            .modifier(SunsetHardBlockPresenter(prompt: hardPrompt, strings: strings, appIcon: appIcon))
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
            get: { memory.shouldShowSoftPrompt(for: decision, version: version) },
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

    func body(content: Content) -> some View {
        #if canImport(UIKit)
        // iOS: its own window above the scene's windows rather than a `fullScreenCover`, which cannot appear while
        // the app already presents a sheet or cover (onboarding, an editor), leaving the old version usable.
        content.background(SunsetHardBlockHost(prompt: prompt, strings: strings, appIcon: appIcon))
        #else
        // macOS: an overlay instead of a sheet, so neither Escape nor a window command can dismiss it. The content
        // below is disabled and hidden from VoiceOver so it cannot be reached from the keyboard either.
        let blocked = prompt != nil
        content
            .disabled(blocked)
            .accessibilityHidden(blocked)
            .overlay {
                if let prompt {
                    SunsetUpdateView(prompt: prompt, strings: strings, appIcon: appIcon)
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
    ///   for `latestVersion`, so the alert does not come back for that version (see ``SunsetSoftPromptRule``).
    ///   Either answer sets `decision` to `nil`.
    /// - ``SunsetDecision/hardUpdate(_:)``: ``SunsetUpdateView`` over everything, with no way to dismiss it
    ///   (on iOS a window above the app's windows, so it also covers sheets the app is presenting; on macOS an
    ///   overlay).
    /// - ``SunsetDecision/upToDate`` or `nil`: nothing.
    ///
    /// The modifier only displays a decision; it never fetches. Get one from the Sunset checker (or
    /// ``SunsetDecider``) and assign it to `decision`.
    ///
    /// - Parameters:
    ///   - decision: The decision to show, `nil` while unknown.
    ///   - latestVersion: The version the soft prompt is about, e.g. the config's `latestVersion`. A newer one shows
    ///     the prompt again after "Not now". When `nil`, the installed app version is used, so "Not now" lasts until
    ///     the app is updated.
    ///   - memory: Where "Not now" is stored. Defaults to `UserDefaults.standard` under
    ///     ``SunsetSoftPromptMemory/defaultKey``.
    ///   - strings: The text; English defaults.
    ///   - appIcon: Optional icon for the hard block.
    public func sunsetPrompt(
        decision: Binding<SunsetDecision?>,
        latestVersion: String? = nil,
        memory: SunsetSoftPromptMemory = SunsetSoftPromptMemory(),
        strings: SunsetStrings = SunsetStrings(),
        appIcon: Image? = nil
    ) -> some View {
        modifier(SunsetPromptModifier(
            decision: decision,
            version: latestVersion ?? installedAppVersion(),
            memory: memory,
            strings: strings,
            appIcon: appIcon
        ))
    }
}

/// The installed app's `CFBundleShortVersionString`, or `""` when it has none.
private func installedAppVersion() -> String {
    Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? ""
}
#endif
