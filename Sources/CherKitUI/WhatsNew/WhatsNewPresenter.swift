#if canImport(SwiftUI)
import CherKitWhatsNew
import Combine

/// The state behind the `.whatsNew` modifiers, kept free of view code so its rules are unit-testable:
/// present only non-empty entries, and run the dismiss action once, only after the sheet was dismissed.
/// For a checker, ``launchCheck(_:)`` runs `onLaunch` at most once, and `markSeen` is the dismiss action.
/// While ``isSuppressed`` (e.g. a Sunset prompt is up) the sheet waits, or is taken away without counting as a
/// dismissal, and comes back once it is no longer suppressed.
@MainActor
final class WhatsNewPresenter: ObservableObject {
    /// Drives `.sheet(isPresented:)`; SwiftUI sets it back to `false` when the sheet goes away.
    @Published var isPresented = false
    /// The releases the sheet shows.
    @Published private(set) var entries: [WhatsNewEntry] = []

    /// Whether the sheet is held back; see ``setSuppressed(_:)``.
    private(set) var isSuppressed = false

    private var onDismiss: (() -> Void)?
    /// Entries were presented and the user has not dismissed them yet.
    private var isPending = false
    /// The sheet is on screen: it appeared (``sheetAppeared()``) and has not been dismissed since.
    private var isSheetShown = false
    /// The sheet was taken away for ``isSuppressed`` and SwiftUI has not reported that dismissal yet.
    private var isHidingForSuppression = false
    private var hasRunLaunchCheck = false
    private var lastOffered: [WhatsNewEntry]?

    /// Shows `entries` and remembers `onDismiss` for ``sheetDismissed()``. Empty entries show nothing. While the sheet
    /// is up, new non-empty entries replace the shown ones and the first `onDismiss` stays.
    func present(_ entries: [WhatsNewEntry], onDismiss: @escaping () -> Void) {
        guard !entries.isEmpty else { return }
        self.entries = entries
        if self.onDismiss == nil { self.onDismiss = onDismiss }
        isPending = true
        if !isSuppressed && !isHidingForSuppression { isPresented = true }
    }

    /// Holds the sheet back while `suppressed`: it is not presented, or is taken away if up (the dismiss action does
    /// not run). Pending entries are presented again once this turns `false`.
    func setSuppressed(_ suppressed: Bool) {
        guard suppressed != isSuppressed else { return }
        isSuppressed = suppressed
        if suppressed {
            guard isPresented else { return }
            // Only a sheet that is on screen reports its dismissal; one not shown yet just isn't presented.
            isHidingForSuppression = isSheetShown
            isPresented = false
        } else if isPending && !isHidingForSuppression {
            isPresented = true
        }
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

    /// Called when the sheet's content appears.
    func sheetAppeared() {
        isSheetShown = true
    }

    /// Called from the sheet's `onDismiss`: runs the pending dismiss action once.
    func sheetDismissed() {
        isPresented = false
        isSheetShown = false
        if isHidingForSuppression || isSuppressed {
            // Taken away by `setSuppressed(true)`, not by the user: keep the entries and the action.
            isHidingForSuppression = false
            if !isSuppressed && isPending { isPresented = true }
            return
        }
        isPending = false
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
