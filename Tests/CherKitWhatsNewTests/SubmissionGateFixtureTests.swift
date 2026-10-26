import CherKitTestSupport
import CherKitWhatsNew
import XCTest

/// Runs `fixtures/whatsnew/submission-gate.json`; Kotlin's `SubmissionGateFixtureTest` runs the same cases.
final class SubmissionGateFixtureTests: XCTestCase {
    private struct Fixture: Decodable { let cases: [Case] }

    private struct Case: Decodable {
        let name: String
        let version: String
        let files: [String: AnyJSON]
        let expected: Expected
    }

    private struct Expected: Decodable {
        let status: String
        let errors: [ErrorCase]
    }

    func testCases() throws {
        let cases = try Fixtures.decode(Fixture.self, module: "whatsnew", name: "submission-gate.json").cases
        XCTAssertFalse(cases.isEmpty)
        for testCase in cases {
            let result = SubmissionGate.check(version: testCase.version, files: testCase.files.mapValues(\.data))
            let (status, errors): (String, [NotesFileError]) = {
                switch result {
                case .ok: return ("ok", [])
                case .missing: return ("missing", [])
                case .invalid(let errors): return ("invalid", errors)
                }
            }()
            XCTAssertEqual(status, testCase.expected.status, testCase.name)
            XCTAssertEqual(
                errors.map { ErrorCase(code: $0.code.rawValue, message: $0.message) }, testCase.expected.errors,
                testCase.name)
        }
    }
}
