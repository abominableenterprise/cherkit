import CherKitCore
import Foundation

/// Best-effort diagnostics. Apps supply the build gate (false for debug, tests and simulators).
/// Collection is lazy; enabled assignments synchronize the backend as in Analytics.
@MainActor
public final class Telemetry: TelemetrySink {
    public let buildEnabled: Bool
    public var enabled = true {
        willSet { if enabled != newValue { closeTraces() } }
        didSet {
            if enabled != oldValue { generation &+= 1 }
            syncBackend()
        }
    }
    /// Restricted sessions allow errors only. A change permanently invalidates existing spans.
    public var restricted = false {
        willSet { if restricted != newValue { closeTraces() } }
        didSet {
            if restricted != oldValue { generation &+= 1 }
            syncRestriction()
        }
    }
    public var isDisabled: Bool { !buildEnabled || !enabled }
    public static var disabled: Telemetry { Telemetry() }

    private let backend: TelemetryBackend?
    private var backendStarted = false
    private var generation: UInt = 0
    private var liveTraces: Set<String> = []

    public init(backend: TelemetryBackend, buildEnabled: Bool) {
        self.backend = backend
        self.buildEnabled = buildEnabled
        syncRestriction()
    }

    private init() {
        backend = nil
        buildEnabled = false
    }

    public func recordError(_ error: Error, attributes: [String: Any] = [:]) {
        let identity = error as NSError
        record(TelemetryError(type: String(reflecting: Swift.type(of: error)),
                              domain: identity.domain, code: identity.code), attributes: attributes)
    }

    public func recordError(domain: String, code: Int, attributes: [String: Any] = [:]) {
        record(TelemetryError(type: "Error", domain: domain, code: code), attributes: attributes)
    }

    private func record(_ error: TelemetryError, attributes: [String: Any]) {
        guard ready() else { return }
        try? backend?.recordError(error, attributes: TelemetryValidation.attributes(attributes))
    }

    public func breadcrumb(name: String, attributes: [String: Any] = [:]) {
        guard TelemetryValidation.name(name), ready(diagnostics: true) else { return }
        try? backend?.breadcrumb(name: name, attributes: TelemetryValidation.attributes(attributes))
    }

    public func setKey(_ key: String, value: Any) {
        guard TelemetryValidation.name(key), TelemetryValidation.value(value), ready(diagnostics: true) else { return }
        try? backend?.setKey(key, value: value)
    }

    public func startSpan(name: String, attributes: [String: Any] = [:]) -> Span {
        guard TelemetryValidation.name(name), ready(diagnostics: true), let backend else { return Span() }
        guard let id = try? backend.startTrace(name: name, attributes: TelemetryValidation.attributes(attributes))
        else { return Span() }
        liveTraces.insert(id)
        return Span(telemetry: self, id: id, generation: generation)
    }

    public func span<T>(_ name: String, attributes: [String: Any] = [:], _ body: () throws -> T) rethrows -> T {
        let handle = startSpan(name: name, attributes: attributes)
        do {
            let result = try body()
            handle.end()
            return result
        } catch {
            handle.end(status: .error)
            throw error
        }
    }

    public func span<T>(_ name: String, attributes: [String: Any] = [:], _ body: () async throws -> T) async rethrows -> T {
        let handle = startSpan(name: name, attributes: attributes)
        do {
            let result = try await body()
            handle.end()
            return result
        } catch {
            handle.end(status: .error)
            throw error
        }
    }

    func setAttribute(traceID: String, generation: UInt, key: String, value: Any) {
        guard active(generation), TelemetryValidation.name(key), TelemetryValidation.value(value) else { return }
        try? backend?.setAttribute(traceID: traceID, key: key, value: value)
    }

    func endTrace(id: String, generation: UInt, status: SpanStatus) {
        guard active(generation), liveTraces.remove(id) != nil else { return }
        try? backend?.stopTrace(id: id, status: status)
    }

    private func closeTraces() {
        let ids = liveTraces.sorted()
        liveTraces.removeAll()
        for id in ids { try? backend?.stopTrace(id: id, status: .ok) }
    }

    private func active(_ token: UInt) -> Bool {
        token == generation && !isDisabled && !restricted && backendStarted
    }

    private func ready(diagnostics: Bool = false) -> Bool {
        guard !isDisabled, !diagnostics || !restricted else { return false }
        if !backendStarted { startBackend() }
        return backendStarted
    }

    private func syncBackend() {
        if !isDisabled, !backendStarted { startBackend() }
        else if isDisabled, backendStarted {
            backendStarted = false
            try? backend?.stop()
        }
    }

    private func syncRestriction() {
        do {
            try backend?.update(restricted: restricted)
        } catch {
            guard backendStarted else { return }
            generation &+= 1
            closeTraces()
            backendStarted = false
            try? backend?.stop()
        }
    }

    private func startBackend() {
        guard let backend else { return }
        do {
            try backend.update(restricted: restricted)
            try backend.start()
            backendStarted = true
        } catch {
            backendStarted = false
            try? backend.stop()
        }
    }
}
