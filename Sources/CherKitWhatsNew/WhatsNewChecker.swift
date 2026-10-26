import CherKitCore
import Foundation

/// The one call apps make at launch: which "What's new" notes to show, plus the full history list.
/// Contract: `fixtures/whatsnew/checker.json`. Never throws; anything unreadable fails open (shows nothing).
///
/// ```swift
/// let whatsNew = WhatsNewChecker()
/// let entries = whatsNew.onLaunch()
/// if !entries.isEmpty { present(entries, onDismiss: whatsNew.markSeen) }
/// ```
public final class WhatsNewChecker: @unchecked Sendable {
    private let source: ReleaseNotesSource
    private let store: WhatsNewStore
    private let lock = NSLock()
    private var pendingLastSeen: String?

    /// A checker reading notes from `source` and keeping state in `store`. The defaults read the app bundle's
    /// `whatsnew` folder and `UserDefaults.standard`.
    public init(
        source: ReleaseNotesSource = BundleReleaseNotesSource(),
        store: WhatsNewStore = UserDefaultsWhatsNewStore()
    ) {
        self.source = source
        self.store = store
    }

    /// The running app's version from `bundle`'s `CFBundleShortVersionString`, or `""` (which fails open) when absent.
    public static func installedVersion(bundle: Bundle = .main) -> String {
        bundle.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? ""
    }

    /// Call once per launch. Appends `installedVersion` to the install history and returns the notes to show,
    /// newest first, with bullets in the user's `locale` (most preferred first). An empty result means show nothing;
    /// the version is then marked seen at once. Otherwise call ``markSeen()`` once the notes were shown, so a crash
    /// before display shows them again next launch. First launch and downgrades show nothing (see ``LaunchPlan``).
    @discardableResult
    public func onLaunch(
        installedVersion: String = WhatsNewChecker.installedVersion(),
        locale: [String] = Locale.preferredLanguages
    ) -> [WhatsNewEntry] {
        lock.lock()
        defer { lock.unlock() }
        let history = store.installHistory()
        let updated = InstallHistory.updated(history, current: installedVersion)
        if updated != history { store.setInstallHistory(updated) }

        let lastSeen = store.lastSeenVersion()
        let plan = LaunchPlan.make(notes: source.releaseNotes(), installed: installedVersion, lastSeen: lastSeen)
        guard !plan.show.isEmpty else {
            pendingLastSeen = nil
            if let next = plan.lastSeen, next != lastSeen { store.setLastSeenVersion(next) }
            return []
        }
        pendingLastSeen = plan.lastSeen
        return plan.show.map { notes in
            WhatsNewEntry(version: notes.version, date: notes.date, bullets: notes.bullets(preferredLocales: locale))
        }
    }

    /// Marks the notes returned by the last ``onLaunch(installedVersion:locale:)`` as seen. Call it after showing
    /// them; calling it again, or when nothing was shown, does nothing.
    public func markSeen() {
        lock.lock()
        defer { lock.unlock() }
        if let version = pendingLastSeen { store.setLastSeenVersion(version) }
        pendingLastSeen = nil
    }

    /// Every bundled release newest first, with bullets in the user's `locale`, for the full history list.
    /// ``WhatsNewHistoryEntry/isInstalled`` marks the latest install-history entry ("you are here").
    public func history(locale: [String] = Locale.preferredLanguages) -> [WhatsNewHistoryEntry] {
        let installed = store.installHistory().last.flatMap(AppVersion.init)
        var versions = Set<AppVersion>()
        return source.releaseNotes()
            .filter { versions.insert($0.version).inserted }
            .sorted { $1.version < $0.version }
            .map { notes in
                WhatsNewHistoryEntry(
                    version: notes.version, date: notes.date, bullets: notes.bullets(preferredLocales: locale),
                    isInstalled: notes.version == installed)
            }
    }
}
