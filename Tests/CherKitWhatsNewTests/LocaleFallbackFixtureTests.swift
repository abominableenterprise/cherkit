import CherKitCore
import CherKitTestSupport
import CherKitWhatsNew
import XCTest

/// Runs `fixtures/whatsnew/locale.json`; Kotlin's `LocaleFallbackFixtureTest` runs the same cases.
final class LocaleFallbackFixtureTests: XCTestCase {
    private struct Fixture: Decodable { let cases: [Case] }

    private struct Case: Decodable {
        let name: String
        let preferred: [String]
        let available: [String]
        let expected: String?
    }

    func testCases() throws {
        let cases = try Fixtures.decode(Fixture.self, module: "whatsnew", name: "locale.json").cases
        XCTAssertFalse(cases.isEmpty)
        for testCase in cases {
            let result = LocaleFallback.resolve(preferred: testCase.preferred, available: testCase.available)
            XCTAssertEqual(result, testCase.expected, testCase.name)
        }
    }

    func testBulletsUseFallback() throws {
        let notes = ReleaseNotes(version: try XCTUnwrap(AppVersion("1.0")), notes: ["en": ["Hi."], "pt": ["Olá."]])
        XCTAssertEqual(notes.bullets(preferredLocales: ["pt-BR"]), ["Olá."])
        XCTAssertEqual(notes.bullets(preferredLocales: ["ja"]), ["Hi."])
    }
}
