#if canImport(SwiftUI)
import CherKitWhatsNew
import SwiftUI

@available(iOS 16, macOS 13, *)
extension View {
    /// Shows the "What's new" sheet after an update. On this view's first appear it calls
    /// `checker.onLaunch(installedVersion:locale:)` once and, if that returns notes, presents a ``WhatsNewSheet``.
    /// `checker.markSeen()` runs when the sheet is dismissed ("Continue" or swipe), never before, so notes the user
    /// never saw come back next launch. Attach it to your root view.
    ///
    /// ```swift
    /// ContentView().whatsNew(checker: whatsNew)   // let whatsNew = WhatsNewChecker(), kept for the app's lifetime
    /// ```
    ///
    /// - Parameters:
    ///   - suppressed: While `true` the sheet waits (or is taken away again, without counting as a dismissal), e.g.
    ///     `suppressed: sunsetKind != nil` so Sunset's prompt goes first. It shows once this turns `false`.
    ///   - onVisibilityChange: Called with `true` when the sheet is presented and `false` when it goes away, e.g.
    ///     `{ review.setInterruptionVisible($0, source: ReviewInterruption.whatsNew) }`.
    public func whatsNew(
        checker: WhatsNewChecker,
        installedVersion: String = WhatsNewChecker.installedVersion(),
        locale: [String] = Locale.preferredLanguages,
        strings: WhatsNewStrings = .english,
        suppressed: Bool = false,
        onVisibilityChange: ((Bool) -> Void)? = nil
    ) -> some View {
        modifier(WhatsNewCheckerModifier(
            checker: checker, installedVersion: installedVersion, locale: locale, strings: strings,
            suppressed: suppressed, onVisibilityChange: onVisibilityChange))
    }

    /// Shows a ``WhatsNewSheet`` for `entries` you got yourself (e.g. from `onLaunch` on your own schedule).
    /// It presents whenever `entries` becomes a new non-empty list, and calls `onDismiss` once when the sheet is
    /// dismissed ("Continue" or swipe). Empty entries show nothing. Call `markSeen()` from `onDismiss` if you
    /// use a ``WhatsNewChecker``. `suppressed` and `onVisibilityChange` work as in
    /// ``whatsNew(checker:installedVersion:locale:strings:suppressed:onVisibilityChange:)``.
    public func whatsNew(
        entries: [WhatsNewEntry],
        strings: WhatsNewStrings = .english,
        suppressed: Bool = false,
        onVisibilityChange: ((Bool) -> Void)? = nil,
        onDismiss: @escaping () -> Void
    ) -> some View {
        modifier(WhatsNewEntriesModifier(
            entries: entries, strings: strings, suppressed: suppressed, onVisibilityChange: onVisibilityChange,
            onDismiss: onDismiss))
    }
}

/// Backs ``SwiftUI/View/whatsNew(checker:installedVersion:locale:strings:suppressed:onVisibilityChange:)``.
@available(iOS 16, macOS 13, *)
private struct WhatsNewCheckerModifier: ViewModifier {
    let checker: WhatsNewChecker
    let installedVersion: String
    let locale: [String]
    let strings: WhatsNewStrings
    let suppressed: Bool
    let onVisibilityChange: ((Bool) -> Void)?
    @StateObject private var presenter = WhatsNewPresenter()

    func body(content: Content) -> some View {
        content
            .onAppear {
                presenter.setSuppressed(suppressed)
                presenter.launchCheck(checker, installedVersion: installedVersion, locale: locale)
            }
            .whatsNewSheet(presenter, strings: strings, suppressed: suppressed, onVisibilityChange: onVisibilityChange)
    }
}

/// Backs ``SwiftUI/View/whatsNew(entries:strings:suppressed:onVisibilityChange:onDismiss:)``.
@available(iOS 16, macOS 13, *)
private struct WhatsNewEntriesModifier: ViewModifier {
    let entries: [WhatsNewEntry]
    let strings: WhatsNewStrings
    let suppressed: Bool
    let onVisibilityChange: ((Bool) -> Void)?
    let onDismiss: () -> Void
    @StateObject private var presenter = WhatsNewPresenter()

    func body(content: Content) -> some View {
        content
            // Runs on appear and whenever `entries` changes; the presenter ignores entries it already offered.
            .task(id: entries) {
                presenter.setSuppressed(suppressed)
                presenter.offer(entries, onDismiss: onDismiss)
            }
            .whatsNewSheet(presenter, strings: strings, suppressed: suppressed, onVisibilityChange: onVisibilityChange)
    }
}

@available(iOS 16, macOS 13, *)
extension View {
    /// The sheet both modifiers share: "Continue" closes it, every dismissal reaches the presenter, `suppressed`
    /// follows the parameter and `onVisibilityChange` hears the presented state.
    fileprivate func whatsNewSheet(
        _ presenter: WhatsNewPresenter,
        strings: WhatsNewStrings,
        suppressed: Bool,
        onVisibilityChange: ((Bool) -> Void)?
    ) -> some View {
        sheet(isPresented: Binding(get: { presenter.isPresented }, set: { if !$0 { presenter.close() } }),
              onDismiss: presenter.sheetDismissed) {
            WhatsNewSheet(entries: presenter.entries, strings: strings, onContinue: presenter.close)
                .onAppear(perform: presenter.sheetAppeared)
        }
        .onChange(of: suppressed) { presenter.setSuppressed($0) }
        .modifier(VisibilityChangeReporter(value: presenter.isPresented, hidden: false) { _, visible in
            onVisibilityChange?(visible)
        })
    }
}
#endif
