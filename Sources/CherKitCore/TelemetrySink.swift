/// Minimal diagnostics interface for modules that do not depend on a telemetry provider.
@MainActor
public protocol TelemetrySink: AnyObject {
    func recordError(domain: String, code: Int, attributes: [String: Any])
    func breadcrumb(name: String, attributes: [String: Any])
}
