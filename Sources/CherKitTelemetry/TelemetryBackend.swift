/// Provider boundary. Calls run on the main actor and must return quickly, never block on networking.
/// Stop must discard queued work and support restarting. All failures are swallowed by the facade.
@MainActor
public protocol TelemetryBackend: AnyObject {
    /// Resume collection. A failed partial start is immediately rolled back with stop.
    func start() throws
    func stop() throws
    /// Synchronize errors-only mode, including before the first start. Default keeps existing adapters compatible.
    func update(restricted: Bool) throws
    func recordError(_ error: TelemetryError, attributes: [String: Any]) throws
    func breadcrumb(name: String, attributes: [String: Any]) throws
    /// Return an opaque ID unique among live traces, including concurrent traces with the same name.
    func startTrace(name: String, attributes: [String: Any]) throws -> String
    func stopTrace(id: String, status: SpanStatus) throws
    func setAttribute(traceID: String, key: String, value: Any) throws
    func setKey(_ key: String, value: Any) throws
}

public extension TelemetryBackend {
    func update(restricted: Bool) throws {}
}
