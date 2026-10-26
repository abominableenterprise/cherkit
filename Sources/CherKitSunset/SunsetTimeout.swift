import Foundation

/// Runs a fetch with a hard deadline, so a source that hangs (or ignores cancellation) can never delay the launch.
enum SunsetTimeout {
    /// The longest wait, so a huge or infinite timeout still ends.
    static let maximumSeconds: TimeInterval = 600

    /// `operation`'s result, or `nil` when it does not finish within `seconds` (it is then cancelled).
    /// A zero, negative or NaN `seconds` returns `nil` without starting `operation`.
    static func run(seconds: TimeInterval, _ operation: @escaping @Sendable () async -> Data?) async -> Data? {
        guard seconds > 0 else { return nil }
        return await withCheckedContinuation { continuation in
            let once = ResumeOnce(continuation)
            let work = Task { await operation() }
            let timer = Task {
                try? await Task.sleep(nanoseconds: nanoseconds(seconds))
                guard !Task.isCancelled else { return }
                work.cancel()
                once.resume(nil)
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
    private var continuation: CheckedContinuation<Data?, Never>?

    init(_ continuation: CheckedContinuation<Data?, Never>) {
        self.continuation = continuation
    }

    func resume(_ value: Data?) {
        lock.lock()
        let waiting = continuation
        continuation = nil
        lock.unlock()
        waiting?.resume(returning: value)
    }
}
