/// The event-name rule shared with Kotlin: `^[a-z][a-z0-9_]{0,63}$`, ASCII only.
/// Apps can call `isValid` in their own tests to check every case of their event type.
public enum AnalyticsEventName {
    public static let maxLength = 64

    public static func isValid(_ name: String) -> Bool {
        let bytes = Array(name.utf8)
        guard let first = bytes.first, bytes.count <= maxLength, isLowercaseLetter(first) else { return false }
        return bytes.dropFirst().allSatisfy { isLowercaseLetter($0) || isDigit($0) || $0 == UInt8(ascii: "_") }
    }

    private static func isLowercaseLetter(_ byte: UInt8) -> Bool {
        byte >= UInt8(ascii: "a") && byte <= UInt8(ascii: "z")
    }

    private static func isDigit(_ byte: UInt8) -> Bool {
        byte >= UInt8(ascii: "0") && byte <= UInt8(ascii: "9")
    }
}
