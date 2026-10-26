import CherKitAnalytics

/// In-memory backend that records every call as "start", "stop" or "capture:<name>".
/// `failNext` makes the next call of that kind throw after it is recorded.
final class RecordingBackend: AnalyticsBackend {
    struct Failure: Error {}

    private(set) var calls: [String] = []
    var failNext: Set<String> = []

    func start() throws { try record("start", kind: "start") }
    func capture(_ name: String) throws { try record("capture:\(name)", kind: "capture") }
    func stop() throws { try record("stop", kind: "stop") }

    private func record(_ call: String, kind: String) throws {
        calls.append(call)
        if failNext.remove(kind) != nil { throw Failure() }
    }
}

/// An event with any name, so fixtures can also feed names an app's enum would never contain.
struct NamedEvent: AnalyticsEvent {
    let name: String
}
