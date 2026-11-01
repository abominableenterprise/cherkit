#if canImport(SwiftUI)
@testable import CherKitUI
import CherKitWhatsNew
import XCTest

/// `.whatsNew(suppressed:)`: the sheet waits while suppressed (e.g. a Sunset prompt is up), is taken away without
/// counting as a dismissal, and comes back once it is no longer suppressed.
@MainActor
final class WhatsNewPresenterSuppressedTests: XCTestCase {
    func testSuppressedHoldsTheSheetUntilReleased() {
        let presenter = WhatsNewPresenter()
        var dismissals = 0
        presenter.setSuppressed(true)
        presenter.offer(whatsNewEntries("2.0.0"), onDismiss: { dismissals += 1 })
        XCTAssertFalse(presenter.isPresented, "waits while Sunset is up")

        presenter.setSuppressed(false)
        XCTAssertTrue(presenter.isPresented)
        presenter.sheetDismissed()
        XCTAssertEqual(dismissals, 1)
        presenter.setSuppressed(true)
        presenter.setSuppressed(false)
        XCTAssertFalse(presenter.isPresented, "a dismissed sheet does not come back")
    }

    func testSuppressingAShownSheetIsNotADismissal() {
        let presenter = WhatsNewPresenter()
        var dismissals = 0
        presenter.offer(whatsNewEntries("2.0.0"), onDismiss: { dismissals += 1 })
        XCTAssertTrue(presenter.isPresented)
        presenter.sheetAppeared()

        presenter.setSuppressed(true)
        XCTAssertFalse(presenter.isPresented)
        presenter.sheetDismissed()  // SwiftUI reports the sheet going away
        XCTAssertEqual(dismissals, 0, "markSeen must not run: the user never dismissed it")
        XCTAssertFalse(presenter.isPresented)

        presenter.setSuppressed(false)
        XCTAssertTrue(presenter.isPresented, "comes back with the same entries")
        XCTAssertEqual(presenter.entries.map(\.version.description), ["2.0.0"])
        presenter.close()
        presenter.sheetDismissed()
        XCTAssertEqual(dismissals, 1)
    }

    func testReleasedBeforeSwiftUIReportsTheHideWaitsForIt() {
        let presenter = WhatsNewPresenter()
        var dismissals = 0
        presenter.offer(whatsNewEntries("2.0.0"), onDismiss: { dismissals += 1 })
        presenter.sheetAppeared()
        presenter.setSuppressed(true)
        presenter.setSuppressed(false)
        XCTAssertFalse(presenter.isPresented, "not re-presented while the old sheet is still going away")

        presenter.sheetDismissed()
        XCTAssertTrue(presenter.isPresented, "re-presented once the old sheet is gone")
        XCTAssertEqual(dismissals, 0)
    }

    func testSuppressedBeforeTheSheetAppearedDoesNotWaitForADismissal() {
        let presenter = WhatsNewPresenter()
        var dismissals = 0
        presenter.offer(whatsNewEntries("2.0.0"), onDismiss: { dismissals += 1 })
        presenter.setSuppressed(true)  // before SwiftUI showed it: no onDismiss will come
        presenter.setSuppressed(false)
        XCTAssertTrue(presenter.isPresented, "never stuck waiting for a dismissal that cannot come")
        XCTAssertEqual(dismissals, 0)
    }

    func testDismissalWhileSuppressedIsNeverTheUsers() {
        let presenter = WhatsNewPresenter()
        var dismissals = 0
        presenter.offer(whatsNewEntries("2.0.0"), onDismiss: { dismissals += 1 })
        presenter.setSuppressed(true)  // requested but not yet shown, then SwiftUI shows and drops it anyway
        presenter.sheetAppeared()
        presenter.sheetDismissed()
        XCTAssertEqual(dismissals, 0, "markSeen must not run")
        presenter.setSuppressed(false)
        XCTAssertTrue(presenter.isPresented)
    }

    func testCheckerNotesWaitWhileSuppressed() {
        let store = RecordingWhatsNewStore(lastSeen: "1.0.0", history: ["1.0.0"])
        let source = StaticNotesSource(files: [
            "1.1.0.json": #"{"version":"1.1.0","date":"2026-11-02","notes":{"en":["New export."]}}"#,
        ])
        let checker = WhatsNewChecker(source: source, store: store)
        let presenter = WhatsNewPresenter()
        presenter.setSuppressed(true)
        presenter.launchCheck(checker, installedVersion: "1.1.0", locale: ["en"])
        XCTAssertFalse(presenter.isPresented)
        presenter.setSuppressed(false)
        XCTAssertTrue(presenter.isPresented)
        XCTAssertEqual(store.lastSeenWrites, [])
        presenter.sheetDismissed()
        XCTAssertEqual(store.lastSeenWrites, ["1.1.0"])
    }
}
#endif
