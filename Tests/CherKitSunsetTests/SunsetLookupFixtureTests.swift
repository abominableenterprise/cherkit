import CherKitCore
import CherKitSunset
import CherKitTestSupport
import Foundation
import XCTest

/// Runs every case in `fixtures/sunset/lookup.json`; Kotlin's `SunsetLookupFixtureTest` runs the same cases.
final class SunsetLookupFixtureTests: XCTestCase {
    private struct Expected: Decodable {
        let version: String
        let minimumOsVersion: String
        let trackViewUrl: String?
    }

    private struct LookupCase: Decodable {
        let name: String
        let text: String?
        let result: Expected?
    }

    private struct CasesOnly: Decodable {
        let cases: [LookupCase]
    }

    func testLookupCases() throws {
        let data = try Fixtures.data(module: "sunset", name: "lookup.json")
        let cases = try JSONDecoder().decode(CasesOnly.self, from: data).cases
        let root = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        let rawCases = try XCTUnwrap(root["cases"] as? [[String: Any]])
        XCTAssertFalse(cases.isEmpty)
        for (index, testCase) in cases.enumerated() {
            let body: Data
            if let text = testCase.text {
                body = Data(text.utf8)
            } else {
                let value = try XCTUnwrap(rawCases[index]["body"], testCase.name)
                body = try JSONSerialization.data(withJSONObject: value, options: [.fragmentsAllowed])
            }
            let expected = try testCase.result.map { result in
                SunsetLookupResult(
                    version: try XCTUnwrap(AppVersion(result.version), testCase.name),
                    minimumOSVersion: try XCTUnwrap(AppVersion(result.minimumOsVersion), testCase.name),
                    trackViewURL: result.trackViewUrl.flatMap(URL.init(string:))
                )
            }
            XCTAssertEqual(SunsetLookupResult.parse(body), expected, testCase.name)
        }
    }
}
