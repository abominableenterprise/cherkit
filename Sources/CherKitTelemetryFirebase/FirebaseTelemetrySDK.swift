import Foundation

/// Narrow SDK boundary; no Firebase configuration or network access in recording-fake tests.
@MainActor
protocol FirebaseTelemetrySDK: AnyObject {
    func setCrashCollectionEnabled(_ enabled: Bool) throws
    func setPerformanceCollectionEnabled(_ enabled: Bool) throws
    func setInstrumentationEnabled(_ enabled: Bool) throws
    func sendUnsentReports() throws
    func deleteUnsentReports() throws
    func record(_ error: NSError) throws
    func log(_ message: String) throws
    func setKey(_ key: String, value: String?) throws
    func startTrace(name: String) throws -> (any FirebaseTelemetryTrace)?
}

@MainActor
protocol FirebaseTelemetryTrace: AnyObject {
    func setValue(_ value: String, forAttribute key: String) throws
    func stop() throws
}
