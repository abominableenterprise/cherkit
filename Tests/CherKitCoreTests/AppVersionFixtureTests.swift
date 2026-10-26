import CherKitCore
import CherKitTestSupport
import XCTest

/// Runs every case in `fixtures/core/version.json`; Kotlin's `AppVersionFixtureTest` runs the same cases.
final class AppVersionFixtureTests: XCTestCase {
    private struct VersionFixture: Decodable {
        let parse: [ParseCase]
        let compare: [CompareCase]
    }

    private struct ParseCase: Decodable {
        let input: String
        /// `nil` means the input is malformed and must not parse.
        let components: [Int]?
    }

    private struct CompareCase: Decodable {
        let a: String
        let b: String
        let result: Int
    }

    private func loadFixture() throws -> VersionFixture {
        try Fixtures.decode(VersionFixture.self, module: "core", name: "version.json")
    }

    func testParseCases() throws {
        let cases = try loadFixture().parse
        XCTAssertFalse(cases.isEmpty)
        for testCase in cases {
            XCTAssertEqual(AppVersion(testCase.input)?.components, testCase.components, "parse \(testCase.input.debugDescription)")
        }
    }

    func testCompareCases() throws {
        let cases = try loadFixture().compare
        XCTAssertFalse(cases.isEmpty)
        for testCase in cases {
            let label = "\(testCase.a) vs \(testCase.b)"
            let a = try XCTUnwrap(AppVersion(testCase.a), label)
            let b = try XCTUnwrap(AppVersion(testCase.b), label)
            XCTAssertEqual(sign(a, b), testCase.result, label)
            XCTAssertEqual(sign(b, a), -testCase.result, "\(label), reversed")
            XCTAssertEqual(a == b, testCase.result == 0, "\(label), ==")
            if testCase.result == 0 { XCTAssertEqual(a.hashValue, b.hashValue, "\(label), hash") }
        }
    }

    func testDescriptionJoinsComponents() throws {
        XCTAssertEqual(try XCTUnwrap(AppVersion(" v1.02.3 (45) ")).description, "1.2.3")
    }

    private func sign(_ lhs: AppVersion, _ rhs: AppVersion) -> Int {
        lhs < rhs ? -1 : (rhs < lhs ? 1 : 0)
    }
}
