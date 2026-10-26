#if canImport(SwiftUI)
@testable import CherKitUI
import CherKitWhatsNew
import XCTest

/// The `.whatsNew(entries:onDismiss:)` wiring: new non-empty entries present the sheet, and `onDismiss` runs once per
/// dismissal, only after it.
@MainActor
final class WhatsNewPresenterEntriesTests: XCTestCase {
    func testOnDismissRunsOnceAfterDismissal() {
        let presenter = WhatsNewPresenter()
        var dismissals = 0
        presenter.offer(whatsNewEntries("2.0.0"), onDismiss: { dismissals += 1 })
        XCTAssertTrue(presenter.isPresented)
        XCTAssertEqual(dismissals, 0, "not on present")

        presenter.close()  // "Continue"
        XCTAssertEqual(dismissals, 0, "not until SwiftUI reports the dismissal")
        presenter.sheetDismissed()
        presenter.sheetDismissed()
        XCTAssertEqual(dismissals, 1)
    }

    func testOnDismissRunsOncePerDismissal() {
        let presenter = WhatsNewPresenter()
        var dismissals = 0
        presenter.offer(whatsNewEntries("2.0.0"), onDismiss: { dismissals += 1 })
        presenter.sheetDismissed()  // swipe
        XCTAssertEqual(dismissals, 1)

        presenter.offer(whatsNewEntries("2.1.0"), onDismiss: { dismissals += 1 })
        XCTAssertTrue(presenter.isPresented)
        XCTAssertEqual(dismissals, 1)
        presenter.close()  // "Continue"
        presenter.sheetDismissed()
        XCTAssertEqual(dismissals, 2)
    }

    func testEmptyAndRepeatedEntriesPresentNothing() {
        let presenter = WhatsNewPresenter()
        var dismissals = 0
        presenter.offer([], onDismiss: { dismissals += 1 })
        XCTAssertFalse(presenter.isPresented)
        presenter.sheetDismissed()
        XCTAssertEqual(dismissals, 0)

        presenter.offer(whatsNewEntries("2.0.0"), onDismiss: { dismissals += 1 })
        presenter.sheetDismissed()
        presenter.offer(whatsNewEntries("2.0.0"), onDismiss: { dismissals += 1 })
        XCTAssertFalse(presenter.isPresented, "the same entries again do not re-present")
        presenter.sheetDismissed()
        XCTAssertEqual(dismissals, 1)
    }
}
#endif
