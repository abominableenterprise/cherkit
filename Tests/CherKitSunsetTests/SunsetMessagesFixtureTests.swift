import CherKitSunset
import CherKitTestSupport
import XCTest

/// Runs every case in `fixtures/sunset/message.json`; Kotlin's `SunsetMessagesFixtureTest` runs the same cases.
final class SunsetMessagesFixtureTests: XCTestCase {
    private struct MessageFixture: Decodable {
        let cases: [MessageCase]
    }

    private struct MessageCase: Decodable {
        let name: String
        let messages: [String: String]
        let languages: [String]
        let expected: String?
    }

    func testMessageCases() throws {
        let cases = try Fixtures.decode(MessageFixture.self, module: "sunset", name: "message.json").cases
        XCTAssertFalse(cases.isEmpty)
        for testCase in cases {
            XCTAssertEqual(SunsetMessages.resolve(testCase.messages, languages: testCase.languages), testCase.expected, testCase.name)
        }
    }
}
