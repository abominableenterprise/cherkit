import CherKitTelemetry
import Foundation

/// Crashlytics and manual Performance traces. Configure Firebase in the app, never in this backend.
/// Use through Telemetry so build/user gates apply; constructing it explicitly disables collection.
@MainActor
public final class FirebaseTelemetryBackend: TelemetryBackend {
    private let sdk: any FirebaseTelemetrySDK
    private let automaticInstrumentationEnabled: Bool
    private var running = false
    private var restricted = false
    private var keys: Set<String> = []
    private struct LiveTrace {
        let trace: any FirebaseTelemetryTrace
        var keys: Set<String> = ["status"]
    }
    private var traces: [String: LiveTrace] = [:]

    /// Automatic instrumentation (including URL capture) is OFF unless the app explicitly opts in.
    public convenience init(automaticInstrumentationEnabled: Bool = false) {
        self.init(sdk: LiveFirebaseTelemetrySDK(),
                  automaticInstrumentationEnabled: automaticInstrumentationEnabled)
    }

    init(sdk: any FirebaseTelemetrySDK, automaticInstrumentationEnabled: Bool = false) {
        self.sdk = sdk
        self.automaticInstrumentationEnabled = automaticInstrumentationEnabled
        disableCollection()
    }

    public func start() throws {
        guard !running else { return }
        do {
            try sdk.setInstrumentationEnabled(!restricted && automaticInstrumentationEnabled)
            try sdk.setPerformanceCollectionEnabled(!restricted)
            try sdk.setCrashCollectionEnabled(true)
            running = true
        } catch {
            disableCollection()
            throw error
        }
    }

    public func stop() {
        running = false
        finishTraces()
        clearKeys()
        disableCollection()
    }

    public func update(restricted: Bool) {
        self.restricted = restricted
        if restricted {
            finishTraces()
            clearKeys()
        }
        try? sdk.setInstrumentationEnabled(running && !restricted && automaticInstrumentationEnabled)
        try? sdk.setPerformanceCollectionEnabled(running && !restricted)
    }

    public func recordError(_ error: TelemetryError, attributes: [String: Any]) {
        guard running else { return }
        // No original Error, userInfo, message, description or per-error attributes cross the boundary.
        let sanitized = NSError(domain: error.domain ?? error.type, code: error.code ?? 0, userInfo: nil)
        try? sdk.record(sanitized)
    }

    public func breadcrumb(name: String, attributes: [String: Any]) {
        guard running, !restricted, FirebaseTelemetryValue.name(name) else { return }
        // Static event name only; don't turn attributes into free-form log text.
        try? sdk.log(name)
    }

    public func setKey(_ key: String, value: Any) {
        guard running, !restricted, FirebaseTelemetryValue.name(key),
              let text = FirebaseTelemetryValue.string(value),
              keys.contains(key) || keys.count < 64 else { return }
        keys.insert(key)
        try? sdk.setKey(key, value: text)
    }

    public func startTrace(name: String, attributes: [String: Any]) -> String {
        let id = UUID().uuidString
        guard running, !restricted, FirebaseTelemetryValue.name(name),
              let trace = try? sdk.startTrace(name: name) else { return id }
        traces[id] = LiveTrace(trace: trace)
        try? trace.setValue("ok", forAttribute: "status")
        for key in attributes.keys.sorted() {
            if let value = attributes[key] { setAttribute(traceID: id, key: key, value: value) }
        }
        return id
    }

    public func setAttribute(traceID: String, key: String, value: Any) {
        guard running, !restricted, key != "status", FirebaseTelemetryValue.attributeName(key),
              let text = FirebaseTelemetryValue.string(value), text.utf8.count <= 100,
              var live = traces[traceID], live.keys.contains(key) || live.keys.count < 5 else { return }
        live.keys.insert(key)
        traces[traceID] = live
        try? live.trace.setValue(text, forAttribute: key)
    }

    public func stopTrace(id: String, status: SpanStatus) {
        guard let live = traces.removeValue(forKey: id) else { return }
        try? live.trace.setValue(status == .error ? "error" : "ok", forAttribute: "status")
        try? live.trace.stop()
    }

    private func finishTraces() {
        let live = traces.values
        traces.removeAll()
        for entry in live { try? entry.trace.stop() }
    }

    private func clearKeys() {
        let old = keys
        keys.removeAll()
        for key in old { try? sdk.setKey(key, value: nil) }
    }

    private func disableCollection() {
        try? sdk.setInstrumentationEnabled(false)
        try? sdk.setPerformanceCollectionEnabled(false)
        try? sdk.setCrashCollectionEnabled(false)
        try? sdk.deleteUnsentReports()
    }
}
