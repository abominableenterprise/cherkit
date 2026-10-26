@testable import CherKitAnalytics
import XCTest

final class AnalyticsTestHostTests: XCTestCase {
    private let noClasses: (String) -> AnyClass? = { _ in nil }

    func testDetectsThisTestRun() {
        XCTAssertTrue(AnalyticsTestHost.isRunningTests)
    }

    func testDetectsXcodeTestHostEnvironment() {
        let env = ["XCTestConfigurationFilePath": "/tmp/x.xctestconfiguration"]
        XCTAssertTrue(AnalyticsTestHost.isRunningTests(environment: env, classNamed: noClasses))
    }

    func testDetectsLoadedXCTest() {
        XCTAssertTrue(AnalyticsTestHost.isRunningTests(environment: [:]) { $0 == "XCTestCase" ? XCTestCase.self : nil })
        XCTAssertTrue(AnalyticsTestHost.isRunningTests(environment: [:]) {
            $0 == "XCTest.XCTestCase" ? XCTestCase.self : nil
        })
    }

    func testNormalAppLaunchIsNotATest() {
        XCTAssertFalse(AnalyticsTestHost.isRunningTests(environment: ["HOME": "/var/mobile"], classNamed: noClasses))
    }
}
