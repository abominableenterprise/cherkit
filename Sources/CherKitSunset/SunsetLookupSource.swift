import Foundation

/// Fetches the raw App Store lookup response. ``URLSessionSunsetLookupSource`` is the built-in one.
///
/// Opt in by passing one to ``SunsetChecker/init(source:cache:timeout:platform:lookup:telemetrySink:)``; it is only asked on the
/// ``SunsetChannel/store`` channel.
public protocol SunsetLookupSource: Sendable {
    /// Returns the response body, or `nil` when the fetch fails, is not a success, or takes longer than `timeout`
    /// seconds. Must not throw: a failed lookup means no lookup prompt.
    func fetch(timeout: TimeInterval) async -> Data?
}
