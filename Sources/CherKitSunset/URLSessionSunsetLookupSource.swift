import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

/// Asks Apple's public App Store lookup with `URLSession` (Foundation only).
public struct URLSessionSunsetLookupSource: SunsetLookupSource {
    /// The lookup URL, e.g. `https://itunes.apple.com/lookup?bundleId=com.example.app&country=us`.
    public let url: URL
    private let session: URLSession

    /// A source for any lookup `url`. Pass a custom `session` only for tests or a special configuration.
    public init(url: URL, session: URLSession = .shared) {
        self.url = url
        self.session = session
    }

    /// A source for the app `bundleID` in the store of `country` (a two-letter code such as `"us"`), or `nil` when
    /// they do not make a valid URL.
    public init?(bundleID: String, country: String, session: URLSession = .shared) {
        var components = URLComponents(string: "https://itunes.apple.com/lookup")
        components?.queryItems = [URLQueryItem(name: "bundleId", value: bundleID), URLQueryItem(name: "country", value: country)]
        guard let url = components?.url else { return nil }
        self.init(url: url, session: session)
    }

    /// The body of a 2xx response, or `nil` on any error, non-2xx status, empty body or after `timeout` seconds.
    /// The HTTP cache is ignored, like the config's.
    public func fetch(timeout: TimeInterval) async -> Data? {
        let request = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalAndRemoteCacheData, timeoutInterval: timeout)
        guard let (data, response) = try? await session.data(for: request) else { return nil }
        return URLSessionSunsetConfigSource.body(data, response: response)
    }
}
