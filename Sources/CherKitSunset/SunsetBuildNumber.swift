/// A build number such as `123` or `4.5.6` (`CFBundleVersion`, the TestFlight `latestBuild`), compared component by
/// component.
///
/// Strict on purpose: one or more groups of 1 to ``maximumComponentDigits`` ASCII digits joined by single dots, nothing
/// else (no spaces, signs, letters or suffixes). Anything else does not parse, and callers then show no prompt. Missing
/// components count as 0, so `12 == 12.0`. The same rules as Kotlin's `SunsetBuildNumber`.
public struct SunsetBuildNumber: Comparable, Hashable, Sendable, CustomStringConvertible {
    /// Largest number of digits in one component, so every component fits in a 32-bit integer.
    public static let maximumComponentDigits = 9

    /// Numeric components in order, e.g. `[4, 5, 6]` for `"4.5.6"`. Never empty, never negative.
    public let components: [Int]

    /// Parses `text`, or returns `nil` when it is not a valid build number.
    public init?(_ text: String) {
        var components: [Int] = []
        for part in text.utf8.split(separator: UInt8(ascii: "."), omittingEmptySubsequences: false) {
            guard (1...Self.maximumComponentDigits).contains(part.count),
                  part.allSatisfy({ (UInt8(ascii: "0")...UInt8(ascii: "9")).contains($0) })
            else { return nil }
            components.append(part.reduce(0) { $0 * 10 + Int($1 - UInt8(ascii: "0")) })
        }
        self.components = components
    }

    /// The components joined with dots, e.g. `4.5.6`; leading zeros are not kept.
    public var description: String {
        components.map(String.init).joined(separator: ".")
    }

    public static func < (lhs: SunsetBuildNumber, rhs: SunsetBuildNumber) -> Bool {
        compare(lhs, rhs) < 0
    }

    /// Equal when every component is, so trailing zero components do not matter.
    public static func == (lhs: SunsetBuildNumber, rhs: SunsetBuildNumber) -> Bool {
        compare(lhs, rhs) == 0
    }

    public func hash(into hasher: inout Hasher) {
        var trimmed = components
        while trimmed.last == 0 { trimmed.removeLast() }
        hasher.combine(trimmed)
    }

    private static func compare(_ lhs: SunsetBuildNumber, _ rhs: SunsetBuildNumber) -> Int {
        for index in 0..<max(lhs.components.count, rhs.components.count) {
            let left = index < lhs.components.count ? lhs.components[index] : 0
            let right = index < rhs.components.count ? rhs.components[index] : 0
            if left != right { return left < right ? -1 : 1 }
        }
        return 0
    }
}
