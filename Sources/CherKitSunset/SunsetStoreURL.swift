import Foundation

/// Accepts only plain `https` store links, with the same rules as Kotlin's `SunsetStoreUrl`.
///
/// Valid: `https://` (any case); a host of ASCII letters, digits, `.` and `-` (no user info, no IP literal), with an
/// optional port of 1 to 5 digits; then a path, query and fragment using only RFC 3986 URL characters (letters,
/// digits, `-._~:/?@!$&'()*+,;=`, `%` plus two hex digits, and at most one `#`). Strict on purpose, so Foundation's
/// `URL` and Kotlin agree on every accepted link.
enum SunsetStoreURL {
    static func validate(_ text: String) -> URL? {
        guard isValid(text) else { return nil }
        return URL(string: text)
    }

    static func isValid(_ text: String) -> Bool {
        let bytes = Array(text.utf8)
        let scheme = Array("https://".utf8)
        guard bytes.count > scheme.count, zip(bytes, scheme).allSatisfy({ lowercased($0) == $1 }) else { return false }
        let rest = bytes[scheme.count...]
        let authority = rest.prefix(while: { !"/?#".utf8.contains($0) })
        return isValidAuthority(Array(authority)) && isValidPathQueryFragment(Array(rest.dropFirst(authority.count)))
    }

    private static func isValidAuthority(_ bytes: [UInt8]) -> Bool {
        let parts = bytes.split(separator: UInt8(ascii: ":"), maxSplits: 1, omittingEmptySubsequences: false)
        let host = parts[0]
        guard !host.isEmpty, host.allSatisfy({ isAlphanumeric($0) || $0 == UInt8(ascii: ".") || $0 == UInt8(ascii: "-") })
        else { return false }
        guard parts.count == 2 else { return true }
        return (1...5).contains(parts[1].count) && parts[1].allSatisfy(isDigit)
    }

    private static func isValidPathQueryFragment(_ bytes: [UInt8]) -> Bool {
        var index = 0
        var fragments = 0
        while index < bytes.count {
            let byte = bytes[index]
            if byte == UInt8(ascii: "%") {
                guard index + 2 < bytes.count, isHexDigit(bytes[index + 1]), isHexDigit(bytes[index + 2]) else { return false }
                index += 3
                continue
            }
            if byte == UInt8(ascii: "#") {
                fragments += 1
                guard fragments == 1 else { return false }
            } else if !(isAlphanumeric(byte) || "-._~:/?@!$&'()*+,;=".utf8.contains(byte)) {
                return false
            }
            index += 1
        }
        return true
    }

    private static func lowercased(_ byte: UInt8) -> UInt8 {
        (UInt8(ascii: "A")...UInt8(ascii: "Z")).contains(byte) ? byte + 32 : byte
    }

    private static func isDigit(_ byte: UInt8) -> Bool {
        (UInt8(ascii: "0")...UInt8(ascii: "9")).contains(byte)
    }

    private static func isAlphanumeric(_ byte: UInt8) -> Bool {
        (UInt8(ascii: "a")...UInt8(ascii: "z")).contains(lowercased(byte)) || isDigit(byte)
    }

    private static func isHexDigit(_ byte: UInt8) -> Bool {
        isDigit(byte) || (UInt8(ascii: "a")...UInt8(ascii: "f")).contains(lowercased(byte))
    }
}
