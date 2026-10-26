import CherKitSunset
import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

/// A value guarded by a lock; `with` is synchronous so it is safe to call from async code.
final class Locked<Value>: @unchecked Sendable {
    private let lock = NSLock()
    private var value: Value

    init(_ value: Value) {
        self.value = value
    }

    func with<Result>(_ body: (inout Value) -> Result) -> Result {
        lock.lock()
        defer { lock.unlock() }
        return body(&value)
    }
}

/// In-memory ``SunsetConfigCache`` that records every save.
final class MemorySunsetCache: SunsetConfigCache, Sendable {
    private let state: Locked<(stored: Data?, saves: [Data])>

    init(_ data: Data? = nil) {
        state = Locked((data, []))
    }

    var saved: [Data] { state.with { $0.saves } }

    func load() async -> Data? {
        state.with { $0.stored }
    }

    func save(_ data: Data) async {
        state.with {
            $0.stored = data
            $0.saves.append(data)
        }
    }
}

/// A source that ignores both its timeout and cancellation and answers `body` only after `delay` seconds, to prove
/// the checker's own deadline. It blocks no thread while waiting.
struct SlowSunsetSource: SunsetConfigSource {
    let body: Data
    let delay: TimeInterval

    func fetch(timeout: TimeInterval) async -> Data? {
        let body = body
        return await withCheckedContinuation { continuation in
            DispatchQueue.global().asyncAfter(deadline: .now() + delay) { continuation.resume(returning: body) }
        }
    }
}

/// What ``StubURLProtocol`` answers for one URL.
enum StubReply {
    case response(status: Int, body: Data)
    case error
    case hang
}

/// Serves canned replies to a real `URLSession`, so the tests run `URLSessionSunsetConfigSource` itself.
final class StubURLProtocol: URLProtocol {
    private static let state = Locked((replies: [URL: StubReply](), requests: [URL: URLRequest]()))

    /// A session whose requests are all answered by this protocol.
    static let session: URLSession = {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [StubURLProtocol.self]
        return URLSession(configuration: configuration)
    }()

    /// Registers `reply` under a fresh URL and returns it.
    static func serve(_ reply: StubReply) -> URL {
        let url = URL(string: "https://sunset.test/\(UUID().uuidString)/sunset.json")!
        state.with { $0.replies[url] = reply }
        return url
    }

    /// The request the session sent for `url`, if any.
    static func request(for url: URL) -> URLRequest? {
        state.with { $0.requests[url] }
    }

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        guard let url = request.url else { return }
        let reply: StubReply? = Self.state.with {
            $0.requests[url] = request
            return $0.replies[url]
        }
        switch reply {
        case .response(let status, let body):
            let response = HTTPURLResponse(url: url, statusCode: status, httpVersion: "HTTP/1.1", headerFields: nil)!
            client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
            client?.urlProtocol(self, didLoad: body)
            client?.urlProtocolDidFinishLoading(self)
        case .error, nil:
            client?.urlProtocol(self, didFailWithError: URLError(.notConnectedToInternet))
        case .hang:
            break
        }
    }

    override func stopLoading() {}
}
