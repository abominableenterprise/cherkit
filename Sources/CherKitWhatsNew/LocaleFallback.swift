/// Picks which notes locale to show: `pt-BR` -> `pt` -> (next preferred locale) -> `en`.
/// The contract is `fixtures/whatsnew/locale.json`.
public enum LocaleFallback {
    /// Locale used when none of the preferred ones match.
    public static let defaultLocale = "en"

    /// The key of `available` to use for `preferred` (most preferred first, e.g. `Locale.preferredLanguages`).
    /// Each preferred locale is tried, then shortened by dropping trailing subtags; ``defaultLocale`` is tried last.
    /// Matching ignores ASCII case and treats `_` like `-`. Returns the key as spelled in `available`, or `nil`.
    public static func resolve<Locales: Sequence>(preferred: [String], available: Locales) -> String?
    where Locales.Element == String {
        var byNormalized: [String: String] = [:]
        for key in available.sorted() where byNormalized[normalize(key)] == nil {
            byNormalized[normalize(key)] = key
        }
        for locale in preferred + [defaultLocale] {
            var tags = normalize(locale).split(separator: "-", omittingEmptySubsequences: false).map(String.init)
            while !tags.isEmpty, !(tags.count == 1 && tags[0].isEmpty) {
                if let key = byNormalized[tags.joined(separator: "-")] { return key }
                tags.removeLast()
            }
        }
        return nil
    }

    private static func normalize(_ locale: String) -> String {
        String(String.UnicodeScalarView(locale.trimmingASCIIWhitespace.unicodeScalars.map { scalar in
            if scalar == "_" { return "-" }
            if ("A"..."Z").contains(scalar), let lower = Unicode.Scalar(scalar.value + 32) { return lower }
            return scalar
        }))
    }
}
