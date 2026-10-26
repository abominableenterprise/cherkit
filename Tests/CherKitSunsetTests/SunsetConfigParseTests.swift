import CherKitCore
import CherKitSunset
import XCTest

/// Field-level checks of the parsed config, beyond the decisions in the shared fixtures.
final class SunsetConfigParseTests: XCTestCase {
    func testParsesEveryField() throws {
        let json = """
        {"schemaVersion": 1, "ios": {"minimumVersion": "1.2", "latestVersion": "1.4.0", "latestMinimumOS": "17.0",
         "storeURL": "https://apps.apple.com/app/id1", "message": {"en": "Update", "de": "Aktualisieren"}}}
        """
        let config = try XCTUnwrap(SunsetConfig.parse(Data(json.utf8)))
        XCTAssertNil(config.android)
        let ios = try XCTUnwrap(config.block(for: .ios))
        XCTAssertEqual(ios.minimumVersion, AppVersion("1.2.0"))
        XCTAssertEqual(ios.latestVersion, AppVersion("1.4"))
        XCTAssertEqual(ios.latestMinimumOS, AppVersion("17"))
        XCTAssertEqual(ios.storeURL, URL(string: "https://apps.apple.com/app/id1"))
        XCTAssertEqual(ios.messages, ["en": "Update", "de": "Aktualisieren"])
    }

    func testInvalidBlockDoesNotDropTheOtherPlatform() throws {
        let json = """
        {"schemaVersion": 1, "ios": {"minimumVersion": "x", "storeURL": "https://a.example"},
         "android": {"minimumVersion": "2", "storeURL": "https://b.example/p?id=1"}}
        """
        let config = try XCTUnwrap(SunsetConfig.parse(Data(json.utf8)))
        XCTAssertNil(config.ios)
        XCTAssertEqual(config.android?.minimumVersion, AppVersion("2"))
    }

    func testSchemaVersionMustBeExactlyOne() {
        XCTAssertNotNil(SunsetConfig.parse(Data(#"{"schemaVersion": 1.0}"#.utf8)))
        XCTAssertNil(SunsetConfig.parse(Data(#"{"schemaVersion": 1.5}"#.utf8)))
        XCTAssertNil(SunsetConfig.parse(Data(#"{"schemaVersion": 0}"#.utf8)))
        XCTAssertNil(SunsetConfig.parse(Data(#"{"schemaVersion": null}"#.utf8)))
    }

    func testPromptAccessor() throws {
        let prompt = SunsetPrompt(storeURL: try XCTUnwrap(URL(string: "https://a.example")), message: nil)
        XCTAssertNil(SunsetDecision.upToDate.prompt)
        XCTAssertEqual(SunsetDecision.softUpdate(prompt).prompt, prompt)
        XCTAssertEqual(SunsetDecision.hardUpdate(prompt).prompt, prompt)
    }
}
