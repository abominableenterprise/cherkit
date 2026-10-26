import CherKitCore
import CherKitTestSupport
@testable import CherKitWhatsNew
import XCTest

/// Runs `fixtures/whatsnew/notes-file.json`; Kotlin's `NotesFileFixtureTest` runs the same cases.
final class NotesFileFixtureTests: XCTestCase {
    private struct Fixture: Decodable { let cases: [Case] }

    private struct Case: Decodable {
        let name: String
        let fileName: String
        let file: AnyJSON?
        let text: String?
        let errors: [ErrorCase]
        let parsed: Parsed?
    }

    private struct Parsed: Decodable {
        let version: String
        let date: String?
        let show: Bool
        let notes: [String: [String]]
    }

    func testCases() throws {
        let cases = try Fixtures.decode(Fixture.self, module: "whatsnew", name: "notes-file.json").cases
        XCTAssertFalse(cases.isEmpty)
        for testCase in cases {
            let data = testCase.text.map { Data($0.utf8) } ?? testCase.file?.data ?? Data()
            let result = NotesFile.read(data, fileName: testCase.fileName)
            let errors = result.errors.map { ErrorCase(code: $0.code.rawValue, message: $0.message) }
            XCTAssertEqual(errors, testCase.errors, testCase.name)
            XCTAssertEqual(result.isValid, testCase.errors.isEmpty, testCase.name)
            if let parsed = testCase.parsed {
                let expected = ReleaseNotes(
                    version: try XCTUnwrap(AppVersion(parsed.version)), date: parsed.date, show: parsed.show,
                    notes: parsed.notes)
                XCTAssertEqual(result.notes, expected, testCase.name)
            }
        }
    }

    func testErrorCodesMatchFixtureStrings() {
        XCTAssertEqual(NotesFileErrorCode.storeTextTooLong.rawValue, "storeTextTooLong")
        XCTAssertEqual(NotesFileErrorCode.allCases.count, 10)
    }

    func testNotesFileVersionNeedsDigitsAndDots() {
        XCTAssertEqual(NotesFileRules.notesFileVersion("whatsnew/1.4.json"), AppVersion("1.4.0"))
        XCTAssertNil(NotesFileRules.notesFileVersion("v1.4.json"))
        XCTAssertNil(NotesFileRules.notesFileVersion("1.4 (2).json"))
        XCTAssertNil(NotesFileRules.notesFileVersion("1.4.0.md"))
    }
}
