import Foundation

/// Loads the shared cross-platform JSON contract in `fixtures/<module>/`.
/// The repo root is found from this file's path, or from `CHERKIT_FIXTURES` (path to `fixtures/`) when set.
public enum Fixtures {
    public static var directory: URL {
        if let override = ProcessInfo.processInfo.environment["CHERKIT_FIXTURES"] {
            return URL(fileURLWithPath: override, isDirectory: true)
        }
        return URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // CherKitTestSupport
            .deletingLastPathComponent() // Tests
            .deletingLastPathComponent() // repo root
            .appendingPathComponent("fixtures", isDirectory: true)
    }

    public static func data(module: String, name: String) throws -> Data {
        try Data(contentsOf: directory.appendingPathComponent(module).appendingPathComponent(name))
    }

    public static func decode<T: Decodable>(_ type: T.Type, module: String, name: String) throws -> T {
        try JSONDecoder().decode(type, from: data(module: module, name: name))
    }
}
