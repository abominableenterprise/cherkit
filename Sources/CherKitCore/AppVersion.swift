import Foundation

/// A store-style app version such as `1.2.3`, compared component by component.
///
/// Parse with `AppVersion("1.2.3")`. Surrounding ASCII whitespace, a leading `v`/`V`, and a build or pre-release suffix
/// that starts with a space, `+` or `-` (`1.2.3 (45)`, `1.2.3+45`, `1.2.3-beta`) are accepted; the suffix is dropped.
/// Anything else returns `nil`, and callers should fail open on `nil`. Missing components count as 0, so
/// `1.2 == 1.2.0`. The contract is `fixtures/core/version.json`, shared with Kotlin's `AppVersion`.
public struct AppVersion: Comparable, Hashable, CustomStringConvertible, Sendable {
    /// Largest number of digits accepted in one component, so every component fits in 32 bits.
    public static let maxComponentDigits = 9

    /// Numeric components in order, e.g. `[1, 2, 3]` for `"1.2.3"`. Never empty, never negative.
    public let components: [Int]

    /// Parses `string`, or returns `nil` when it is not a valid version.
    public init?(_ string: String) {
        // ASCII-only trimming keeps the rules identical to Kotlin's, whose Unicode whitespace set differs.
        var scalars = string.unicodeScalars[...].drop(while: Self.isASCIIWhitespace)
        while let last = scalars.last, Self.isASCIIWhitespace(last) { scalars = scalars.dropLast() }
        if let first = scalars.first, first == "v" || first == "V" { scalars = scalars.dropFirst() }

        var components: [Int] = []
        var current = 0
        var digits = 0
        for scalar in scalars {
            if let digit = Self.asciiDigit(scalar) {
                digits += 1
                guard digits <= Self.maxComponentDigits else { return nil }
                current = current * 10 + digit
                continue
            }
            // Every non-digit ends a component, so it must follow at least one digit.
            guard digits > 0 else { return nil }
            components.append(current)
            current = 0
            digits = 0
            if scalar == "." { continue }
            guard Self.suffixSeparators.contains(scalar) else { return nil }
            self.components = components
            return
        }
        guard digits > 0 else { return nil }
        components.append(current)
        self.components = components
    }

    public var description: String {
        components.map(String.init).joined(separator: ".")
    }

    public static func < (lhs: AppVersion, rhs: AppVersion) -> Bool {
        lhs.compare(rhs) < 0
    }

    public static func == (lhs: AppVersion, rhs: AppVersion) -> Bool {
        lhs.compare(rhs) == 0
    }

    public func hash(into hasher: inout Hasher) {
        // Trailing zeros do not change equality, so they must not change the hash.
        hasher.combine(Array(components.reversed().drop(while: { $0 == 0 }).reversed()))
    }

    /// -1, 0 or 1 as `self` is lower than, equal to or higher than `other`.
    private func compare(_ other: AppVersion) -> Int {
        for index in 0..<max(components.count, other.components.count) {
            let lhs = index < components.count ? components[index] : 0
            let rhs = index < other.components.count ? other.components[index] : 0
            if lhs != rhs { return lhs < rhs ? -1 : 1 }
        }
        return 0
    }

    private static let suffixSeparators: Set<Unicode.Scalar> = [" ", "+", "-"]

    private static func isASCIIWhitespace(_ scalar: Unicode.Scalar) -> Bool {
        scalar == " " || (scalar.value >= 0x09 && scalar.value <= 0x0D)
    }

    private static func asciiDigit(_ scalar: Unicode.Scalar) -> Int? {
        guard scalar.value >= 0x30, scalar.value <= 0x39 else { return nil }
        return Int(scalar.value - 0x30)
    }
}
