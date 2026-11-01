import CherKitSunset
import CherKitTestSupport
import Foundation
import XCTest

/// Runs every case in `fixtures/sunset/test-build-prompt.json`; Kotlin's `SunsetTestBuildPromptFixtureTest` runs the
/// same cases.
final class SunsetTestBuildPromptFixtureTests: XCTestCase {
    private struct PromptCase: Decodable {
        let name: String
        let decision: String
        let kind: String?
        let latestBuild: String?
        let rememberedBuild: String?
        let shouldShow: Bool
        let action: String?
        let rememberedAfter: String?
    }

    private struct CasesOnly: Decodable {
        let cases: [PromptCase]
    }

    func testPromptCases() throws {
        let cases = try Fixtures.decode(CasesOnly.self, module: "sunset", name: "test-build-prompt.json").cases
        XCTAssertFalse(cases.isEmpty)
        for testCase in cases {
            let prompt = try testCase.kind.map { kind in
                SunsetPrompt(
                    storeURL: SunsetTestFlightConfig.defaultURL,
                    message: nil,
                    kind: try XCTUnwrap(SunsetUpdateKind(rawValue: kind), testCase.name),
                    latestBuild: try testCase.latestBuild.map { try XCTUnwrap(SunsetBuildNumber($0), testCase.name) }
                )
            }
            let decision: SunsetDecision
            switch testCase.decision {
            case "softUpdate": decision = .softUpdate(try XCTUnwrap(prompt, testCase.name))
            case "hardUpdate": decision = .hardUpdate(try XCTUnwrap(prompt, testCase.name))
            default: decision = .upToDate
            }
            XCTAssertEqual(
                SunsetTestBuildPromptRule.shouldShow(decision, rememberedBuild: testCase.rememberedBuild),
                testCase.shouldShow,
                testCase.name
            )
            if let action = testCase.action, let prompt {
                let remembered = SunsetTestBuildPromptRule.buildToRemember(
                    after: try XCTUnwrap(SunsetPromptAction(rawValue: action), testCase.name),
                    on: prompt
                )
                XCTAssertEqual(remembered, testCase.rememberedAfter, testCase.name)
            }
        }
    }
}
