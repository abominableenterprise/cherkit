import Foundation

/// Stores the last good config body so Sunset still works offline. ``UserDefaultsSunsetConfigCache`` is the built-in one.
public protocol SunsetConfigCache: Sendable {
    /// The last saved body, or `nil` when there is none or it cannot be read.
    func load() async -> Data?
    /// Saves `data`. Called only with bodies that parsed (``SunsetConfigChoice/Source/fetched``). Failures are ignored.
    func save(_ data: Data) async
}
