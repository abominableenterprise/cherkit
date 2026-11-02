import CherKitCore
import Foundation

/// Serial, nonblocking delivery from synchronous APIs to the main-actor sink.
/// Only fixed identifiers and empty attributes cross this boundary.
internal final class WhatsNewTelemetry: @unchecked Sendable {
    private let sink: TelemetrySink
    private let lock = NSLock()
    private var pending: Task<Void, Never>?

    init(_ sink: TelemetrySink) { self.sink = sink }

    func error(_ code: Int) { enqueue(code: code) }
    func shown() { enqueue(code: nil) }

    private func enqueue(code: Int?) {
        lock.lock()
        defer { lock.unlock() }
        let previous = pending
        let sink = sink
        pending = Task { @MainActor in
            await previous?.value
            if let code {
                sink.recordError(domain: "cherkit.whatsnew", code: code, attributes: [:])
            } else {
                sink.breadcrumb(name: "cherkit.whatsnew.shown", attributes: [:])
            }
        }
    }

    private func snapshot() -> Task<Void, Never>? {
        lock.lock()
        defer { lock.unlock() }
        return pending
    }

    /// Tests await all reports queued before this call, without blocking the main actor.
    func drain() async { await snapshot()?.value }
}

/// Lets the checker forward its sink through the default source without reporting the same failure twice.
internal protocol ReportingReleaseNotesSource: ReleaseNotesSource {
    func releaseNotes(reportingTo telemetry: WhatsNewTelemetry?) -> [ReleaseNotes]
}
