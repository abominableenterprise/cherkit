#if canImport(SwiftUI)
// Not run yet: Package.swift has no CherKitUITests target. Adding one is a Package.swift-only PR (AGENTS.md); until
// then run these locally by declaring `.testTarget(name: "CherKitUITests", dependencies: ["CherKitUI"])`.
import CherKitCore
@testable import CherKitUI
import CherKitWhatsNew
import Foundation
import XCTest

/// The `.whatsNew` wiring without UI automation: `onLaunch` once, the sheet only for non-empty notes, and `markSeen`
/// only after the sheet was dismissed.
@MainActor
final class WhatsNewPresenterTests: XCTestCase {
    private func checker(lastSeen: String? = "1.0.0", history: [String] = ["1.0.0"]) -> (WhatsNewChecker, MemoryStore) {
        let store = MemoryStore(lastSeen: lastSeen, history: history)
        let source = StaticNotesSource(files: [
            "1.1.0.json": #"{"version":"1.1.0","date":"2026-11-02","notes":{"en":["New export."]}}"#,
        ])
        return (WhatsNewChecker(source: source, store: store), store)
    }

    func testLaunchCheckPresentsNotesAndMarksSeenOnlyAfterDismiss() {
        let (checker, store) = checker()
        let presenter = WhatsNewPresenter()

        presenter.launchCheck(checker, installedVersion: "1.1.0", locale: ["en"])
        XCTAssertTrue(presenter.isPresented)
        XCTAssertEqual(presenter.entries.map(\.version.description), ["1.1.0"])
        XCTAssertEqual(presenter.entries.first?.bullets, ["New export."])
        XCTAssertEqual(store.lastSeenVersion(), "1.0.0", "not seen while the sheet is up")

        presenter.close()  // "Continue"
        XCTAssertFalse(presenter.isPresented)
        XCTAssertEqual(store.lastSeenVersion(), "1.0.0", "not seen until SwiftUI reports the dismissal")

        presenter.sheetDismissed()
        XCTAssertEqual(store.lastSeenVersion(), "1.1.0")
    }

    func testSwipeDismissMarksSeen() {
        let (checker, store) = checker()
        let presenter = WhatsNewPresenter()
        presenter.launchCheck(checker, installedVersion: "1.1.0", locale: ["en"])

        presenter.sheetDismissed()  // interactive dismissal: no close() first
        XCTAssertFalse(presenter.isPresented)
        XCTAssertEqual(store.lastSeenVersion(), "1.1.0")
    }

    func testLaunchCheckRunsOnce() {
        let (checker, store) = checker()
        let presenter = WhatsNewPresenter()
        presenter.launchCheck(checker, installedVersion: "1.1.0", locale: ["en"])
        presenter.sheetDismissed()
        store.setLastSeenVersion("1.0.0")  // would show again if onLaunch ran a second time

        presenter.launchCheck(checker, installedVersion: "1.1.0", locale: ["en"])
        XCTAssertFalse(presenter.isPresented)
        XCTAssertEqual(store.installHistory(), ["1.0.0", "1.1.0"])
    }

    func testNothingToShowPresentsNothing() {
        let (checker, store) = checker(lastSeen: "1.1.0", history: ["1.0.0", "1.1.0"])
        let presenter = WhatsNewPresenter()
        presenter.launchCheck(checker, installedVersion: "1.1.0", locale: ["en"])

        XCTAssertFalse(presenter.isPresented)
        XCTAssertTrue(presenter.entries.isEmpty)
        presenter.sheetDismissed()
        XCTAssertEqual(store.lastSeenVersion(), "1.1.0")
    }

    func testOfferCallsOnDismissOnceAfterDismissal() {
        let presenter = WhatsNewPresenter()
        var dismissals = 0
        presenter.offer(entries("2.0.0"), onDismiss: { dismissals += 1 })
        XCTAssertTrue(presenter.isPresented)
        XCTAssertEqual(dismissals, 0)

        presenter.close()
        XCTAssertEqual(dismissals, 0)
        presenter.sheetDismissed()
        presenter.sheetDismissed()
        XCTAssertEqual(dismissals, 1)
    }

    func testOfferIgnoresEmptyAndRepeatedEntries() {
        let presenter = WhatsNewPresenter()
        var dismissals = 0
        presenter.offer([], onDismiss: { dismissals += 1 })
        XCTAssertFalse(presenter.isPresented)

        presenter.offer(entries("2.0.0"), onDismiss: { dismissals += 1 })
        presenter.sheetDismissed()
        presenter.offer(entries("2.0.0"), onDismiss: { dismissals += 1 })
        XCTAssertFalse(presenter.isPresented, "the same entries again do not re-present")

        presenter.offer(entries("2.1.0"), onDismiss: { dismissals += 1 })
        XCTAssertTrue(presenter.isPresented)
        XCTAssertEqual(dismissals, 1)
    }

    private func entries(_ version: String) -> [WhatsNewEntry] {
        [WhatsNewEntry(version: AppVersion(version)!, date: nil, bullets: ["Something new."])]
    }
}

/// In-memory `WhatsNewStore`.
private final class MemoryStore: WhatsNewStore, @unchecked Sendable {
    private let lock = NSLock()
    private var lastSeen: String?
    private var history: [String]

    init(lastSeen: String?, history: [String]) {
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

/// Notes files held in memory.
private struct StaticNotesSource: ReleaseNotesSource {
    let files: [String: String]

    func notesFiles() -> [String: Data] { files.mapValues { Data($0.utf8) } }
}
#endif
