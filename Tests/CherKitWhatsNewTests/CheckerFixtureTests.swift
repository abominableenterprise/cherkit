import CherKitTestSupport
import CherKitWhatsNew
import Foundation
import XCTest

/// Runs `fixtures/whatsnew/checker.json` through a `BundleReleaseNotesSource` reading a temp folder and an in-memory
/// store; Kotlin's `CheckerFixtureTest` runs the same cases.
final class CheckerFixtureTests: XCTestCase {
    private struct Fixture: Decodable { let cases: [Case] }

    private struct Case: Decodable {
        let name: String
        let files: [String: AnyJSON]
        let store: StoreState
        let skipped: [String]?
        let steps: [Step]
    }

    struct StoreState: Decodable, Equatable {
        let lastSeen: String?
        let history: [String]
    }

    private struct Step: Decodable {
        let launch: String?
        let locale: [String]?
        let show: [Shown]?
        let markSeen: Bool?
        let history: [String]?
        let entries: [HistoryEntry]?
        let store: StoreState
    }

    private struct Shown: Decodable, Equatable {
        let version: String
        let date: String?
        let bullets: [String]
    }

    private struct HistoryEntry: Decodable, Equatable {
        let version: String
        let date: String?
        let bullets: [String]
        let isInstalled: Bool
    }

    func testCases() throws {
        let cases = try Fixtures.decode(Fixture.self, module: "whatsnew", name: "checker.json").cases
        XCTAssertFalse(cases.isEmpty)
        for testCase in cases {
            let directory = try TempNotesDirectory(files: testCase.files.mapValues(\.fileBytes))
            defer { directory.remove() }
            let skipped = SkipLog()
            let source = BundleReleaseNotesSource(directory: directory.url, onSkip: skipped.record)
            let store = FakeWhatsNewStore(lastSeen: testCase.store.lastSeen, history: testCase.store.history)
            let checker = WhatsNewChecker(source: source, store: store)
            for (index, step) in testCase.steps.enumerated() {
                let label = "\(testCase.name), step \(index + 1)"
                if let installed = step.launch {
                    let shown = checker.onLaunch(installedVersion: installed, locale: step.locale ?? [])
                    XCTAssertEqual(
                        shown.map { Shown(version: $0.version.description, date: $0.date, bullets: $0.bullets) },
                        step.show ?? [], label)
                } else if step.markSeen == true {
                    checker.markSeen()
                } else if let locale = step.history {
                    XCTAssertEqual(
                        checker.history(locale: locale).map {
                            HistoryEntry(
                                version: $0.version.description, date: $0.date, bullets: $0.bullets,
                                isInstalled: $0.isInstalled)
                        },
                        step.entries ?? [], label)
                } else {
                    XCTFail("unknown step: \(label)")
                }
                XCTAssertEqual(StoreState(lastSeen: store.lastSeenVersion(), history: store.installHistory()), step.store, label)
            }
            XCTAssertEqual(Set(skipped.names), Set(testCase.skipped ?? []), testCase.name)
        }
    }
}

private extension AnyJSON {
    /// A string is the raw file text (e.g. malformed JSON); anything else is the JSON itself.
    var fileBytes: Data {
        if case .string(let text) = self { return Data(text.utf8) }
        return data
    }
}
