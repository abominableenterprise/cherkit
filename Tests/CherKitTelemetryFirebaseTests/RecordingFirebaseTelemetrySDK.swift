import Foundation
@testable import CherKitTelemetryFirebase

/// Records attempted calls separately from successful state changes, including rollback failures.
@MainActor
final class RecordingFirebaseTelemetrySDK: FirebaseTelemetrySDK {
    enum Failure: Error { case requested }

    var calls: [String] = []
    var failures: Set<String> = []
    var crashEnabled = true
    var performanceEnabled = true
    var instrumentationEnabled = true
    var deletedReports = 0
    // Firebase resolves only the first send/delete decision in a process.
    var reportDecision: String?
    var errors: [NSError] = []
    var logs: [String] = []
    var keys: [String: String] = [:]
    var traces: [RecordingFirebaseTelemetryTrace] = []
    var returnsNilTrace = false
    var newTraceFailures: Set<String> = []

    func attempt(_ call: String) throws {
        calls.append(call)
        if failures.contains(call) { throw Failure.requested }
    }

    func setCrashCollectionEnabled(_ enabled: Bool) throws {
        try attempt("crash.\(enabled)")
        crashEnabled = enabled
    }

    func setPerformanceCollectionEnabled(_ enabled: Bool) throws {
        try attempt("performance.\(enabled)")
        performanceEnabled = enabled
    }

    func setInstrumentationEnabled(_ enabled: Bool) throws {
        try attempt("instrumentation.\(enabled)")
        instrumentationEnabled = enabled
    }

    func sendUnsentReports() throws {
        try attempt("send")
        if reportDecision == nil { reportDecision = "send" }
    }

    func deleteUnsentReports() throws {
        try attempt("delete")
        deletedReports += 1
        if reportDecision == nil { reportDecision = "delete" }
    }

    func record(_ error: NSError) throws {
        try attempt("record")
        errors.append(error)
    }

    func log(_ message: String) throws {
        try attempt("log")
        logs.append(message)
    }

    func setKey(_ key: String, value: String?) throws {
        try attempt(value == nil ? "clearKey" : "setKey")
        keys[key] = value
    }

    func startTrace(name: String) throws -> (any FirebaseTelemetryTrace)? {
        try attempt("startTrace")
        guard !returnsNilTrace else { return nil }
        let trace = RecordingFirebaseTelemetryTrace(name: name)
        trace.failures = newTraceFailures
        traces.append(trace)
        return trace
    }
}

@MainActor
final class RecordingFirebaseTelemetryTrace: FirebaseTelemetryTrace {
    let name: String
    var attributes: [String: String] = [:]
    var assignments: [(String, String)] = []
    var stopAttempts = 0
    var failures: Set<String> = []

    init(name: String) { self.name = name }

    func setValue(_ value: String, forAttribute key: String) throws {
        assignments.append((key, value))
        if failures.contains("setValue") { throw RecordingFirebaseTelemetrySDK.Failure.requested }
        attributes[key] = value
    }

    func stop() throws {
        stopAttempts += 1
        if failures.contains("stop") { throw RecordingFirebaseTelemetrySDK.Failure.requested }
    }
}
