import Foundation

/// Fetches the raw hosted config. ``URLSessionSunsetConfigSource`` is the built-in one; apps can supply their own.
public protocol SunsetConfigSource: Sendable {
    /// Returns the response body, or `nil` when the fetch fails, is not a success, or takes longer than `timeout`
    /// seconds. Must not throw for network errors: Sunset fails open and falls back to the cache.
    func fetch(timeout: TimeInterval) async -> Data?
}
