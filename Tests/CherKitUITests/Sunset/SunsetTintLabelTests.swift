#if canImport(SwiftUI)
@testable import CherKitUI
import SwiftUI
import XCTest

/// The hard block's automatic button label color: black on bright tints, white on dark ones (WCAG luminance).
final class SunsetTintLabelTests: XCTestCase {
    func testThresholdIsWhereWhiteDropsBelowThreeToOne() {
        let l = SunsetTintLabel.luminanceThreshold
        XCTAssertEqual(l, 0.30, accuracy: 1e-9)
        XCTAssertEqual(1.05 / (l + 0.05), 3, accuracy: 1e-9, "white on the fill")
        XCTAssertEqual((l + 0.05) / 0.05, 7, accuracy: 1e-9, "black on the fill")
    }

    func testRelativeLuminanceMatchesWCAG() {
        XCTAssertEqual(SunsetTintLabel.relativeLuminance(red: 1, green: 1, blue: 1), 1, accuracy: 1e-9)
        XCTAssertEqual(SunsetTintLabel.relativeLuminance(red: 0, green: 0, blue: 0), 0, accuracy: 1e-9)
        XCTAssertEqual(SunsetTintLabel.relativeLuminance(red: 1, green: 0, blue: 0), 0.2126, accuracy: 1e-9)
        // #777777 is a well-known reference: luminance ≈ 0.184.
        XCTAssertEqual(SunsetTintLabel.relativeLuminance(red: 0x77 / 255, green: 0x77 / 255, blue: 0x77 / 255), 0.184, accuracy: 0.001)
    }

    func testLightYellowGetsBlackAndDarkBlueGetsWhite() {
        XCTAssertTrue(SunsetTintLabel.prefersBlack(red: 1, green: 0.95, blue: 0.6), "light yellow")
        XCTAssertFalse(SunsetTintLabel.prefersBlack(red: 0, green: 0, blue: 0.5), "dark blue")
        XCTAssertFalse(SunsetTintLabel.prefersBlack(red: 0, green: 0.48, blue: 1), "system blue keeps today's white")
        XCTAssertTrue(SunsetTintLabel.prefersBlack(red: 1, green: 1, blue: 1), "white")
        XCTAssertFalse(SunsetTintLabel.prefersBlack(red: 0, green: 0, blue: 0), "black")
    }

    func testResolvesTheTintInTheEnvironment() {
        var light = EnvironmentValues()
        light.colorScheme = .light
        var dark = EnvironmentValues()
        dark.colorScheme = .dark
        for environment in [light, dark] {
            XCTAssertEqual(SunsetTintLabel.color(on: Color(red: 1, green: 0.95, blue: 0.6), in: environment), .black)
            XCTAssertEqual(SunsetTintLabel.color(on: Color(red: 0, green: 0, blue: 0.5), in: environment), .white)
        }
    }

    #if canImport(UIKit)
    func testAdaptiveTintFollowsLightAndDarkMode() {
        // An accent that is dark blue in light mode and light yellow in dark mode.
        let adaptive = Color(UIColor { traits in
            traits.userInterfaceStyle == .dark
                ? UIColor(red: 1, green: 0.95, blue: 0.6, alpha: 1)
                : UIColor(red: 0, green: 0, blue: 0.5, alpha: 1)
        })
        var light = EnvironmentValues()
        light.colorScheme = .light
        var dark = EnvironmentValues()
        dark.colorScheme = .dark
        XCTAssertEqual(SunsetTintLabel.color(on: adaptive, in: light), .white)
        XCTAssertEqual(SunsetTintLabel.color(on: adaptive, in: dark), .black)
    }
    #endif
}
#endif
