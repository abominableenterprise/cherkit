import CherKitTestSupport
import CherKitWhatsNew
import XCTest

/// Runs `fixtures/whatsnew/install-history.json`; Kotlin's `InstallHistoryFixtureTest` runs the same cases.
final class InstallHistoryFixtureTests: XCTestCase {
    private struct Fixture: Decodable { let cases: [Case] }

    private struct Case: Decodable {
        let name: String
        let history: [String]
        let current: String
        let cap: Int?
        let expected: [String]
    }

    func testCases() throws {
        let cases = try Fixtures.decode(Fixture.self, module: "whatsnew", name: "install-history.json").cases
        XCTAssertFalse(cases.isEmpty)
        for testCase in cases {
            let result = testCase.cap.map { InstallHistory.updated(testCase.history, current: testCase.current, cap: $0) }
                ?? InstallHistory.updated(testCase.history, current: testCase.current)
            XCTAssertEqual(result, testCase.expected, testCase.name)
        }
    }

    func testDefaultCapIs100() {
        let history = (0..<100).map { "1.\($0)" }
        let result = InstallHistory.updated(history, current: "2.0")
        XCTAssertEqual(result.count, 100)
        XCTAssertEqual(result.first, "1.1")
        XCTAssertEqual(result.last, "2.0")
    }
}
