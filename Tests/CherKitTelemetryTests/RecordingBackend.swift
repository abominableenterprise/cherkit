import CherKitTelemetry

@MainActor
final class RecordingBackend: TelemetryBackend {
    enum Failure: Error { case expected }
    var calls: [String] = []
    var failNext: Set<String> = []
    var restrictionUpdates: [Bool] = []
    var appliedRestriction: Bool?
    var startRestrictions: [Bool?] = []
    var errors: [TelemetryError] = []
    var breadcrumbs: [String] = []
    var traceNames: [String] = []
    var attributes: [[String: Any]] = []
    var keys: [(String, Any)] = []
    var traceAttributes: [(String, String, Any)] = []
    var stops: [(String, SpanStatus)] = []
    var liveTraces: Set<String> = []
    private var nextID = 0

    private func call(_ name: String) throws {
        calls.append(name)
        if failNext.remove(name) != nil { throw Failure.expected }
    }

    func update(restricted: Bool) throws {
        restrictionUpdates.append(restricted)
        if failNext.remove("update") != nil { throw Failure.expected }
        appliedRestriction = restricted
    }
    func start() throws {
        startRestrictions.append(appliedRestriction)
        try call("start")
    }
    func stop() throws {
        liveTraces.removeAll()
        try call("stop")
    }
    func recordError(_ error: TelemetryError, attributes: [String: Any]) throws {
        try call("error")
        errors.append(error)
        self.attributes.append(attributes)
    }
    func breadcrumb(name: String, attributes: [String: Any]) throws {
        try call("breadcrumb")
        breadcrumbs.append(name)
        self.attributes.append(attributes)
    }
    func startTrace(name: String, attributes: [String: Any]) throws -> String {
        try call("startTrace")
        nextID += 1
        let id = "trace-\(nextID)"
        liveTraces.insert(id)
        traceNames.append(name)
        self.attributes.append(attributes)
        return id
    }
    func stopTrace(id: String, status: SpanStatus) throws {
        stops.append((id, status))
        liveTraces.remove(id)
        try call("stopTrace")
    }
    func setAttribute(traceID: String, key: String, value: Any) throws {
        try call("setAttribute")
        traceAttributes.append((traceID, key, value))
    }
    func setKey(_ key: String, value: Any) throws {
        try call("key")
        keys.append((key, value))
    }
}
