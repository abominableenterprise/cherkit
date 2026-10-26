import Foundation

/// Detects a process running XCTest, so apps can keep analytics off there:
/// `Analytics(backend: ..., buildEnabled: isReleaseBuild && !AnalyticsTestHost.isRunningTests)`.
public enum AnalyticsTestHost {
    /// True inside an Xcode test host app (XCTest environment variables) or any process that has loaded XCTest.
    public static var isRunningTests: Bool {
        isRunningTests(environment: ProcessInfo.processInfo.environment, classNamed: NSClassFromString)
    }

    static func isRunningTests(environment: [String: String], classNamed: (String) -> AnyClass?) -> Bool {
        let xcTestKeys = ["XCTestConfigurationFilePath", "XCTestBundlePath", "XCTestSessionIdentifier"]
        if xcTestKeys.contains(where: { environment[$0] != nil }) { return true }
        // Apple platforms register Objective-C class names; swift-corelibs-xctest on Linux uses module-qualified names.
        return classNamed("XCTestCase") != nil || classNamed("XCTest.XCTestCase") != nil
    }
}
