#if canImport(SwiftUI)
@testable import CherKitUI
import CherKitWhatsNew
import XCTest

/// The `.whatsNew(checker:)` wiring without UI automation: `onLaunch` once, the sheet only for non-empty notes, and
/// `markSeen` exactly once, only after the sheet was dismissed ("Continue" or swipe).
@MainActor
final class WhatsNewPresenterTests: XCTestCase {
    private func checker(
        lastSeen: String? = "1.0.0", history: [String] = ["1.0.0"]
    ) -> (WhatsNewChecker, RecordingWhatsNewStore) {
        let store = RecordingWhatsNewStore(lastSeen: lastSeen, history: history)
        let source = StaticNotesSource(files: [
            "1.1.0.json": #"{"version":"1.1.0","date":"2026-11-02","notes":{"en":["New export."]}}"#,
        ])
        return (WhatsNewChecker(source: source, store: store), store)
    }

    func testPresentDoesNotMarkSeen() {
        let (checker, store) = checker()
        let presenter = WhatsNewPresenter()

        presenter.launchCheck(checker, installedVersion: "1.1.0", locale: ["en"])
        XCTAssertTrue(presenter.isPresented)
        XCTAssertEqual(presenter.entries.map(\.version.description), ["1.1.0"])
        XCTAssertEqual(presenter.entries.first?.bullets, ["New export."])
        XCTAssertEqual(store.lastSeenWrites, [], "markSeen must not run while the sheet is up")
        XCTAssertEqual(store.lastSeenVersion(), "1.0.0")
    }

    func testContinueMarksSeenOnceAfterDismissal() {
        let (checker, store) = checker()
        let presenter = WhatsNewPresenter()
        presenter.launchCheck(checker, installedVersion: "1.1.0", locale: ["en"])

        presenter.close()  // "Continue"
        XCTAssertFalse(presenter.isPresented)
        XCTAssertEqual(store.lastSeenWrites, [], "not seen until SwiftUI reports the dismissal")

        presenter.sheetDismissed()
        presenter.sheetDismissed()  // a stray second report must not mark seen again
        XCTAssertEqual(store.lastSeenWrites, ["1.1.0"])
        XCTAssertEqual(store.lastSeenVersion(), "1.1.0")
    }

    func testSwipeDismissMarksSeenOnce() {
        let (checker, store) = checker()
        let presenter = WhatsNewPresenter()
        presenter.launchCheck(checker, installedVersion: "1.1.0", locale: ["en"])

        presenter.sheetDismissed()  // interactive dismissal: no close() first
        presenter.sheetDismissed()
        XCTAssertFalse(presenter.isPresented)
        XCTAssertEqual(store.lastSeenWrites, ["1.1.0"])
    }

    func testLaunchCheckRunsOnce() {
        let (checker, store) = checker()
        let presenter = WhatsNewPresenter()
        presenter.launchCheck(checker, installedVersion: "1.1.0", locale: ["en"])
        presenter.sheetDismissed()
        store.forgetLastSeen("1.0.0")  // would show again if onLaunch ran a second time

        presenter.launchCheck(checker, installedVersion: "1.1.0", locale: ["en"])
        XCTAssertFalse(presenter.isPresented)
        XCTAssertEqual(store.installHistory(), ["1.0.0", "1.1.0"])
        XCTAssertEqual(store.lastSeenWrites, ["1.1.0"])
    }

    func testNothingToShowPresentsNothing() {
        let (checker, store) = checker(lastSeen: "1.1.0", history: ["1.0.0", "1.1.0"])
        let presenter = WhatsNewPresenter()
        presenter.launchCheck(checker, installedVersion: "1.1.0", locale: ["en"])

        XCTAssertFalse(presenter.isPresented)
        XCTAssertTrue(presenter.entries.isEmpty)
        presenter.sheetDismissed()
        XCTAssertEqual(store.lastSeenWrites, [])
    }

    func testPresentIgnoresEmptyEntries() {
        let presenter = WhatsNewPresenter()
        var dismissals = 0
        presenter.present([], onDismiss: { dismissals += 1 })

        XCTAssertFalse(presenter.isPresented)
        XCTAssertTrue(presenter.entries.isEmpty)
        presenter.sheetDismissed()
        XCTAssertEqual(dismissals, 0, "nothing was shown, so there is nothing to dismiss")
    }
}
#endif
