import CherKitWhatsNew
import Foundation

/// In-memory `WhatsNewStore`.
final class FakeWhatsNewStore: WhatsNewStore, @unchecked Sendable {
    private let lock = NSLock()
    private var lastSeen: String?
    private var history: [String]

    init(lastSeen: String? = nil, history: [String] = []) {
        self.lastSeen = lastSeen
        self.history = history
    }

    private func locked<T>(_ body: () -> T) -> T {
        lock.lock()
        defer { lock.unlock() }
        return body()
    }

    func lastSeenVersion() -> String? { locked { lastSeen } }
    func setLastSeenVersion(_ version: String) { locked { lastSeen = version } }
    func installHistory() -> [String] { locked { history } }
    func setInstallHistory(_ history: [String]) { locked { self.history = history } }
}

/// Collects the file names passed to a `ReleaseNotesLoader.SkipHandler`.
final class SkipLog: @unchecked Sendable {
    private let lock = NSLock()
    private var recorded: [String] = []

    var names: [String] { locked { recorded } }

    private func locked<T>(_ body: () -> T) -> T {
        lock.lock()
        defer { lock.unlock() }
        return body()
    }

    var record: ReleaseNotesLoader.SkipHandler {
        { [self] name, _ in locked { recorded.append(name) } }
    }
}

/// A fresh temp folder holding `files` (name -> content).
struct TempNotesDirectory {
    let url: URL

    init(files: [String: Data], subdirectory: String? = nil) throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
        url = subdirectory.map { root.appendingPathComponent($0, isDirectory: true) } ?? root
        try FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        for (name, data) in files { try data.write(to: url.appendingPathComponent(name)) }
        self.root = root
    }

    private let root: URL

    func remove() { try? FileManager.default.removeItem(at: root) }
}
