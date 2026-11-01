import Foundation

/// Accepts a `testFlight.url`: `itms-beta://` or `https://` (any case, any host), with the rest following the
/// `storeURL` rules (``SunsetStoreURL``). The same rules as Kotlin's `SunsetTestFlightUrl`.
enum SunsetTestFlightURL {
    static func validate(_ text: String) -> URL? {
        guard isValid(text) else { return nil }
        return URL(string: text)
    }

    static func isValid(_ text: String) -> Bool {
        let scheme = "itms-beta://"
        if text.count > scheme.count, text.prefix(scheme.count).lowercased() == scheme {
            return SunsetStoreURL.isValid("https://" + text.dropFirst(scheme.count))
        }
        return SunsetStoreURL.isValid(text)
    }
}
