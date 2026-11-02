import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

/// Internal detail preserves failures without changing the public `fetch(timeout:) -> Data?` APIs.
enum SunsetFetchResult: Sendable {
    case body(Data)
    case failed
    case timedOut

    var data: Data? {
        guard case .body(let data) = self else { return nil }
        return data
    }
}

protocol SunsetDetailedSource: Sendable {
    func fetchResult(timeout: TimeInterval) async -> SunsetFetchResult
}

/// Shared URLSession implementation; no error text or request data escapes into telemetry.
enum SunsetURLSessionFetch {
    static func run(url: URL, session: URLSession, timeout: TimeInterval) async -> SunsetFetchResult {
        let request = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalAndRemoteCacheData, timeoutInterval: timeout)
        do {
            let (data, response) = try await session.data(for: request)
            guard let body = URLSessionSunsetConfigSource.body(data, response: response) else { return .failed }
            return .body(body)
        } catch {
            let error = error as NSError
            return error.domain == NSURLErrorDomain && error.code == NSURLErrorTimedOut ? .timedOut : .failed
        }
    }
}
