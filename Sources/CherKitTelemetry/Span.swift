/// A best-effort trace handle. Closing it, or changing its owner's gates, invalidates it permanently.
@MainActor
public final class Span {
    private weak var telemetry: Telemetry?
    private let id: String?
    private let generation: UInt
    private var ended = false

    init(telemetry: Telemetry? = nil, id: String? = nil, generation: UInt = 0) {
        self.telemetry = telemetry
        self.id = id
        self.generation = generation
    }

    public func setAttribute(_ key: String, value: Any) {
        guard !ended, let id else { return }
        telemetry?.setAttribute(traceID: id, generation: generation, key: key, value: value)
    }

    public func end(status: SpanStatus = .ok) {
        guard !ended else { return }
        ended = true
        guard let id else { return }
        telemetry?.endTrace(id: id, generation: generation, status: status)
    }
}
