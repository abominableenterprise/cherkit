import Foundation

extension AttributionEnvironment {
    static var current: Self {
        #if os(iOS)
        let platform = "iOS"
        #else
        let platform = "other"
        #endif
        #if DEBUG
        let isDebug = true
        #else
        let isDebug = false
        #endif
        #if targetEnvironment(simulator)
        let isSimulator = true
        #else
        let isSimulator = false
        #endif
        return Self(platform: platform, isDebug: isDebug,
                    isTestHost: runningTests(environment: ProcessInfo.processInfo.environment,
                                             classNamed: NSClassFromString),
                    isSimulator: isSimulator)
    }

    static func runningTests(environment: [String: String], classNamed: (String) -> AnyClass?) -> Bool {
        let keys = ["XCTestConfigurationFilePath", "XCTestBundlePath", "XCTestSessionIdentifier",
                    "SWIFT_TESTING_ENABLED"]
        return keys.contains { environment[$0] != nil }
            || classNamed("XCTestCase") != nil || classNamed("XCTest.XCTestCase") != nil
    }
}
