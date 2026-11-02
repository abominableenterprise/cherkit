import Foundation

/// Runs a fetch with a hard deadline, so a source that hangs (or ignores cancellation) can never delay the launch.
enum SunsetTimeout {
    /// The longest wait, so a huge or infinite timeout still ends.
    static let maximumSeconds: TimeInterval = 600

    /// Preserves the fetch outcome, or returns `timedOut` when the deadline expires (then cancels the work).
    /// A zero, negative or NaN `seconds` times out without starting `operation`.
    static func run(
        seconds: TimeInterval,
        _ operation: @escaping @Sendable () async -> SunsetFetchResult
    ) async -> SunsetFetchResult {
        guard seconds > 0 else { return .timedOut }
        return await withCheckedContinuation { continuation in
            let once = ResumeOnce(continuation)
            let work = Task { await operation() }
            let timer = Task {
                try? await Task.sleep(nanoseconds: nanoseconds(seconds))
                guard !Task.isCancelled else { return }
                once.resume(.timedOut)
                work.cancel()
            }
            Task {
                once.resume(await work.value)
                timer.cancel()
            }
        }
    }

    /// `seconds` (positive) capped at ``maximumSeconds``, in nanoseconds.
    static func nanoseconds(_ seconds: TimeInterval) -> UInt64 {
        UInt64(min(seconds, maximumSeconds) * 1_000_000_000)
    }
}

/// Resumes a continuation once, with whichever result arrives first.
private final class ResumeOnce: @unchecked Sendable {
    private let lock = NSLock()
    private var continuation: CheckedContinuation<SunsetFetchResult, Never>?

    init(_ continuation: CheckedContinuation<SunsetFetchResult, Never>) {
        self.continuation = continuation
    }

    func resume(_ value: SunsetFetchResult) {
        lock.lock()
        let waiting = continuation
        continuation = nil
        lock.unlock()
        waiting?.resume(returning: value)
    }
}
