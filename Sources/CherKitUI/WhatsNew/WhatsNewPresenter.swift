#if canImport(SwiftUI)
import CherKitWhatsNew
import Combine

/// The state behind the `.whatsNew` modifiers, kept free of view code so its rules are unit-testable:
/// present only non-empty entries, and run the dismiss action once, only after the sheet was dismissed.
/// For a checker, ``launchCheck(_:)`` runs `onLaunch` at most once, and `markSeen` is the dismiss action.
@MainActor
final class WhatsNewPresenter: ObservableObject {
    /// Drives `.sheet(isPresented:)`; SwiftUI sets it back to `false` when the sheet goes away.
    @Published var isPresented = false
    /// The releases the sheet shows.
    @Published private(set) var entries: [WhatsNewEntry] = []

    private var onDismiss: (() -> Void)?
    private var hasRunLaunchCheck = false
    private var lastOffered: [WhatsNewEntry]?

    /// Shows `entries` and remembers `onDismiss` for ``sheetDismissed()``. Empty entries show nothing. While the sheet
    /// is up, new non-empty entries replace the shown ones and the first `onDismiss` stays.
    func present(_ entries: [WhatsNewEntry], onDismiss: @escaping () -> Void) {
        guard !entries.isEmpty else { return }
        self.entries = entries
        if self.onDismiss == nil { self.onDismiss = onDismiss }
        isPresented = true
    }

    /// ``present(_:onDismiss:)`` for app-supplied entries, skipping a list equal to the last one offered, so a view
    /// re-appearing with the same entries does not show them twice.
    func offer(_ entries: [WhatsNewEntry], onDismiss: @escaping () -> Void) {
        guard entries != lastOffered else { return }
        lastOffered = entries
        present(entries, onDismiss: onDismiss)
    }

    /// Closes the sheet; ``sheetDismissed()`` still runs when SwiftUI reports the dismissal.
    func close() {
        isPresented = false
    }

    /// Called from the sheet's `onDismiss`: runs the pending dismiss action once.
    func sheetDismissed() {
        isPresented = false
        let action = onDismiss
        onDismiss = nil
        action?()
    }

    /// The first call asks `checker` for the notes (`onLaunch`) and presents them with `markSeen` as the dismiss
    /// action; later calls do nothing, so re-appearing never asks again.
    func launchCheck(_ checker: WhatsNewChecker, installedVersion: String, locale: [String]) {
        guard !hasRunLaunchCheck else { return }
        hasRunLaunchCheck = true
        let entries = checker.onLaunch(installedVersion: installedVersion, locale: locale)
        present(entries, onDismiss: { checker.markSeen() })
    }
}
#endif
