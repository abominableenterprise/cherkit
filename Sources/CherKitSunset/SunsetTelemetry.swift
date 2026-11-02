import CherKitCore

/// The reference is immutable and only invoked on MainActor. This wrapper keeps the async checker Sendable
/// even for sinks whose protocol does not require Sendable; it never queues unstructured reporting tasks.
final class SunsetTelemetry: @unchecked Sendable {
    private let sink: any TelemetrySink

    init(_ sink: any TelemetrySink) { self.sink = sink }

    @MainActor
    func report(
        config: SunsetFetchResult,
        configValid: Bool,
        lookup: SunsetFetchResult?,
        lookupValid: Bool,
        decision: SunsetDecision
    ) {
        if let code = Self.code(config, valid: configValid, base: 1) {
            sink.recordError(domain: "cherkit.sunset", code: code, attributes: [:])
        }
        if let lookup, let code = Self.code(lookup, valid: lookupValid, base: 4) {
            sink.recordError(domain: "cherkit.sunset", code: code, attributes: [:])
        }
        let kind: String
        switch decision {
        case .upToDate: kind = "none"
        case .softUpdate(let prompt): kind = prompt.kind == .testBuild ? "test_build" : "soft"
        case .hardUpdate: kind = "hard"
        }
        sink.breadcrumb(name: "cherkit.sunset.decision", attributes: ["kind": kind])
    }

    private static func code(_ result: SunsetFetchResult, valid: Bool, base: Int) -> Int? {
        switch result {
        case .failed: return base
        case .timedOut: return base + 1
        case .body: return valid ? nil : base + 2
        }
    }
}
