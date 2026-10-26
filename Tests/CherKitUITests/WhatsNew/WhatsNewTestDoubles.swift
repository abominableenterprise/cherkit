#if canImport(SwiftUI)
import CherKitCore
import CherKitWhatsNew
import Foundation

/// In-memory `WhatsNewStore` that records every `setLastSeenVersion` write, so a test can tell how often `markSeen`
/// reached the store.
final class RecordingWhatsNewStore: WhatsNewStore, @unchecked Sendable {
    private let lock = NSLock()
    private var lastSeen: String?
    private var history: [String]
    private var writes: [String] = []

    init(lastSeen: String?, history: [String]) {
        self.lastSeen = lastSeen
        self.history = history
    }

    /// Every version written with `setLastSeenVersion`, oldest first.
    var lastSeenWrites: [String] { locked { writes } }

    private func locked<T>(_ body: () -> T) -> T {
        lock.lock()
        defer { lock.unlock() }
        return body()
    }

    func lastSeenVersion() -> String? { locked { lastSeen } }
    func setLastSeenVersion(_ version: String) {
        locked {
            lastSeen = version
            writes.append(version)
        }
    }
    func installHistory() -> [String] { locked { history } }
    func setInstallHistory(_ history: [String]) { locked { self.history = history } }

    /// Puts `lastSeen` back without recording a write, as if the user had never seen the notes.
    func forgetLastSeen(_ version: String) { locked { lastSeen = version } }
}

/// Notes files held in memory.
struct StaticNotesSource: ReleaseNotesSource {
    let files: [String: String]

    func notesFiles() -> [String: Data] { files.mapValues { Data($0.utf8) } }
}

/// One release with a single bullet, for the entries overload.
func whatsNewEntries(_ version: String) -> [WhatsNewEntry] {
    [WhatsNewEntry(version: AppVersion(version)!, date: nil, bullets: ["Something new."])]
}
#endif
