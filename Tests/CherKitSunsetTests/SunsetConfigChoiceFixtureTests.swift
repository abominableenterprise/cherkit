import CherKitSunset
import CherKitTestSupport
import XCTest

/// Runs every case in `fixtures/sunset/config-choice.json`; Kotlin's `SunsetConfigChoiceFixtureTest` runs the same cases.
final class SunsetConfigChoiceFixtureTests: XCTestCase {
    private struct ChoiceFixture: Decodable {
        let cases: [ChoiceCase]
    }

    private struct ChoiceCase: Decodable {
        let name: String
        let fetched: String?
        let cached: String?
        let source: String
    }

    func testChoiceCases() throws {
        let cases = try Fixtures.decode(ChoiceFixture.self, module: "sunset", name: "config-choice.json").cases
        XCTAssertFalse(cases.isEmpty)
        for testCase in cases {
            let fetched = testCase.fetched.map { Data($0.utf8) }
            let cached = testCase.cached.map { Data($0.utf8) }
            let choice = SunsetConfigChoice.choose(fetched: fetched, cached: cached)
            XCTAssertEqual(choice.source.rawValue, testCase.source, testCase.name)
            let expectedBody = choice.source == .fetched ? fetched : (choice.source == .cached ? cached : nil)
            XCTAssertEqual(choice.config, expectedBody.flatMap(SunsetConfig.parse), testCase.name)
        }
    }
}
