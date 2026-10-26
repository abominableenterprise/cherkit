import CherKitCore
import CherKitTestSupport
import CherKitWhatsNew
import XCTest

/// Runs `fixtures/whatsnew/store-text.json`; Kotlin's `StoreTextFixtureTest` runs the same cases.
final class StoreTextFixtureTests: XCTestCase {
    private struct Fixture: Decodable { let cases: [Case] }

    private struct Case: Decodable {
        let name: String
        let notes: Notes
        let locale: String
        let limit: Int?
        let expected: Expected
    }

    private struct Notes: Decodable {
        let show: Bool
        let notes: [String: [String]]
    }

    private struct Expected: Decodable {
        let text: String?
        let length: Int?
        let tooLong: Int?
        let noNotes: Bool?
    }

    func testCases() throws {
        let cases = try Fixtures.decode(Fixture.self, module: "whatsnew", name: "store-text.json").cases
        XCTAssertFalse(cases.isEmpty)
        for testCase in cases {
            let notes = ReleaseNotes(
                version: try XCTUnwrap(AppVersion("1.0")), show: testCase.notes.show, notes: testCase.notes.notes)
            let result = testCase.limit.map { StoreText.make(for: notes, locale: testCase.locale, limit: $0) }
                ?? StoreText.make(for: notes, locale: testCase.locale)
            let expected = testCase.expected
            switch result {
            case .text(let text):
                XCTAssertNil(expected.tooLong, testCase.name)
                XCTAssertNil(expected.noNotes, testCase.name)
                if let want = expected.text { XCTAssertEqual(text, want, testCase.name) }
                if let want = expected.length { XCTAssertEqual(text.utf16.count, want, testCase.name) }
            case .tooLong(let length):
                XCTAssertEqual(length, expected.tooLong, testCase.name)
            case .noNotes:
                XCTAssertEqual(expected.noNotes, true, testCase.name)
            }
        }
    }
}
