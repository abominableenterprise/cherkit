import CherKitCore
import CherKitTestSupport
import CherKitWhatsNew
import XCTest

/// Runs `fixtures/whatsnew/launch.json`; Kotlin's `LaunchPlanFixtureTest` runs the same cases.
final class LaunchPlanFixtureTests: XCTestCase {
    private struct Fixture: Decodable { let cases: [Case] }

    private struct Case: Decodable {
        let name: String
        let notes: [Note]
        let installed: String
        let lastSeen: String?
        let expected: Expected
    }

    private struct Note: Decodable {
        let version: String
        let show: Bool
    }

    private struct Expected: Decodable {
        let show: [String]
        let lastSeen: String?
    }

    func testCases() throws {
        let cases = try Fixtures.decode(Fixture.self, module: "whatsnew", name: "launch.json").cases
        XCTAssertFalse(cases.isEmpty)
        for testCase in cases {
            // Each note's bullet records its version as written, so duplicates can be told apart.
            let notes = try testCase.notes.map { note in
                ReleaseNotes(
                    version: try XCTUnwrap(AppVersion(note.version), testCase.name), show: note.show,
                    notes: ["en": [note.version]])
            }
            let plan = LaunchPlan.make(notes: notes, installed: testCase.installed, lastSeen: testCase.lastSeen)
            XCTAssertEqual(plan.show.compactMap { $0.notes["en"]?.first }, testCase.expected.show, testCase.name)
            XCTAssertEqual(plan.lastSeen, testCase.expected.lastSeen, testCase.name)
        }
    }
}
