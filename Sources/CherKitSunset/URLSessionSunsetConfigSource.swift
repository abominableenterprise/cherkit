import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

/// Fetches the hosted config over HTTPS with `URLSession`.
///
/// The request ignores the HTTP cache (local and remote), so a raised `minimumVersion` reaches users on their next
/// launch. Only a 2xx response with a non-empty body counts; anything else returns `nil` and Sunset falls back to the
/// cached config.
public struct URLSessionSunsetConfigSource: SunsetConfigSource {
    /// The hosted config file, e.g. `https://example.github.io/app/sunset.json`.
    public let url: URL
    private let session: URLSession

    /// A source for `url`. Pass a custom `session` only for tests or a special configuration.
    public init(url: URL, session: URLSession = .shared) {
        self.url = url
        self.session = session
    }

    /// The body of a 2xx response, or `nil` on any error, non-2xx status, empty body or after `timeout` seconds.
    public func fetch(timeout: TimeInterval) async -> Data? {
        let request = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalAndRemoteCacheData, timeoutInterval: timeout)
        guard let (data, response) = try? await session.data(for: request) else { return nil }
        return Self.body(data, response: response)
    }

    /// `data` when `response` is an HTTP 2xx and `data` is not empty, else `nil`.
    static func body(_ data: Data, response: URLResponse) -> Data? {
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode), !data.isEmpty else {
            return nil
        }
        return data
    }
}
