import CherKitSunset
import CherKitTestSupport
import Foundation
import XCTest

/// Runs every case in `fixtures/sunset/decision.json`; Kotlin's `SunsetDecisionFixtureTest` runs the same cases.
final class SunsetDecisionFixtureTests: XCTestCase {
    private struct DecisionCase: Decodable {
        let name: String
        let config: String?
        let configText: String?
        let platform: String
        let appVersion: String
        let osVersion: String
        let languages: [String]
        let decision: String
        let storeURL: String?
        let message: String?
    }

    private struct CasesOnly: Decodable {
        let cases: [DecisionCase]
    }

    func testDecisionCases() throws {
        let data = try Fixtures.data(module: "sunset", name: "decision.json")
        let cases = try JSONDecoder().decode(CasesOnly.self, from: data).cases
        // Configs stay raw JSON objects so each one can be served back as a hosted body.
        let root = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        let configs = try XCTUnwrap(root["configs"] as? [String: Any])
        XCTAssertFalse(cases.isEmpty)
        for testCase in cases {
            let platform = try XCTUnwrap(SunsetPlatform(rawValue: testCase.platform), testCase.name)
            let body: Data?
            if let name = testCase.config {
                body = try JSONSerialization.data(withJSONObject: try XCTUnwrap(configs[name], testCase.name))
            } else {
                body = testCase.configText.map { Data($0.utf8) }
            }
            let decision = SunsetDecider.decide(
                config: body.flatMap(SunsetConfig.parse),
                platform: platform,
                appVersion: testCase.appVersion,
                osVersion: testCase.osVersion,
                languages: testCase.languages
            )
            XCTAssertEqual(decision, try expected(testCase), testCase.name)
        }
    }

    private func expected(_ testCase: DecisionCase) throws -> SunsetDecision {
        if testCase.decision == "upToDate" { return .upToDate }
        let url = try XCTUnwrap(URL(string: try XCTUnwrap(testCase.storeURL, testCase.name)), testCase.name)
        let prompt = SunsetPrompt(storeURL: url, message: testCase.message)
        switch testCase.decision {
        case "softUpdate": return .softUpdate(prompt)
        case "hardUpdate": return .hardUpdate(prompt)
        default:
            XCTFail("unknown decision \(testCase.decision) in \(testCase.name)")
            return .upToDate
        }
    }
}
