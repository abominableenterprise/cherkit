import CherKitSunset
import Foundation
import XCTest

/// `URLSessionSunsetLookupSource(bundle:locale:)`: the one-line App Store lookup for the running app.
final class SunsetLookupSourceBundleTests: XCTestCase {
    func testCountryIsTheLowercaseRegion() {
        XCTAssertEqual(URLSessionSunsetLookupSource.country(for: Locale(identifier: "en_US")), "us")
        XCTAssertEqual(URLSessionSunsetLookupSource.country(for: Locale(identifier: "de_CH")), "ch")
        XCTAssertEqual(URLSessionSunsetLookupSource.country(for: Locale(identifier: "fr-CA")), "ca")
    }

    func testNoCountryWithoutATwoLetterRegion() {
        XCTAssertNil(URLSessionSunsetLookupSource.country(for: Locale(identifier: "es_419")))
    }

    func testBundleWithoutAnIdentifierHasNoLookup() {
        let bundle = Bundle(path: NSTemporaryDirectory()) ?? Bundle(for: Self.self)
        guard bundle.bundleIdentifier == nil else { return }
        XCTAssertNil(URLSessionSunsetLookupSource(bundle: bundle, locale: Locale(identifier: "en_US")))
    }

    func testBundleSourceAsksForItsIdentifierAndCountry() throws {
        let bundle = Bundle(for: Self.self)
        guard let bundleID = bundle.bundleIdentifier else { throw XCTSkip("the test bundle has no identifier here") }
        let source = try XCTUnwrap(URLSessionSunsetLookupSource(bundle: bundle, locale: Locale(identifier: "en_GB")))
        let expected = try XCTUnwrap(URLSessionSunsetLookupSource(bundleID: bundleID, country: "gb"))
        XCTAssertEqual(source.url, expected.url)
    }
}
